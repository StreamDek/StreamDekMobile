package net.streamdek.mobile.nativeapp

import net.streamdek.mobile.R

private fun addonHomeCatalogTitle(addonName: String, catalogName: String, type: String, needsDifferentiator: Boolean): String {
  val cleaned = cleanAddonCatalogLabel(catalogName, addonName)
  val base = cleaned.ifBlank { cleanAddonCatalogLabel(addonName).ifBlank { addonName } }
  return if (!needsDifferentiator) base else "$base ${
    when (type) {
      "movie" -> "Movies"
      "series", "show" -> "Series"
      "tv", "channel", "live", "iptv" -> "Live"
      "sport", "sports", "events" -> "Sports"
      else -> type
    }
  }"
}

internal fun addonHomeCatalogCandidates(addons: List<InstalledAddon>): List<HomeCatalogRow> {
  val enabledAddons = addons.filter { it.enabled }.sortedBy { it.position }
  val duplicateCatalogNames = enabledAddons
    .flatMap { addon -> addon.manifest.catalogs.map { catalog -> catalog.name.trim().lowercase() to catalog.type.trim().lowercase() } }
    .filter { (_, type) -> type.isNotBlank() }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, values) -> values.toSet().size > 1 }
  return enabledAddons.flatMap { addon ->
    addon.manifest.catalogs.mapIndexedNotNull { index, catalog ->
      val rawType = catalog.type.trim().lowercase()
      if (!catalog.hasHomePreview) return@mapIndexedNotNull null
      val title = addonHomeCatalogTitle(addon.manifest.name, catalog.name.ifBlank { catalog.id }, rawType, duplicateCatalogNames[catalog.name.trim().lowercase()] == true)
      HomeCatalogRow(
        id = "addon:${addon.id}:$rawType:${catalog.id}:$index",
        title = title,
        subtitleRes = R.string.home_row_from_addon,
        subtitleArg = addon.manifest.name,
        builtin = false,
      )
    }
  }
}

/** Custom types such as Newsio's `news` are valid catalogs, even without a TMDB equivalent. */
internal val AddonCatalog.hasHomePreview: Boolean
  get() = type.isNotBlank() && id.isNotBlank() && !requiresSearch

