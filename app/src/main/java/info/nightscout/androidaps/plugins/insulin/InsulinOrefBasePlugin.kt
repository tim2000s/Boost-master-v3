package info.nightscout.androidaps.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.T
import info.nightscout.shared.logging.AAPSLogger
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow

/**
 * Created by adrian on 13.08.2017.
 *
 * parameters are injected from child class
 *
 */
abstract class InsulinOrefBasePlugin(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config,
    val hardLimits: HardLimits
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.INSULIN)
        .fragmentClass(InsulinFragment::class.java.name)
        .pluginIcon(R.drawable.ic_insulin)
        .shortName(R.string.insulin_shortname)
        .visibleByDefault(false)
        .neverVisible(config.NSCLIENT),
    aapsLogger, rh, injector
), Insulin {

    private var lastWarned: Long = 0
    override val dia
        get(): Double {
            val dia = userDefinedDia
            return if (dia >= hardLimits.minDia()) {
                dia
            } else {
                sendShortDiaNotification(dia)
                hardLimits.minDia()
            }
        }

    open fun sendShortDiaNotification(dia: Double) {
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            val notification = Notification(Notification.SHORT_DIA, String.format(notificationPattern, dia, hardLimits.minDia()), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        }
    }

    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)

    open val userDefinedDia: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.dia ?: hardLimits.minDia()
        }

    @Inject lateinit var activePlugin: ActivePlugin
    override fun iobCalcForTreatment(bolus: Bolus, time: Long, dia: Double): Iob {
        assert(dia != 0.0)
        assert(peak != 0)
        val insulinInterface = activePlugin.activeInsulin
        val insulinID = insulinInterface.id.value
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            // force the IOB to 0 if over DIA hours have passed
            if (t < 8 * 60 && (insulinID == 105 || insulinID == 205)) { //MP: use pharmacodynamic model if PD model is selected insulin (ID 105 or 205)
                val PDresult = PDmodelIobCalculation(bolus, insulinID, t)
                result.iobContrib = PDresult.iobContrib
                result.activityContrib = PDresult.activityContrib
            } else { // MP: If the pharmacodynamic models are not used (IDs 105 & 205), use the traditional PK-based insulin model instead;
                val td = dia * 60 //getDIA() always >= MIN_DIA
                val tp = peak.toDouble()
                // force the IOB to 0 if over DIA hours have passed
                if (t < td) {
                    val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                    val a = 2 * tau / td
                    val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
                    result.activityContrib = bolus.amount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                    result.iobContrib = bolus.amount * (1 - s * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1))
                }
            }
        }
        return result
    }

    fun PDmodelIobCalculation(bolus: Bolus, insulinID: Int, t: Double): Iob {
        //MP Model for estimation of PD-based peak time: (a0 + a1*X)/(1+b1*X), where X = bolus size
        val a0 = 61.33 //MP Units = min
        val a1 = 12.27
        val b1 = 0.05185
        val tp: Double
        val result = Iob()
        if (insulinID == 205) { //MP ID = 205 for Lyumjev U200
            tp = (a0 + a1 * 2 * bolus.amount)/(1 + b1 * 2 * bolus.amount) //MP Units = min
        } else {
            tp = (a0 + a1 * bolus.amount) / (1 + b1 * bolus.amount) //MP Units = min
        }
        val tp_model = tp.pow(2.0) * 2 //MP The peak time in the model is defined as half of the square root of this variable - thus the tp entered into the model must be transformed first
        /**
         *
         * MP - UAM Tsunami PD model U100 vs U200
         *
         * InsActinvity calculation below: The same formula is used for both, U100 and U200
         * insulin as the concentration effect is already included in the peak time calculation.
         * If peak time is kept constant and only the dose is doubled, the general shape of the
         * curve doesn't change and hence the equation does not need adjusting. Unless a global
         * U200 mode is introduced where ISF between U100 and U200 has the same value (i.e.: When
         * ISF doubling and basal halving is done in AAPS' calculations and not by the user), the
         * equation doesn't need any changing.
         * The user must keep in mind that the displayed IOB is only half of the actual IOB.
         *
         */
        result.activityContrib = (2 * bolus.amount / tp_model) * t * exp(-t.pow(2.0) / tp_model)

        //MP New IOB formula - integrated version of the above activity curve
        val lowerLimit = t //MP lower integration limit, in min
        val upperLimit = 8.0 * 60 //MP upper integration limit, in min
        result.iobContrib = bolus.amount * (exp(-lowerLimit.pow(2.0)/tp_model) - exp(-upperLimit.pow(2.0)/tp_model))

        return result
    }

    override val insulinConfiguration: InsulinConfiguration
        get() = InsulinConfiguration(friendlyName, (dia * 1000.0 * 3600.0).toLong(), T.mins(peak.toLong()).msecs())

    override val comment
        get(): String {
            var comment = commentStandardText()
            val userDia = userDefinedDia
            if (userDia < hardLimits.minDia()) {
                comment += "\n" + rh.gs(R.string.dia_too_short, userDia, hardLimits.minDia())
            }
            return comment
        }

    abstract override val peak: Int
    abstract fun commentStandardText(): String
}