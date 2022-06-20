package info.nightscout.androidaps.workflow

import android.content.Context
import android.graphics.Color
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.interfaces.ResourceHelper
import javax.inject.Inject
import kotlin.math.max

class PrepareTsunamiDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var overviewPlugin: OverviewPlugin
    var ctx: Context
    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        ctx =  rh.getThemedCtx(context)
    }

    class PrepareTsunamiData(
        val overviewData: OverviewData
    )

    override fun doWork(): Result {

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as PrepareTsunamiData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TSUNAMI_DATA, 0, null))
        var toTime = data.overviewData.toTime
        val tsunamiArray: MutableList<DataPoint> = ArrayList()
        var lastTsunami = -1.0
        loop.lastRun?.constraintsProcessed?.let { toTime = max(it.latestPredictionsTime, toTime) }
        var time = data.overviewData.fromTime
        val upperLimit = data.overviewData.maxBgValue//maxOf(data.overviewData.maxBgValue, data.overviewData.maxIAValue, data.overviewData.maxBasalValueFound, data.overviewData.maxCobValueFound)

        while (time < toTime) {
            val progress = (time - data.overviewData.fromTime).toDouble() / (data.overviewData.toTime - data.overviewData.fromTime) * 100.0
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TSUNAMI_DATA, progress.toInt(), null))
            val tsuEnabled = repository.getTsunamiModeActiveAt(time).blockingGet()
            val currentTsunami: Double = if (tsuEnabled is ValueWrapper.Existing) {
                upperLimit
            } else {
                0.0
            }
            if (currentTsunami != lastTsunami) {
                if (lastTsunami != -1.0) tsunamiArray.add(DataPoint(time.toDouble()/*/(1000*60*60)*/, lastTsunami))
                tsunamiArray.add(DataPoint(time.toDouble()/*/(1000*60*60)*/, currentTsunami))
            }
            lastTsunami = currentTsunami
            time += 60 * 1000L
        }
        // final points
        val tsuEnabled = repository.getTsunamiModeActiveAt(System.currentTimeMillis()).blockingGet()
        if (tsuEnabled is ValueWrapper.Existing) {
            tsunamiArray.add(DataPoint((tsuEnabled.value.timestamp + tsuEnabled.value.duration).toDouble(), upperLimit)) //MP upperLimit must not exceed chart height, else background will be invisible!
            tsunamiArray.add(DataPoint((tsuEnabled.value.timestamp + tsuEnabled.value.duration).toDouble(), 0.0))
        }

        // create series
        //data.overviewData.tempBasalGraphSeries = LineGraphSeries(Array(tempBasalArray.size) { i -> tempBasalArray[i] }).also {
        data.overviewData.tsunamiSeries = LineGraphSeries(Array(tsunamiArray.size) { i -> tsunamiArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = Color.argb(50, 0, 211, 141)
            it.thickness = 0
        }
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TSUNAMI_DATA, 100, null))
        return Result.success()
    }
}