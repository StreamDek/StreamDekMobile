package net.streamdek.mobile.nativeapp

/**
 * What kind of source a stream came from, rather than just its name.
 *
 * Carried over from the television, which has shown this on every row for a while: a list of
 * results all named after their provider does not say whether "Nova" is an add-on the account
 * installed, a plugin from a collection somebody added, or a file already on the device. Those are
 * different things to trust, to disable and to go looking for when a source stops working, and the
 * name alone tells you none of it.
 *
 * The phone has two plugin systems where the television has one, so both prefixes are read here.
 * Everything else is an add-on, which is what the television assumes for anything unprefixed.
 */
private const val PLUGIN_ADDON_ID_PREFIX = "plugin:"
private const val CLOUDSTREAM_ADDON_ID_PREFIX = "cloudstream:"
private const val DOWNLOADS_ADDON_ID = "streamdek:downloads"

/**
 * The label for a stream, read against whatever the app currently has installed.
 *
 * CloudStream is loaded on demand, so its manager is only asked for once it exists — reaching for
 * it earlier is what would turn a missing collection name into a crash.
 */
fun streamOriginLabel(stream: AddonStream?, addonFallback: String): String? = streamOriginLabel(
  stream,
  StreamDekPlugins.manager.state,
  if (CloudStreamPlugins.isInitialized) CloudStreamPlugins.manager.state else CsPluginState(),
  addonFallback,
)

/**
 * @param addonFallback what to call a stream that came from a plain add-on rather than a plugin or
 * CloudStream collection. Passed in rather than written here because it is a word on the screen,
 * and this file has no composition to read a resource from.
 */
fun streamOriginLabel(
  stream: AddonStream?,
  plugins: PluginState,
  cloudStream: CsPluginState,
  addonFallback: String,
): String? {
  val addonId = stream?.addonId?.trim().orEmpty()
  if (addonId.isEmpty()) return null
  return when {
    addonId == DOWNLOADS_ADDON_ID -> "Downloaded"
    addonId.startsWith(PLUGIN_ADDON_ID_PREFIX) -> {
      val providerId = addonId.removePrefix(PLUGIN_ADDON_ID_PREFIX)
      val repoUrl = plugins.providers.firstOrNull { it.id == providerId }?.repoUrl.orEmpty()
      pluginOriginLabel(plugins.repos.firstOrNull { it.url == repoUrl }?.name, repoUrl)
    }
    addonId.startsWith(CLOUDSTREAM_ADDON_ID_PREFIX) -> {
      val providerName = addonId.removePrefix(CLOUDSTREAM_ADDON_ID_PREFIX)
      val repoUrl = cloudStream.providers.firstOrNull { it.name == providerName }?.repoUrl.orEmpty()
      pluginOriginLabel(cloudStream.repos.firstOrNull { it.url == repoUrl }?.name, repoUrl)
    }
    else -> addonFallback
  }
}

private fun pluginOriginLabel(repoName: String?, repoUrl: String): String {
  val collection = repoName?.takeIf { it.isNotBlank() } ?: pluginRepoShortLabel(repoUrl)
  return listOfNotNull("Plugin", collection).joinToString(" · ")
}

/** A collection with no name still has a URL; its host is enough to tell two of them apart. */
private fun pluginRepoShortLabel(repoUrl: String): String? {
  val trimmed = repoUrl.trim().takeIf { it.isNotEmpty() } ?: return null
  return runCatching { java.net.URI(trimmed).host }.getOrNull()?.removePrefix("www.")
    ?: trimmed.substringAfter("//").substringBefore('/').takeIf { it.isNotEmpty() }
}
