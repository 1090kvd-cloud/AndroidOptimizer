package cloud.kvd.androidoptimizer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    private val current = SettingsValues(screenTimeoutMs = 60_000, brightness = 200)

    @Test fun encodeDecode_roundTrip() {
        val v = SettingsValues(15_000, BRIGHTNESS_AUTO)
        assertEquals(v, SettingsValues.decode(v.encode()))
    }

    @Test fun encodeDecode_partialValues() {
        val v = SettingsValues(screenTimeoutMs = 600_000)
        assertEquals("t=600000", v.encode())
        assertEquals(v, SettingsValues.decode("t=600000"))
        assertEquals(SettingsValues(), SettingsValues.decode(""))
    }

    @Test fun decode_ignoresGarbage() {
        assertEquals(SettingsValues(screenTimeoutMs = 5), SettingsValues.decode("t=5,x=9,h=abc,,b"))
    }

    @Test fun overlay_nullKeepsCurrent() {
        val r = current.overlay(SettingsValues(screenTimeoutMs = 15_000))
        assertEquals(SettingsValues(15_000, 200), r)
    }

    @Test fun diff_listsOnlyRealChanges() {
        val d = diff(current, profileValues(ProfileId.SAVER))
        assertEquals(listOf(SettingField.SCREEN_TIMEOUT, SettingField.BRIGHTNESS), d.map { it.field })
        assertEquals(SettingChange(SettingField.SCREEN_TIMEOUT, 60_000, 15_000), d[0])
        assertEquals(SettingChange(SettingField.BRIGHTNESS, 200, 77), d[1])
    }

    @Test fun diff_screenOnTouchesOnlyTimeout() {
        val d = diff(current, profileValues(ProfileId.SCREEN_ON))
        assertEquals(listOf(SettingField.SCREEN_TIMEOUT), d.map { it.field })
    }

    @Test fun diff_brightnessToleranceAndAuto() {
        val target = SettingsValues(brightness = 77)
        assertTrue(diff(SettingsValues(brightness = 79), target).isEmpty())
        assertEquals(1, diff(SettingsValues(brightness = 90), target).size)
        assertEquals(1, diff(SettingsValues(brightness = BRIGHTNESS_AUTO), target).size)
        assertTrue(diff(SettingsValues(brightness = BRIGHTNESS_AUTO), SettingsValues(brightness = BRIGHTNESS_AUTO)).isEmpty())
    }

    @Test fun diff_unknownCurrentCountsAsChange() {
        assertEquals(1, diff(SettingsValues(), SettingsValues(brightness = 77)).size)
    }

    @Test fun isApplied_afterOverlay() {
        val saver = profileValues(ProfileId.SAVER)
        assertFalse(isApplied(current, saver))
        assertTrue(isApplied(current.overlay(saver), saver))
    }

    @Test fun journalEntry_roundTrip() {
        val e = JournalEntry(1730000000000L, ProfileId.SAVER, current, current.overlay(profileValues(ProfileId.SAVER)), reverted = true)
        assertEquals(e, JournalEntry.decode(e.encode()))
    }

    @Test fun journalEntry_rejectsBrokenLines() {
        assertNull(JournalEntry.decode("garbage"))
        assertNull(JournalEntry.decode("1|UNKNOWN|t=1|t=2|0"))
        assertNull(JournalEntry.decode("x|SAVER|t=1|t=2|0"))
    }

    @Test fun journal_roundTripAndEmpty() {
        val list = listOf(
            JournalEntry(2, ProfileId.DAILY, current, current, false),
            JournalEntry(1, ProfileId.SAVER, current, current, true)
        )
        assertEquals(list, decodeJournal(encodeJournal(list)))
        assertTrue(decodeJournal("").isEmpty())
    }

    @Test fun journal_newestFirstAndCapped() {
        var list = emptyList<JournalEntry>()
        for (i in 1..(JOURNAL_MAX + 5)) list = list.withNew(JournalEntry(i.toLong(), ProfileId.DAILY, current, current))
        assertEquals(JOURNAL_MAX, list.size)
        assertEquals((JOURNAL_MAX + 5).toLong(), list.first().id)
    }

    @Test fun journal_revertOnlyLatestNotReverted() {
        val list = listOf(
            JournalEntry(3, ProfileId.DAILY, current, current),
            JournalEntry(2, ProfileId.SAVER, current, current),
            JournalEntry(1, ProfileId.SAVER, current, current)
        )
        assertEquals(3L, list.latestRevertable()?.id)
        val after = list.markReverted(3)
        assertEquals(2L, after.latestRevertable()?.id)
        assertTrue(after.first { it.id == 3L }.reverted)
        assertNull(after.markReverted(2).markReverted(1).latestRevertable())
    }
}
