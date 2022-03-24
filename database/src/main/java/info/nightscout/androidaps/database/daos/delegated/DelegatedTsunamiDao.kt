package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.TsunamiDao
import info.nightscout.androidaps.database.entities.Tsunami
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedTsunamiDao(changes: MutableList<DBEntry>, private val dao: TsunamiDao) : DelegatedDao(changes), TsunamiDao by dao {

    override fun insertNewEntry(entry: Tsunami): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: Tsunami): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}