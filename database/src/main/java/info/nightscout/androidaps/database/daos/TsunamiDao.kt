package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_TSUNAMI
import info.nightscout.androidaps.database.entities.Tsunami
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Suppress("FunctionName")
@Dao
internal interface TsunamiDao : TraceableDao<Tsunami> {

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE id = :id")
    override fun findById(id: Long): Tsunami?

    @Query("DELETE FROM $TABLE_TSUNAMI")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE tsunamiMode <> NULL ORDER BY timestamp DESC limit 1")
    fun getTsunamiMode(): Maybe<Tsunami>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTsunamiModeActiveAt(timestamp: Long): Maybe<Tsunami>
//MP graph test
    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTsunamiDataFromTime(timestamp: Long): Single<List<Tsunami>>

    /*
    @Query("SELECT id FROM $TABLE_TSUNAMI ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): Tsunami?

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTsunamiActiveAt(timestamp: Long): Maybe<Tsunami>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTsunamiDataFromTime(timestamp: Long): Single<List<Tsunami>>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTsunamiDataIncludingInvalidFromTime(timestamp: Long): Single<List<Tsunami>>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTsunamiData(): Single<List<Tsunami>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_TSUNAMI WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<Tsunami>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<Tsunami>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Tsunami>

    @Query("SELECT * FROM $TABLE_TSUNAMI WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<Tsunami>
*/
}