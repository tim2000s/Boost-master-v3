package info.nightscout.androidaps.plugins.aps.tsunami

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.getPassedDurationToTimeInMinutes
import info.nightscout.androidaps.extensions.plannedRemainingMinutes
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.Round
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin


class DetermineBasalAdapterTAEJS internal constructor(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    private var profile = JSONObject()
    private var mGlucoseStatus = JSONObject()
    private var iobData: JSONArray? = null
    private var mealData = JSONObject()
    private var currentTemp = JSONObject()
    private var autosensData = JSONObject()
    private var microBolusAllowed = false
    private var smbAlwaysAllowed = false
    private var currentTime: Long = 0
    private var saveCgmSource = false
    var currentTempParam: String? = null
        private set
    var iobDataParam: String? = null
        private set
    var glucoseStatusParam: String? = null
        private set
    var profileParam: String? = null
        private set
    var mealDataParam: String? = null
        private set
    var scriptDebug = ""
        private set

    @Suppress("SpellCheckingInspection")
    operator fun invoke(): DetermineBasalResultTAE? {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")
        aapsLogger.debug(LTag.APS, "Glucose status: " + mGlucoseStatus.toString().also { glucoseStatusParam = it })
        aapsLogger.debug(LTag.APS, "IOB data:       " + iobData.toString().also { iobDataParam = it })
        aapsLogger.debug(LTag.APS, "Current temp:   " + currentTemp.toString().also { currentTempParam = it })
        aapsLogger.debug(LTag.APS, "Profile:        " + profile.toString().also { profileParam = it })
        aapsLogger.debug(LTag.APS, "Meal data:      " + mealData.toString().also { mealDataParam = it })
        aapsLogger.debug(LTag.APS, "Autosens data:  $autosensData")
        aapsLogger.debug(LTag.APS, "Reservoir data: " + "undefined")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "SMBAlwaysAllowed:  $smbAlwaysAllowed")
        aapsLogger.debug(LTag.APS, "CurrentTime: $currentTime")
        aapsLogger.debug(LTag.APS, "isSaveCgmSource: $saveCgmSource")
        var determineBasalResultTAE: DetermineBasalResultTAE? = null
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        // Turn off optimization to make Rhino Android compatible
        rhino.optimizationLevel = -1
        try {

            //register logger callback for console.log and console.error
            ScriptableObject.defineClass(scope, LoggerCallback::class.java)
            val myLogger = rhino.newObject(scope, "LoggerCallback", null)
            scope.put("console2", scope, myLogger)
            rhino.evaluateString(scope, readFile("OpenAPSAMA/loggerhelper.js"), "JavaScript", 0, null)

            //set module parent
            rhino.evaluateString(scope, "var module = {\"parent\":Boolean(1)};", "JavaScript", 0, null)
            rhino.evaluateString(scope, "var round_basal = function round_basal(basal, profile) { return basal; };", "JavaScript", 0, null)
            rhino.evaluateString(scope, "require = function() {return round_basal;};", "JavaScript", 0, null)

            //generate functions "determine_basal" and "setTempBasal"
            rhino.evaluateString(scope, readFile("Tsunami/determine-basal.js"), "JavaScript", 0, null)
            rhino.evaluateString(scope, readFile("Tsunami/basal-set-temp.js"), "setTempBasal.js", 0, null)
            val determineBasalObj = scope["determine_basal", scope]
            val setTempBasalFunctionsObj = scope["tempBasalFunctions", scope]

            //call determine-basal
            if (determineBasalObj is Function && setTempBasalFunctionsObj is NativeObject) {

                //prepare parameters
                val params = arrayOf(
                    makeParam(mGlucoseStatus, rhino, scope),
                    makeParam(currentTemp, rhino, scope),
                    makeParamArray(iobData, rhino, scope),
                    makeParam(profile, rhino, scope),
                    makeParam(autosensData, rhino, scope),
                    makeParam(mealData, rhino, scope),
                    setTempBasalFunctionsObj,
                    java.lang.Boolean.valueOf(microBolusAllowed),
                    makeParam(null, rhino, scope),  // reservoir data as undefined
                    java.lang.Long.valueOf(currentTime),
                    java.lang.Boolean.valueOf(saveCgmSource)
                )
                val jsResult = determineBasalObj.call(rhino, scope, scope, params) as NativeObject
                scriptDebug = LoggerCallback.scriptDebug

                // Parse the jsResult object to a JSON-String
                val result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString()
                aapsLogger.debug(LTag.APS, "Result: $result")
                try {
                    val resultJson = JSONObject(result)
                    determineBasalResultTAE = DetermineBasalResultTAE(injector, resultJson)
                } catch (e: JSONException) {
                    aapsLogger.error(LTag.APS, "Unhandled exception", e)
                }
            } else {
                aapsLogger.error(LTag.APS, "Problem loading JS Functions")
            }
        } catch (e: IOException) {
            aapsLogger.error(LTag.APS, "IOException")
        } catch (e: RhinoException) {
            aapsLogger.error(LTag.APS, "RhinoException: (" + e.lineNumber() + "," + e.columnNumber() + ") " + e.toString())
        } catch (e: IllegalAccessException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InstantiationException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InvocationTargetException) {
            aapsLogger.error(LTag.APS, e.toString())
        } finally {
            Context.exit()
        }
        glucoseStatusParam = mGlucoseStatus.toString()
        iobDataParam = iobData.toString()
        currentTempParam = currentTemp.toString()
        profileParam = profile.toString()
        mealDataParam = mealData.toString()
        return determineBasalResultTAE
    }

    @Suppress("SpellCheckingInspection") fun setData(profile: Profile,
                                                     maxIob: Double,
                                                     maxBasal: Double,
                                                     minBg: Double,
                                                     maxBg: Double,
                                                     targetBg: Double,
                                                     basalRate: Double,
                                                     iobArray: Array<IobTotal>,
                                                     glucoseStatus: GlucoseStatus,
                                                     mealData: MealData,
                                                     autosensDataRatio: Double,
                                                     tempTargetSet: Boolean,
                                                     microBolusAllowed: Boolean,
                                                     uamAllowed: Boolean,
                                                     advancedFiltering: Boolean,
                                                     isSaveCgmSource: Boolean
    ) {
        val pump = activePlugin.activePump
        val pumpBolusStep = pump.pumpDescription.bolusStep
        this.profile.put("max_iob", maxIob)
        //mProfile.put("dia", profile.getDia());
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg)
        this.profile.put("max_bg", maxBg)
        this.profile.put("target_bg", targetBg)
        this.profile.put("carb_ratio", profile.getIc())
        this.profile.put("sens", profile.getIsfMgdl())
        this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))

        //mProfile.put("high_temptarget_raises_sensitivity", SP.getBoolean(R.string.key_high_temptarget_raises_sensitivity, UAMDefaults.high_temptarget_raises_sensitivity));
//**********************************************************************************************************************************************
        this.profile.put("high_temptarget_raises_sensitivity", false)
        //mProfile.put("low_temptarget_lowers_sensitivity", SP.getBoolean(R.string.key_low_temptarget_lowers_sensitivity, UAMDefaults.low_temptarget_lowers_sensitivity));
        //this.profile.put("high_temptarget_raises_sensitivity",sp.getBoolean(resourceHelper.gs(R.string.key_high_temptarget_raises_sensitivity),TAEDefaults.high_temptarget_raises_sensitivity))
        //this.profile.put("low_temptarget_lowers_sensitivity",sp.getBoolean(resourceHelper.gs(R.string.key_low_temptarget_lowers_sensitivity),TAEDefaults.low_temptarget_lowers_sensitivity))
        this.profile.put("low_temptarget_lowers_sensitivity", false)
//**********************************************************************************************************************************************
        this.profile.put("sensitivity_raises_target", sp.getBoolean(R.string.key_sensitivity_raises_target, TAEDefaults.sensitivity_raises_target))
        this.profile.put("resistance_lowers_target", sp.getBoolean(R.string.key_resistance_lowers_target, TAEDefaults.resistance_lowers_target))
        this.profile.put("adv_target_adjustments", TAEDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", TAEDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", TAEDefaults.half_basal_exercise_target)
        this.profile.put("maxCOB", TAEDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, UAMDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", TAEDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)
        this.profile.put("A52_risk_enable", TAEDefaults.A52_risk_enable)
        val smbEnabled = sp.getBoolean(R.string.key_use_smb, false)
        this.profile.put("SMBInterval", sp.getInt(R.string.key_smbinterval, TAEDefaults.SMBInterval))
        this.profile.put("enableSMB_with_COB", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_COB, false))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_temptarget, false))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && sp.getBoolean(R.string.key_allowSMB_with_high_temptarget, false))
        this.profile.put("enableSMB_always", smbEnabled && sp.getBoolean(R.string.key_enableSMB_always, false) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && sp.getBoolean(R.string.key_enableSMB_after_carbs, false) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", sp.getInt(R.string.key_smbmaxminutes, TAEDefaults.maxSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uamsmbmaxminutes, TAEDefaults.maxUAMSMBBasalMinutes))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", sp.getInt(R.string.key_carbsReqThreshold, TAEDefaults.carbsReqThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")))
        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }
//**********************************************************************************************************************************************
        this.profile.put("use_autoisf", sp.getBoolean(R.string.key_tae_useautoisf, false))
        this.profile.put("autoisf_max", SafeParse.stringToDouble(sp.getString(R.string.key_tae_autoisf_max,"1.2")))
        this.profile.put("autoisf_hourlychange", SafeParse.stringToDouble(sp.getString(R.string.key_tae_autoisf_hourlychange,"0.2")))
        this.profile.put("UAM_boluscap", SafeParse.stringToDouble(sp.getString(R.string.key_UAM_boluscap, "1")))
        this.profile.put("insulinreqPCT", SafeParse.stringToDouble(sp.getString(R.string.key_insulinreqPCT, "65")))
        this.profile.put("tae_start", SafeParse.stringToDouble(sp.getString(R.string.key_tae_start, "11.0")))
        this.profile.put("tae_end", SafeParse.stringToDouble(sp.getString(R.string.key_tae_end, "23.0")))
        this.profile.put("percentage", profile.percentage)
        this.profile.put("dia", profile.dia)

        val activityPredTime_PK = TimeUnit.MILLISECONDS.toMinutes(activePlugin.activeInsulin.insulinConfiguration.peak) //MP act. pred. time for PK ins. models; target time = insulin peak time
        val activityPredTime_PD = 65L //MP activity prediction time for pharmacodynamic model; fixed to 65 min (approx. peak time of 1 U bolus)
        this.profile.put("peaktime", activityPredTime_PK) //            SafeParse.stringToDouble(sp.getString(R.string.key_insulin_oref_peak, "45"))
        val insulinInterface = activePlugin.activeInsulin
        val insulinID = insulinInterface.id.value
        this.profile.put("insulinID", insulinID)

        //MP UAM tsunami profile variables END
//**********************************************************************************************************************************************
        val now = System.currentTimeMillis()
        val tb = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.convertedToAbsolute(now, profile) ?: 0.0)
        // as we have non default temps longer than 30 mintues
        if (tb != null) currentTemp.put("minutesrunning", tb.getPassedDurationToTimeInMinutes(now))
//**********************************************************************************************************************************************
        //MD: TempTarget Info ==== START
        //MD: TempTarget Info ==== START
        //val tempTarget = repository.getTemporaryTargetActiveAt(now).blockingGet()

        //if (tempTarget is ValueWrapper.Existing) {
        //    this.profile.put("temptarget_duration", TimeUnit.MILLISECONDS.toMinutes(tempTarget.value.duration))
        //    this.profile.put("temptarget_minutesrunning", tempTarget.value.realTTDuration)
        //}

        var currentactivity = 0.0
        for (i in -4..0) { //MP: -4 to 0 calculates all the insulin active during the last 5 minutes
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(i.toLong()), profile)
            currentactivity += iob.activity
        }

        var futureactivity = 0.0
        var activityPredTime: Long
        if (insulinID != 6 && insulinID != 7) { //MP if not using PD insulin models
            activityPredTime = activityPredTime_PK
        } else { //MP if using PD insulin models
            activityPredTime = activityPredTime_PD
        }
        for (i in -4..0) { //MP: calculate 5-minute-insulin activity centering around peaktime
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(activityPredTime - i), profile)
            futureactivity += iob.activity
        }

        var sensorlag = -10L //MP Assume that the glucose value measurement reflect the BG value from 'sensorlag' minutes ago & calculate the insulin activity then
        var sensorlagactivity = 0.0
        for (i in -4..0) {
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(sensorlag - i), profile)
            sensorlagactivity += iob.activity
        }

        var activity_historic = -20L //MP Activity at the time in minutes from now. Used to calculate activity in the past to use as target activity.
        var historicactivity = 0.0
        for (i in -2..2) {
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(activity_historic - i), profile)
            historicactivity += iob.activity
        }

        futureactivity = Round.roundTo(futureactivity, 0.0001)
        sensorlagactivity = Round.roundTo(sensorlagactivity, 0.0001)
        historicactivity = Round.roundTo(historicactivity, 0.0001)
        currentactivity = Round.roundTo(currentactivity, 0.0001)

        //MD: TempTarget Info ==== END
//**********************************************************************************************************************************************
        iobData = iobCobCalculator.convertToJSONArray(iobArray)
//**********************************************************************************************************************************************
        mGlucoseStatus.put("futureactivity", futureactivity);
        mGlucoseStatus.put("activityPredTime", activityPredTime);
        mGlucoseStatus.put("sensorlagactivity", sensorlagactivity)
        mGlucoseStatus.put("historicactivity", historicactivity)
        mGlucoseStatus.put("currentactivity", currentactivity)
        mGlucoseStatus.put("deltascore", glucoseStatus.deltascore);
//**********************************************************************************************************************************************
        mGlucoseStatus.put("glucose", glucoseStatus.glucose)
//**********************************************************************************************************************************************
        // MP data smoothing START
        mGlucoseStatus.put("glucose_5m", glucoseStatus.bg_5minago)
        // MP data smoothing end
//**********************************************************************************************************************************************
        mGlucoseStatus.put("noise", glucoseStatus.noise)
        if (sp.getBoolean(R.string.key_always_use_shortavg, false)) {
            mGlucoseStatus.put("delta", glucoseStatus.shortAvgDelta)
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta)
        }
        mGlucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        mGlucoseStatus.put("date", glucoseStatus.date)
//**********************************************************************************************************************************************
        // autoISF === START
        // mod 7: append 2 variables for 5% range
        mGlucoseStatus.put("autoISF_duration", glucoseStatus.autoISF_duration)
        mGlucoseStatus.put("autoISF_average", glucoseStatus.autoISF_average)
        // autoISF === END
        // MP data smoothing START
        mGlucoseStatus.put("insufficientsmoothingdata", glucoseStatus.insufficientsmoothingdata)
        mGlucoseStatus.put("bg_supersmooth_now", glucoseStatus.bg_supersmooth_now)
        mGlucoseStatus.put("delta_supersmooth_now", glucoseStatus.delta_supersmooth_now)
        // MP data smoothing END
//**********************************************************************************************************************************************
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("mealCOB", mealData.mealCOB)
        this.mealData.put("slopeFromMaxDeviation", mealData.slopeFromMaxDeviation)
        this.mealData.put("slopeFromMinDeviation", mealData.slopeFromMinDeviation)
        this.mealData.put("lastBolusTime", mealData.lastBolusTime)
//**********************************************************************************************************************************************
        //MP Get last bolus for UAM tsunami start
        this.mealData.put("lastBolus", mealData.lastBolus)
        //MP Get last bolus for UAM tsunami end
//**********************************************************************************************************************************************
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)
        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }
        this.microBolusAllowed = microBolusAllowed
        smbAlwaysAllowed = advancedFiltering
        currentTime = now
        saveCgmSource = isSaveCgmSource
    }

    private fun makeParam(jsonObject: JSONObject?, rhino: Context, scope: Scriptable): Any {
        return if (jsonObject == null) Undefined.instance
        else NativeJSON.parse(rhino, scope, jsonObject.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun makeParamArray(jsonArray: JSONArray?, rhino: Context, scope: Scriptable): Any {
        return NativeJSON.parse(rhino, scope, jsonArray.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    @Throws(IOException::class) private fun readFile(filename: String): String {
        val bytes = scriptReader.readFile(filename)
        var string = String(bytes, StandardCharsets.UTF_8)
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20)
        }
        return string
    }

    init {
        injector.androidInjector().inject(this)
    }
}