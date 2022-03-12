package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Round

data class GlucoseStatus(
    val glucose: Double,
    val noise: Double = 0.0,
    val delta: Double = 0.0,
    val shortAvgDelta: Double = 0.0,
    val longAvgDelta: Double = 0.0,
    val date: Long = 0L,
    //*** Tsunami ***
    var activity_pred_time: Long = 40L, //MP Time in minutes from now to calculate insulin activity for
    var bg_5minago: Double = 0.0,
    var deltaScore: Double = 0.0,
    var deltathreshold: Double = 7.0, //MP average delta above which deltaScore will be 1.
    var weight: Double = 0.15, //MP Weighting used for weighted averages
    //*** Tsunami data smoothing ***
    var insufficientSmoothingData: Boolean = false,
    var ssBGnow: Double = 0.0,
    var ssDnow: Double = 0.0,
) {

    fun log(): String = "Glucose: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl " +
        "Noise: " + DecimalFormatter.to0Decimal(noise) + " " +
        "Delta: " + DecimalFormatter.to0Decimal(delta) + " mg/dl" +
        "Short avg. delta: " + " " + DecimalFormatter.to2Decimal(shortAvgDelta) + " mg/dl " +
        "Long avg. delta: " + DecimalFormatter.to2Decimal(longAvgDelta) + " mg/dl" +
        //*** Tsunami ***
        "activity_pred_time: " + activity_pred_time + " min" +
        "bg_5minago: " + DecimalFormatter.to0Decimal(bg_5minago) + " mg/dl " +
        "deltaScore: " + DecimalFormatter.to2Decimal(deltaScore) + " a.u." +
        "Long avg. delta: " + DecimalFormatter.to2Decimal(longAvgDelta) + " mg/dl" +
        //*** Tsunami data smoothing ***
        "insufficientSmoothingData: " + insufficientSmoothingData +
        "ssBGnow: " + DecimalFormatter.to0Decimal(ssBGnow) + " mg/dl " +
        "ssDnow: " + DecimalFormatter.to0Decimal(ssDnow) + " mg/dl "
}

fun GlucoseStatus.asRounded() = copy(
    glucose = Round.roundTo(glucose, 0.1),
    noise = Round.roundTo(noise, 0.01),
    delta = Round.roundTo(delta, 0.01),
    shortAvgDelta = Round.roundTo(shortAvgDelta, 0.01),
    longAvgDelta = Round.roundTo(longAvgDelta, 0.01),
    //*** Tsunami ***
    bg_5minago = Round.roundTo(this.bg_5minago, 0.1),
    deltaScore = Round.roundTo(deltaScore, 0.01),
    //*** Tsunami data smoothing ***
    ssBGnow = Round.roundTo(ssBGnow, 0.1),
    ssDnow = Round.roundTo(ssDnow, 0.1),
)