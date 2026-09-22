package net.streamdek.mobile.nativeapp

/** Bounded code locations only: never exception messages, URLs, file paths or thread names. */
internal fun crashDiagnostics(error: Throwable): Map<String, Any> {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && causes.size < 10 && causes.none { it === current }) {
        causes += current
        current = current.cause
    }
    val selected = if (causes.size <= 4) causes else causes.take(2) + causes.takeLast(2)
    return mapOf(
        "diagnosticsVersion" to 1,
        "causes" to selected.map { cause ->
            mapOf(
                "exceptionClass" to cause.javaClass.name.take(180),
                "frames" to cause.stackTrace.take(24).map(::crashFrame),
            )
        },
    )
}

internal fun crashFrame(frame: StackTraceElement): String =
    "${frame.className}.${frame.methodName}:${frame.lineNumber}".take(300)

