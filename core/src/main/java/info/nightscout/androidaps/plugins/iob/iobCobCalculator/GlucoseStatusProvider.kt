package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import dagger.Reusable
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
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
                //*** Tsunami ***
                bg_5minago = 0.0,
                deltaScore = 0.0,
                //*** Tsunami data smoothing ***
                insufficientSmoothingData = true,
                ssBGnow = now.value,
                ssDnow = 0.0,
            ).asRounded()
        }
        val nowValueList = ArrayList<Double>()
        val lastDeltas = ArrayList<Double>()
        val shortDeltas = ArrayList<Double>()
        val longDeltas = ArrayList<Double>()

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
        
        /**
         *  TSUNAMI DATA SMOOTHING CORE
         *
         *  Calculated a weighted average of 1st and 2nd order exponential smoothing functions
         *  to reduce the effect of sensor noise on APS performance. The weighted average
         *  is a compromise between the fast response to changing BGs at the cost of smoothness
         *  as offered by 1st order exponential smoothing, and the predictive, trend-sensitive but
         *  slower-to-respond smoothing as offered by 2nd order functions.
         *
         */
        val o1_sBG: ArrayList<Double> = ArrayList() //MP array for 1st order Smoothed Blood Glucose
        val o2_sBG: ArrayList<Double> = ArrayList() //MP array for 2nd order Smoothed Blood Glucose
        val o2_sD: ArrayList<Double> = ArrayList() //MP array for 2nd order Smoothed delta
        val ssBG: ArrayList<Double> = ArrayList() //MP array for weighted averaged, doubly smoothed Blood Glucose
        val ssD: ArrayList<Double> = ArrayList() //MP array for deltas of doubly smoothed Blood Glucose
        var windowSize = 25 //MP number of bg readings to include in smoothing window
        val o1_weight = 0.4
        val o1_a = 0.5
        val o2_a = 0.4
        val o2_b = 1.0
        var insufficientSmoothingData = false

        // ADJUST SMOOTHING WINDOW TO ONLY INCLUDE VALID READINGS
        // Valid readings include:
        // - Values that actually exist (windowSize may not be larger than sizeRecords)
        // - Values that come in approx. every 5 min. If the time gap between two readings is larger, this is likely due to a sensor error or warmup of a new sensor.d
        // - Values that are not 38 mg/dl; 38 mg/dl reflects an xDrip error state (according to a comment in determine-basal.js)

        //MP: Adjust smoothing window if database size is smaller than the default value + 1 (+1 because the reading before the oldest reading to be smoothed will be used in the calculations
        if (sizeRecords <= windowSize) { //MP standard smoothing window
            windowSize = (sizeRecords - 1).coerceAtLeast(0) //MP Adjust smoothing window to the size of database if it is smaller than the original window size; -1 to always have at least one older value to compare against as a buffer to prevent app crashes
        }

        //MP: Adjust smoothing window further if a gap in the BG database is detected, e.g. due to sensor errors of sensor swaps, or if 38 mg/dl are reported (xDrip error state)
        for (i in 0 until windowSize) {
            if (Math.round((data[i].timestamp - data[i + 1].timestamp) / (1000.0 * 60)) >= 12) { //MP: 12 min because a missed reading (i.e. readings coming in after 10 min) can occur for various reasons, like walking away from the phone or reinstalling AAPS
                //if (Math.round((data.get(i).date - data.get(i + 1).date) / 60000L) <= 7) { //MP crashes the app, useful for testing
                windowSize = i + 1 //MP: If time difference between two readings exceeds 7 min, adjust windowSize to *include* the more recent reading (i = reading; +1 because windowSize reflects number of valid readings);
                break
            } else if (data[i].value == 38.0) {
                windowSize = i //MP: 38 mg/dl reflects an xDrip error state; Chain of valid readings ends here, *exclude* this value (windowSize = i; i + 1 would include the current value)
                break
            }
        }

        // CALCULATE SMOOTHING WINDOW - 1st order exponential smoothing
        o1_sBG.clear() // MP reset smoothed bg array

        if (windowSize >= 4) { //MP: Require a valid windowSize of at least 4 readings
            o1_sBG.add(data[windowSize - 1].value) //MP: Initialise smoothing with the oldest valid data point
            for (i in 0 until windowSize) { //MP calculate smoothed bg window of valid readings
                o1_sBG.add(
                    0,
                    o1_sBG[0] + o1_a * (data[windowSize - 1 - i].value - o1_sBG[0])
                ) //MP build array of 1st order smoothed bgs
            }
        } else {
            insufficientSmoothingData = true
        }

        // CALCULATE SMOOTHING WINDOW - 2nd order exponential smoothing
        o2_sBG.clear() // MP reset smoothed bg array
        o2_sD.clear() // MP reset smoothed delta array

        if (windowSize >= 4) { //MP: Require a valid windowSize of at least 4 readings
            o2_sBG.add(data[windowSize - 1].value) //MP Start 2nd order exponential data smoothing with the oldest valid bg
            o2_sD.add(data[windowSize - 2].value - data[windowSize - 1].value) //MP Start 2nd order exponential data smoothing with the oldest valid delta
            for (i in 0 until windowSize - 1) { //MP calculated smoothed bg window of last 1 h
                o2_sBG.add(
                    0,
                    o2_a * data[windowSize - 2 - i].value + (1 - o2_a) * (o2_sBG[0] + o2_sD[0])
                ) //MP build array of 2nd order smoothed bgs; windowSize-1 is the oldest valid bg value, so windowSize-2 is from when on the smoothing begins;
                o2_sD.add(
                    0,
                    o2_b * (o2_sBG[0] - o2_sBG[1]) + (1 - o2_b) * o2_sD[0]
                ) //MP build array of 1st order smoothed bgs
            }
        } else {
            insufficientSmoothingData = true
        }

        // CALCULATE WEIGHTED AVERAGES OF GLUCOSE & DELTAS
        ssBG.clear() // MP reset doubly smoothed bg array
        ssD.clear() // MP reset doubly smoothed delta array

        if (!insufficientSmoothingData) { //MP Build doubly smoothed array only if there is enough valid readings
            for (i in o2_sBG.indices) { //MP calculated doubly smoothed bg of all o1/o2 smoothed data available; o2 & o1 smoothbg array sizes are equal in size, so only one is used as a condition here
                ssBG.add(o1_weight * o1_sBG[i] + (1 - o1_weight) * o2_sBG[i]) //MP build array of doubly smoothed bgs
            }
            for (i in 0 until ssBG.size - 1) {
                ssD.add(ssBG[i] - ssBG[i + 1]) //MP build array of doubly smoothed bg deltas
            }
        }

        // REPORT SMOOTHING RESULT IN GLUCOSE STATUS
        var ssBGnow : Double
        var ssDnow : Double
        if (!insufficientSmoothingData) {
            ssBGnow = ssBG[0]
            ssDnow = ssD[0]
        } else { //todo: below is a quick solution, should probably be improved, e.g. by preventing SMBs
            ssBGnow = data[0].value
            ssDnow = data[0].value - data[1].value
        }
        //####################################### MP
        //### TSUNAMI DATA SMOOTHING CORE END ### MP
        //####################################### MP

        //*** Tsunami ***
        // MP Tsunami meal detection system (requires data smoothing code for variable definitions)
        //TODO: Check if code includes data smoothing or if running Tsunami standalone version
        //Uncomment below if using WITHOUT data smoothing code
        //var deltaScore = 0.5

        //Uncomment below if using WITH data smoothing code
        var deltaScore: Double
        val deltaThreshold = 4.0 //MP average delta above which deltaScore will be 1.
        val weight = 0.15 //MP Weighting used for weighted averages
        var scoreDivisor: Double
        val before = data[1]

        if (!insufficientSmoothingData) {
            deltaScore = 0.0
            scoreDivisor = 0.0
            for (i in 0 until Math.min(windowSize - 1, 6)) { //MP Dynamically adjust deltas to include
                deltaScore += ssD[i] * (1 - weight * i)
                scoreDivisor += 1 - weight * i //MP weighted score
            }
            deltaScore = deltaScore / scoreDivisor / deltaThreshold //MP: Check how deltaScore compares to the threshold
        } else {
            deltaScore = 0.5 //MP If there's not enough data, set deltaScore to 50%
        }

        return GlucoseStatus(
            glucose = now.value,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = shortAverageDelta,
            delta = delta,
            longAvgDelta = average(longDeltas),
            //*** Tsunami ***
            bg_5minago = before.value,
            deltaScore = deltaScore,
            //*** Tsunami data smoothing ***
            insufficientSmoothingData = insufficientSmoothingData,
            ssBGnow = ssBGnow,
            ssDnow = ssDnow,
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