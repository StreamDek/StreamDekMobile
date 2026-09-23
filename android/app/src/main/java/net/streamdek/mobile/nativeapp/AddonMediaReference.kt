package net.streamdek.mobile.nativeapp
import java.util.Base64
/** Source-qualified navigation identity. Never send this envelope to a metadata provider. */
internal data class AddonMediaReference(val addonId: String, val type: String, val id: String) {
    fun encode(): String = "sd-addon:" + listOf(addonId, type, id).joinToString(":") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    }
    companion object {
        fun decode(value: String): AddonMediaReference? = runCatching {
            if (!value.startsWith("sd-addon:")) return null
            val parts = value.removePrefix("sd-addon:").split(':')
            if (parts.size != 3) return null
            val values = parts.map { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
            if (values.any { it.isBlank() }) return null
            AddonMediaReference(values[0], values[1], values[2])
        }.getOrNull()
    }
}
