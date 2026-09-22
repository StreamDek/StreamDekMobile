package net.streamdek.mobile.mpv

/** Delivers UI work without blocking the native event thread; stale surface callbacks are discarded. */
internal class MpvUiDispatcher(
    private val isMainThread: () -> Boolean,
    private val post: (() -> Unit) -> Unit,
) {
    @Volatile private var session: Any? = null

    // Surface lifecycle calls these on the main thread.
    fun start() { session = Any() }
    fun stop() { session = null }

    fun dispatch(block: () -> Unit) {
        val expected = session ?: return
        val guarded = { if (session === expected) block() }
        if (isMainThread()) guarded() else post(guarded)
    }
}
