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
  if (CloudStreamPlugins.isInitialized) CloudStreamPluginLoader.providerFiles() else emptyMap(),
)

/**
 * @param addonFallback what to call a stream that came from a plain add-on rather than a plugin or
 * CloudStream collection. Passed in rather than written here because it is a word on the screen,
 * and this file has no composition to read a resource from.
 * @param cloudStreamProviderFiles the plugin file each loaded CloudStream provider came from, by
 * provider name — see [CloudStreamPluginLoader.providerFiles].
 */
fun streamOriginLabel(
  stream: AddonStream?,
  plugins: PluginState,
  cloudStream: CsPluginState,
  addonFallback: String,
  cloudStreamProviderFiles: Map<String, String> = emptyMap(),
): String? {
  val addonId = stream?.addonId?.trim().orEmpty()
  if (addonId.isEmpty()) return null
  return when {
    addonId == DOWNLOADS_ADDON_ID -> "Downloaded"
    addonId.startsWith(PLUGIN_ADDON_ID_PREFIX) -> {
      val providerId = addonId.removePrefix(PLUGIN_ADDON_ID_PREFIX)
      val repoUrl = plugins.providers.firstOrNull { it.id == providerId }?.repoUrl.orEmpty()
      collectionOriginLabel(PLUGIN_ORIGIN, plugins.repos.firstOrNull { it.url == repoUrl }?.name, repoUrl)
    }
    addonId.startsWith(CLOUDSTREAM_ADDON_ID_PREFIX) -> {
      val providerName = addonId.removePrefix(CLOUDSTREAM_ADDON_ID_PREFIX)
      // Matching the provider's name against the collection's plugin names alone used to miss —
      // a plugin registers its sources under names of their own — which left these results saying
      // only "Plugin", indistinguishable from the other plugin system. The loaded file is the
      // reliable link; the name match stays as the fallback.
      val entry = cloudStreamProviderFiles[providerName]
        ?.let { path -> cloudStream.providers.firstOrNull { it.installedFilePath == path } }
        ?: cloudStream.providers.firstOrNull { it.name == providerName }
      val repoUrl = entry?.repoUrl.orEmpty()
      collectionOriginLabel(CLOUDSTREAM_ORIGIN, cloudStream.repos.firstOrNull { it.url == repoUrl }?.name, repoUrl)
    }
    else -> addonFallback
  }
}

/**
 * "CloudStream · <collection>" for a loaded CloudStream provider, by name — the same words its
 * streams carry, so a Home row and the sources it leads to read as coming from the same place.
 * Null when CloudStream is not running or the provider is not one it has loaded.
 */
fun cloudStreamProviderOriginLabel(providerName: String): String? {
  if (!CloudStreamPlugins.isInitialized) return null
  return streamOriginLabel(
    stream = AddonStream(
      addonId = CLOUDSTREAM_ADDON_ID_PREFIX + providerName,
      addonName = providerName,
      name = null,
      title = null,
      description = null,
      url = null,
      infoHash = null,
      fileIdx = null,
      filename = null,
      quality = null,
      size = null,
      cachedBy = emptyList(),
    ),
    plugins = StreamDekPlugins.manager.state,
    cloudStream = CloudStreamPlugins.manager.state,
    addonFallback = "",
    cloudStreamProviderFiles = CloudStreamPluginLoader.providerFiles(),
  )
}

private const val PLUGIN_ORIGIN = "Plugin"
private const val CLOUDSTREAM_ORIGIN = "CloudStream"

/** "Plugin · Collection" or "CloudStream · Repo": which system, then which collection within it. */
private fun collectionOriginLabel(kind: String, repoName: String?, repoUrl: String): String {
  val collection = repoName?.takeIf { it.isNotBlank() } ?: pluginRepoShortLabel(repoUrl)
  return listOfNotNull(kind, collection).joinToString(" · ")
}

/** A collection with no name still has a URL; its host is enough to tell two of them apart. */
private fun pluginRepoShortLabel(repoUrl: String): String? {
  val trimmed = repoUrl.trim().takeIf { it.isNotEmpty() } ?: return null
  return runCatching { java.net.URI(trimmed).host }.getOrNull()?.removePrefix("www.")
    ?: trimmed.substringAfter("//").substringBefore('/').takeIf { it.isNotEmpty() }
}
