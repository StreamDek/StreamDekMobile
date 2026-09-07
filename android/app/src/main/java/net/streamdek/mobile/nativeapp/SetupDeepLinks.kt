package net.streamdek.mobile.nativeapp

import java.net.URI

internal const val SETUP_REGISTER = "register"
internal const val SETUP_LOGIN = "login"
internal const val SETUP_CREATE_PROFILE = "profiles/create"

private val supportedSetupDestinations = setOf(
  SETUP_REGISTER,
  SETUP_LOGIN,
  SETUP_CREATE_PROFILE,
  "content-services",
  "addons",
  "sync-services",
  "player",
  "streams",
  "devices",
)

/** Returns a known destination from links such as streamdek://setup/content-services. */
internal fun normalizeSetupDestination(rawUrl: String): String? {
  val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
  if (!uri.scheme.equals("streamdek", ignoreCase = true)) return null
  if (!uri.host.equals("setup", ignoreCase = true)) return null
  val destination = uri.path.orEmpty().trim('/').lowercase()
  return destination.takeIf(supportedSetupDestinations::contains)
}

internal fun setupSettingsRoute(destination: String): SettingsRoute? = when (destination) {
  SETUP_CREATE_PROFILE -> SettingsRoute.Profiles
  "content-services" -> SettingsRoute.ContentServices
  "addons" -> SettingsRoute.Addons
  "sync-services" -> SettingsRoute.SyncServices
  "player" -> SettingsRoute.Player
  "streams" -> SettingsRoute.Streams
  "devices" -> SettingsRoute.ConnectTv
  else -> null
}
