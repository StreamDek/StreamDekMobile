package net.streamdek.mobile.mpv

import org.junit.Assert.*
import org.junit.Test

class MpvUiDispatcherTest {
    @Test fun workerCallbacksWaitForMainAndKeepOrder() {
        val queue = mutableListOf<() -> Unit>()
        val seen = mutableListOf<Int>()
        val owner = Thread.currentThread()
        val dispatcher = MpvUiDispatcher({ Thread.currentThread() === owner }, { queue.add(it) })
        dispatcher.start()
        val worker = Thread {
            dispatcher.dispatch { assertSame(owner, Thread.currentThread()); seen.add(1) }
            dispatcher.dispatch { assertSame(owner, Thread.currentThread()); seen.add(2) }
        }
        worker.start()
        worker.join()
        assertTrue(seen.isEmpty())
        queue.forEach { it() }
        assertEquals(listOf(1, 2), seen)
    }

    @Test fun callbacksFromReleasedSurfaceCannotReachReplacement() {
        val queue = mutableListOf<() -> Unit>()
        var calls = 0
        val dispatcher = MpvUiDispatcher({ false }, { queue.add(it) })
        dispatcher.start()
        dispatcher.dispatch { calls++ }
        dispatcher.stop()
        dispatcher.dispatch { calls++ }
        dispatcher.start()
        dispatcher.dispatch { calls += 10 }
        queue.forEach { it() }
        assertEquals(10, calls)
    }

    @Test fun mainThreadWorkRunsImmediatelyOnlyWhileActive() {
        var calls = 0
        val dispatcher = MpvUiDispatcher({ true }, { error("Unexpected post") })
        dispatcher.dispatch { calls++ }
        dispatcher.start()
        dispatcher.dispatch { calls++ }
        dispatcher.stop()
        dispatcher.dispatch { calls++ }
        assertEquals(1, calls)
    }
}
