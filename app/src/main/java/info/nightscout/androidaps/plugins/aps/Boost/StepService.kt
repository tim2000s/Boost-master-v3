package info.nightscout.androidaps.plugins.aps.Boost

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import kotlin.math.roundToLong

object StepService : SensorEventListener {

    private const val TAG = "StepService"
    private var previousStepCount = -1
    private val stepsMap = LinkedHashMap<Long, Int>()
    private const val fiveMinutesInMs = 300000
    private const val numOf5MinBlocksToKeep = 20

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    private fun currentTimeIn5Min(): Long {
        return (System.currentTimeMillis() / fiveMinutesInMs.toDouble()).roundToLong()
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        sensorEvent ?: return

        val now = currentTimeIn5Min()
        val stepCount = sensorEvent.values[0].toInt()
        if(previousStepCount >= 0) {
            var recentStepCount = stepCount - previousStepCount
            if(stepsMap.contains(now)) {
                recentStepCount += stepsMap.getValue(now)
            }
            stepsMap[now] = recentStepCount
        }
        previousStepCount = stepCount

        if(stepsMap.size > numOf5MinBlocksToKeep) {
            val removeBefore = now - numOf5MinBlocksToKeep
            stepsMap.entries.removeIf { it.key < removeBefore}
        }
    }

    fun getRecentStepCount5Min(): Int {
        val now = currentTimeIn5Min() - 1
        return if(stepsMap.contains(now)) stepsMap.getValue(now) else 0
    }

    fun getRecentStepCount10Min(): Int {
        val tenMinAgo = currentTimeIn5Min() - 2
        return if(stepsMap.contains(tenMinAgo)) stepsMap.getValue(tenMinAgo) else 0
    }

    fun getRecentStepCount15Min(): Int {
        val fifteenMinAgo = currentTimeIn5Min() - 3
        return if(stepsMap.contains(fifteenMinAgo)) stepsMap.getValue(fifteenMinAgo) else 0
    }

    fun getRecentStepCount30Min(): Int {
        return getStepsInLastXMin(6)
    }

    fun getRecentStepCount60Min(): Int {
        return getStepsInLastXMin(12)
    }

    private fun getStepsInLastXMin(numberOf5MinIncrements: Int): Int {
        var stepCount = 0
        val thirtyMinAgo = currentTimeIn5Min() - numberOf5MinIncrements
        for (entry in stepsMap.entries) {
            if (entry.key > thirtyMinAgo) {
                stepCount += entry.value
            }
        }
        return stepCount
    }

}