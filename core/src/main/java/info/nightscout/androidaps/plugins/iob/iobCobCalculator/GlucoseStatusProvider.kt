package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import dagger.Reusable
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToLong

@Reusable
class GlucoseStatusProvider @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil
) {

    val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData()

    fun getGlucoseStatusData(allowOldData: Boolean = false): GlucoseStatus? {
        val data = iobCobCalculator.ads.getBgReadingsDataTableCopy()
        val sizeRecords = data.size
        if (sizeRecords == 0) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
            return null
        }
        if (data[0].timestamp < dateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
            aapsLogger.debug(LTag.GLUCOSE, "oldData")
            return null
        }
        val now = data[0]
        val nowDate = now.timestamp
        var change: Double
        if (sizeRecords == 1) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
            return GlucoseStatus(
                glucose = now.value,
                noise = 0.0,
                delta = 0.0,
                shortAvgDelta = 0.0,
                longAvgDelta = 0.0,
                date = nowDate,
                //*** autoISF specific values ******************************************************************************************************************
                autoISF_duration = 0.0,
                autoISF_average = now.value,
                //*** Tsunami specific values ******************************************************************************************************************
                bg_5minago = 0.0,
                deltascore = 0.0
            ).asRounded()
        }
        val nowValueList = ArrayList<Double>()
        val lastDeltas = ArrayList<Double>()
        val shortDeltas = ArrayList<Double>()
        val longDeltas = ArrayList<Double>()

        //*** Tsunami specific values ******************************************************************************************************************
        var deltascore: Double
        val deltathreshold = 7.0 //MP average delta above which deltascore will be 1.
        val weight = 0.15 //MP Weighting used for weighted averages
        var scoredivisor: Double
        val before = data[1]

        // Use the latest sgv value in the now calculations
        nowValueList.add(now.value)
        for (i in 1 until sizeRecords) {
            if (data[i].value > 38) {
                val then = data[i]
                val thenDate = then.timestamp

                val minutesAgo = ((nowDate - thenDate) / (1000.0 * 60)).roundToLong()
                // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                change = now.value - then.value
                val avgDel = change / minutesAgo * 5
                aapsLogger.debug(LTag.GLUCOSE, "$then minutesAgo=$minutesAgo avgDelta=$avgDel")

                // use the average of all data points in the last 2.5m for all further "now" calculations
                if (0 < minutesAgo && minutesAgo < 2.5) {
                    // Keep and average all values within the last 2.5 minutes
                    nowValueList.add(then.value)
                    now.value = average(nowValueList)
                    // short_deltas are calculated from everything ~5-15 minutes ago
                } else if (2.5 < minutesAgo && minutesAgo < 17.5) {
                    //console.error(minutesAgo, avgDelta);
                    shortDeltas.add(avgDel)
                    // last_deltas are calculated from everything ~5 minutes ago
                    if (2.5 < minutesAgo && minutesAgo < 7.5) {
                        lastDeltas.add(avgDel)
                    }
                    // long_deltas are calculated from everything ~20-40 minutes ago
                } else if (17.5 < minutesAgo && minutesAgo < 42.5) {
                    longDeltas.add(avgDel)
                } else {
                    // Do not process any more records after >= 42.5 minutes
                    break
                }
            }
        }
        val shortAverageDelta = average(shortDeltas)
        val delta = if (lastDeltas.isEmpty()) {
            shortAverageDelta
        } else {
            average(lastDeltas)
        }

        //*** autoISF core ******************************************************************************************************************
        val bw = 0.05 // used for Eversense; may be lower for Dexcom

        var sumBG = now.value
        var oldavg = now.value
        var minutesdur = Math.round(0L / (1000.0 * 60))
        for (i in 1 until sizeRecords) {
            val then = data[i]
            val thenDate: Long = then.timestamp
            //  GZ mod 7c: stop the series if there was a CGM gap greater than 13 minutes, i.e. 2 regular readings
            if (Math.round((nowDate - thenDate) / (1000.0 * 60)) - minutesdur > 13) {
                break
            }
            if (then.value > oldavg * (1 - bw) && then.value < oldavg * (1 + bw)) {
                sumBG += then.value
                oldavg = sumBG / (i + 1)
                minutesdur = Math.round((nowDate - thenDate) / (1000.0 * 60))
            } else {
                break
            }
        }
        // autoISF === END

        // MP Tsunami meal detection system (requires data smoothing code for variable definitions)
        //Uncomment below if using WITHOUT data smoothing code

        deltascore = 0.5

        //Uncomment below if using WITH data smoothing code
        /*
        if (!insufficientsmoothingdata) {
            deltascore = 0.0
            scoredivisor = 0.0
            for (i in 0 until Math.min(windowsize - 1, 6)) { //MP Dynamically adjust deltas to include
                deltascore += ssmooth_delta[i] * (1 - weight * i)
                scoredivisor += 1 - weight * i //MP weighted score
            }
            deltascore = deltascore / scoredivisor / deltathreshold //MP: Check how deltascore compares to the threshold
        } else {
            deltascore = 0.5 //MP If there's not enough data, set deltascore to 50%
        }
        */
        return GlucoseStatus(
            glucose = now.value,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = shortAverageDelta,
            delta = delta,
            longAvgDelta = average(longDeltas),
            //*** autoISF core ******************************************************************************************************************
            autoISF_average = oldavg,
            autoISF_duration = minutesdur.toDouble(),
            //*** Tsunami specific values ******************************************************************************************************************
            bg_5minago = before.value,
            deltascore = deltascore,
        ).also { aapsLogger.debug(LTag.GLUCOSE, it.log()) }.asRounded()
    }

    companion object {

        fun average(array: ArrayList<Double>): Double {
            var sum = 0.0
            if (array.size == 0) return 0.0
            for (value in array) {
                sum += value
            }
            return sum / array.size
        }
    }
}