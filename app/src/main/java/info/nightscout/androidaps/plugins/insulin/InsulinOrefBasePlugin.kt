package info.nightscout.androidaps.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
    config: Config
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
            return if (dia >= MIN_DIA) {
                dia
            } else {
                sendShortDiaNotification(dia)
                MIN_DIA
            }
        }

    open fun sendShortDiaNotification(dia: Double) {
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            val notification = Notification(Notification.SHORT_DIA, String.format(notificationPattern, dia, MIN_DIA), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        }
    }

    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)

    open val userDefinedDia: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.dia ?: MIN_DIA
        }

    @Inject lateinit var activePlugin: ActivePlugin
    override fun iobCalcForTreatment(bolus: Bolus, time: Long, dia: Double): Iob {
        val insulinInterface = activePlugin.activeInsulin
        val insulinID = insulinInterface.id.value
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val now = System.currentTimeMillis()
            var circadian_sensitivity = 1.0
            if (now >= 0 && now < 2){
                circadian_sensitivity = 1.4
            }else if (now >= 2 && now < 3){
                 circadian_sensitivity = 0.8
            }else if (now >= 3 && now < 8){
                 circadian_sensitivity = 0.8
            }else if (now >= 8 && now < 11){
                 circadian_sensitivity = 0.6
            }else if (now >= 11 && now < 15){
                 circadian_sensitivity = 0.8
            }else if (now >= 15 && now <= 22){
                 circadian_sensitivity = 1.0
            }else if (now >= 22 && now <= 24){
                circadian_sensitivity = 1.2
            }
            // force the IOB to 0 if over DIA hours have passed
            if (t < 3.8 * circadian_sensitivity * 60 && (insulinID == 6 || insulinID == 7)) { //MP: Fixed DIA cut-off of 8 h - the model automatically changes its DIA based on the bolus size, thus no user-set DIA is required.
                //MP Model for estimation of PD-based peak time: (a0 + a1*X)/(1+b1*X), where X = bolus size
                val a0 = 61.33
                val a1 = 12.27
                val b1 = 0.05185
                val tp: Double
                if (insulinID == 6) { //MP ID = 6 for Lyumjev U200
                    tp = (a0 + a1 * 2 * bolus.amount)/(1 + b1 * 2 * bolus.amount)
                } else {
                    tp = (a0 + a1 * bolus.amount) / (1 + b1 * bolus.amount)
                }
                val tp_model = tp.pow(2.0) * 2 //MP The peak time in the model is defined as half of the square root of this variable - thus the tp entered into the model must be transformed first
                //MP Calculate remaining IOB of this bolus (PD based approach)
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
                val lowerLimit = t //MP lower integration limit
                val upperLimit = 3.8 * circadian_sensitivity //MP upper integration limit
                result.iobContrib = bolus.amount - (0.5 * (2 * bolus.amount / tp_model) * tp_model * (exp(-upperLimit.pow(2)/tp_model) - exp(-lowerLimit.pow(2)/tp_model)))

                //MP Below: old IOB formula; produces (almost?) identical results, but requires for loop
                //var pct_ins_left = 0.0 //MP insulin equivalents in U that are still "unused"
                //for (i in 0..t.toInt()) {
                //    pct_ins_left += (2 * bolus.amount / tp_model) * i * exp(-i.toDouble().pow(2.0) / tp_model)
                //}
                //result.iobContrib = bolus.amount * (1 - (pct_ins_left/bolus.amount))

            } else {
                // MP: If the Lyumjev pharmacodynamic models are not used (IDs 6 & 7), use the traditional PK-based insulin model instead;
                val peak = peak * circadian_sensitivity
                val td = dia * circadian_sensitivity * 60 //getDIA() always >= MIN_DIA
                val tp = peak.toDouble()
                // force the IOB to 0 if over DIA hours have passed
                if (t < td) {
                    val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                    val a = 2 * tau / td
                    val S = 1 / (1 - a + (1 + a) * exp(-td / tau))
                    result.activityContrib = bolus.amount * (S / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                    result.iobContrib = bolus.amount * (1 - S * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * Math.exp(-t / tau) + 1))
                }
            }
        }
        return result
    }
/* NON-TSUNAMI ACTIVITY CALCULATION CODE (original)
    override fun iobCalcForTreatment(bolus: Bolus, time: Long, dia: Double): Iob {
        val result = Iob()
        val peak = peak
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = dia * 60 //getDIA() always >= MIN_DIA
            val tp = peak.toDouble()
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val S = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (S / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - S * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * Math.exp(-t / tau) + 1))
            }
        }
        return result
    }
*/
    override val insulinConfiguration: InsulinConfiguration

        get() = InsulinConfiguration(friendlyName, (dia * 1000.0 * 3600.0).toLong(), T.mins(peak.toLong()).msecs())

    override val comment
        get(): String {
            var comment = commentStandardText()
            val userDia = userDefinedDia
            if (userDia < MIN_DIA) {
                comment += "\n" + rh.gs(R.string.dia_too_short, userDia, MIN_DIA)
            }
            return comment
        }

    abstract val peak: Int
    abstract fun commentStandardText(): String

    companion object {

        const val MIN_DIA = 5.0
    }
}