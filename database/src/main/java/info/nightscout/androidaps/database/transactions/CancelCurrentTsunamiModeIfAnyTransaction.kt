package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Tsunami
import info.nightscout.androidaps.database.interfaces.end

class CancelCurrentTsunamiModeIfAnyTransaction(
    val timestamp: Long
) : Transaction<CancelCurrentTsunamiModeIfAnyTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.tsunamiDao.getTsunamiModeActiveAt(timestamp).blockingGet()
        if (current != null) {
            current.end = timestamp
            database.tsunamiDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<Tsunami>()
    }
}