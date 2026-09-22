package net.streamdek.mobile.nativeapp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class CrashDiagnosticsTest {
    @Test fun keepsFrameworkAndObfuscatedFramesWithoutMessagesOrPaths() {
        val failure = IllegalStateException("https://private.invalid/?token=DO_NOT_RECORD")
        failure.stackTrace = arrayOf(
            StackTraceElement("a.b", "c", "/private/path", 38),
            StackTraceElement("android.view.ViewRootImpl", "checkThread", "ViewRootImpl.java", 10),
        )
        val report = Gson().toJson(crashDiagnostics(failure))
        assertTrue(report.contains("a.b.c:38"))
        assertTrue(report.contains("android.view.ViewRootImpl.checkThread:10"))
        assertFalse(report.contains("DO_NOT_RECORD"))
        assertFalse(report.contains("/private/path"))
    }

    @Test fun persistenceRoundTripKeepsStructuredCausesAndFrames() {
        val failure = IllegalArgumentException("private")
        failure.stackTrace = arrayOf(StackTraceElement("a.b", "c", "File.kt", 1))
        val gson = Gson()
        val restored = gson.fromJson<Map<String, Any>>(gson.toJson(crashDiagnostics(failure)),
            object : TypeToken<Map<String, Any>>() {}.type)
        val causes = restored["causes"] as List<*>
        val cause = causes.single() as Map<*, *>
        assertEquals("java.lang.IllegalArgumentException", cause["exceptionClass"])
        assertEquals(listOf("a.b.c:1"), cause["frames"])
    }

    @Test fun boundsCauseDepthAndFramesWhileRetainingRoot() {
        var failure: Throwable = ClassNotFoundException("private")
        repeat(8) { failure = IllegalStateException("wrapper", failure) }
        failure.stackTrace = Array(100) { StackTraceElement("a", "b", "c", it) }
        val causes = crashDiagnostics(failure)["causes"] as List<*>
        assertEquals(4, causes.size)
        assertEquals(24, ((causes.first() as Map<*, *>)["frames"] as List<*>).size)
        assertEquals("java.lang.ClassNotFoundException", (causes.last() as Map<*, *>)["exceptionClass"])
    }

    @Test fun cyclicCausesTerminate() {
        val outer = IllegalStateException()
        val inner = IllegalArgumentException(outer)
        outer.initCause(inner)
        assertEquals(2, (crashDiagnostics(outer)["causes"] as List<*>).size)
    }
}
