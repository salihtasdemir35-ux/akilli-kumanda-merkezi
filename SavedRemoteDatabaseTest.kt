package com.akillikumanda.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akillikumanda.app.core.database.AppDatabase
import com.akillikumanda.app.core.model.*
import com.akillikumanda.app.data.remote.BackupManager
import com.akillikumanda.app.data.remote.SaveResult
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Cihazda/emülatörde çalışır: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class SavedRemoteDatabaseTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test-kumanda.db"
    private lateinit var db: AppDatabase

    private val profile = RemoteProfile(
        id = "tv.lg.nec.a", category = DeviceCategory.TV, brand = "LG", model = null, modelPrefixes = emptyList(),
        protocol = IrProtocol.NEC, connectionType = ConnectionType.IR, carrierFrequencyHz = 38000,
        commands = listOf(RemoteCommand("MUTE", "Sessiz", "", ProtocolData.Scalar(0x20DF906F, 32), RepeatPolicy.ONCE, DeviceStateRequirement.ON)),
        source = "test", license = "test", verificationStatus = VerificationStatus.UNVERIFIED, version = 1,
    )

    private fun open() = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName).build()

    @Before fun setUp() { ctx.deleteDatabase(dbName); db = open() }
    @After fun tearDown() { db.close(); ctx.deleteDatabase(dbName) }

    @Test fun saveAndDelete() = runTest {
        val repo = SavedRemoteRepository(db.savedRemoteDao())
        val id = (repo.saveConfirmed(profile, "Salon TV", "Salon", null) as SaveResult.Saved).id
        assertEquals("Salon TV", repo.get(id)!!.name)
        repo.delete(repo.get(id)!!)
        assertNull(repo.get(id))
    }

    @Test fun duplicateIsRejected() = runTest {
        val repo = SavedRemoteRepository(db.savedRemoteDao())
        repo.saveConfirmed(profile, "Salon TV", null, null)
        assertTrue(repo.saveConfirmed(profile, "  salon   tv ", null, null) is SaveResult.Duplicate)
    }

    @Test fun dataSurvivesReopen() = runTest {
        SavedRemoteRepository(db.savedRemoteDao()).saveConfirmed(profile, "Yatak odası", null, null)
        db.close()
        db = open()
        val all = db.savedRemoteDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(profile, SavedRemoteRepository(db.savedRemoteDao()).profileOf(all.single()))
    }

    @Test fun backupRoundTripSkipsDuplicates() = runTest {
        val dao = db.savedRemoteDao()
        SavedRemoteRepository(dao).saveConfirmed(profile, "Salon TV", null, null)
        val text = BackupManager(dao).export()
        val report = BackupManager(dao).import(text)
        assertEquals(0, report.imported)
        assertEquals(1, report.skippedDuplicate)
    }
}
