package com.example

import com.example.telecom.CallAudioRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioRecorderUtilsTest {

    @Test
    fun `buildRecordingFileName sanitizes phone number and appends m4a extension`() {
        val fileName = CallAudioRecorder.buildRecordingFileName("+1 (415) 555-0192", 1_700_000_000_000L)
        assertTrue(fileName.startsWith("CallGuard_14155550192_"))
        assertTrue(fileName.endsWith(".m4a"))
    }

    @Test
    fun `buildRecordingFileName handles unknown blank caller`() {
        val fileName = CallAudioRecorder.buildRecordingFileName("Private", 1_700_000_000_000L)
        assertTrue(fileName.startsWith("CallGuard_unknown_"))
        assertTrue(fileName.endsWith(".m4a"))
    }

    @Test
    fun `formatDurationMillis formats minutes and hours accurately`() {
        assertEquals("00:00", CallAudioRecorder.formatDurationMillis(0L))
        assertEquals("00:45", CallAudioRecorder.formatDurationMillis(45_000L))
        assertEquals("03:09", CallAudioRecorder.formatDurationMillis(189_000L))
        assertEquals("1:05:02", CallAudioRecorder.formatDurationMillis(3_902_000L))
    }

    @Test
    fun `formatFileSize formats KB and MB cleanly`() {
        assertEquals("0 KB", CallAudioRecorder.formatFileSize(0L))
        assertEquals("1 KB", CallAudioRecorder.formatFileSize(512L))
        assertEquals("256 KB", CallAudioRecorder.formatFileSize(256L * 1024L))
        assertEquals("2.5 MB", CallAudioRecorder.formatFileSize((2.5 * 1024.0 * 1024.0).toLong()))
    }
}
