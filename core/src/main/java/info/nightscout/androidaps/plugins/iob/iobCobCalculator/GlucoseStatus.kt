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
    //*** autoISF specific values ******************************************************************************************************************
    var autoISF_duration: Double = 0.0,
    var autoISF_average: Double = 0.0,
    //*** Tsunami specific values ******************************************************************************************************************
    var activity_pred_time: Long = 40L, //MP Time in minutes from now to calculate insulin activity for
    var bg_5minago: Double = 0.0,
    var deltascore: Double = 0.0,
    var deltathreshold: Double = 7.0, //MP average delta above which deltascore will be 1.
    var weight: Double = 0.15, //MP Weighting used for weighted averages
    //**********************************************************************************************************************************************
    val date: Long = 0L,
    //*** Tsunami data smoothing specific values ******************************************************************************************************************
    var insufficientsmoothingdata: Boolean = false,
    var bg_supersmooth_now: Double = 0.0,
    var delta_supersmooth_now: Double = 0.0,
) {

    fun log(): String = "Glucose: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl " +
        "Noise: " + DecimalFormatter.to0Decimal(noise) + " " +
        "Delta: " + DecimalFormatter.to0Decimal(delta) + " mg/dl" +
        "Short avg. delta: " + " " + DecimalFormatter.to2Decimal(shortAvgDelta) + " mg/dl " +
        "Long avg. delta: " + DecimalFormatter.to2Decimal(longAvgDelta) + " mg/dl" +
        //*** autoISF specific values ******************************************************************************************************************
        "autoISF_duration: " + autoISF_duration + " min" +                                  //Todo Check Unit (min or msec)
        "autoISF_average: " + DecimalFormatter.to1Decimal(autoISF_average) + " mg/dl/U" +
        //*** Tsunami specific values ******************************************************************************************************************
        "activity_pred_time: " + activity_pred_time + " min" +
        "bg_5minago: " + DecimalFormatter.to0Decimal(bg_5minago) + " mg/dl " +
        "deltascore: " + DecimalFormatter.to2Decimal(deltascore) + " a.u." +
        "Long avg. delta: " + DecimalFormatter.to2Decimal(longAvgDelta) + " mg/dl" +
        //*** Tsunami data smoothing ******************************************************************************************************************
        "insufficientsmoothingdata: " + insufficientsmoothingdata +
        "bg_supersmooth_now: " + DecimalFormatter.to0Decimal(bg_supersmooth_now) + " mg/dl " +
        "delta_supersmooth_now: " + DecimalFormatter.to0Decimal(delta_supersmooth_now) + " mg/dl "
}

fun GlucoseStatus.asRounded() = copy(
    glucose = Round.roundTo(glucose, 0.1),
    noise = Round.roundTo(noise, 0.01),
    delta = Round.roundTo(delta, 0.01),
    shortAvgDelta = Round.roundTo(shortAvgDelta, 0.01),
    longAvgDelta = Round.roundTo(longAvgDelta, 0.01),
    //*** autoISF specific values ******************************************************************************************************************
    autoISF_duration = Round.roundTo(autoISF_duration, 0.1),
    autoISF_average = Round.roundTo(this.autoISF_average, 0.1),
    //*** Tsunami specific values ******************************************************************************************************************
    bg_5minago = Round.roundTo(this.bg_5minago, 0.1),
    deltascore = Round.roundTo(deltascore, 0.01),
    longAvgDelta = Round.roundTo(longAvgDelta, 0.01),
    //*** Tsunami data smoothing specific values ******************************************************************************************************************
    bg_supersmooth_now = Round.roundTo(bg_supersmooth_now, 0.1),
    delta_supersmooth_now =Round.roundTo(delta_supersmooth_now, 0.1)
)