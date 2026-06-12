import type { InstalledAddon } from '../context/AddonContext';

type AddonResource = string | { name?: string | null; types?: string[] | null };

function normalizeResourceName(resource: AddonResource): string {
  if (typeof resource === 'string') return resource.trim().toLowerCase();
  return (resource?.name ?? '').trim().toLowerCase();
}

export function addonSupportsStreams(addon: Pick<InstalledAddon, 'manifest'> | null | undefined): boolean {
  const resources = Array.isArray(addon?.manifest?.resources) ? addon.manifest.resources : [];
  return resources.some(resource => normalizeResourceName(resource) === 'stream');
}

export function getStreamCapableAddons(addons: InstalledAddon[]): InstalledAddon[] {
  return addons.filter(addon => addon.enabled && addonSupportsStreams(addon));
}

function resourceEntryTypes(resource: AddonResource): string[] | null {
  if (typeof resource === 'string') return null;
  return Array.isArray(resource?.types)
    ? resource.types.map(type => String(type).trim().toLowerCase())
    : null;
}

/** Maps the app-internal content type to the Stremio-native stream type. */
export function toNativeStreamType(type: string): string {
  const normalized = type.trim().toLowerCase();
  if (normalized === 'tv') return 'series';
  if (normalized === 'live-tv') return 'tv';
  return normalized;
}

/**
 * Whether an addon serves streams for a given Stremio-native type. Live-only
 * addons (types like 'tv'/'events'/'sport') declare no 'movie'/'series'
 * support and must not be queried — or shown as sources — for those titles.
 */
export function addonSupportsStreamType(
  addon: Pick<InstalledAddon, 'manifest'> | null | undefined,
  nativeType: string,
): boolean {
  if (!addonSupportsStreams(addon)) return false;
  const normalizedType = nativeType.trim().toLowerCase();
  const resources = Array.isArray(addon?.manifest?.resources) ? addon.manifest.resources : [];
  const streamResourceTypes = resources
    .filter(resource => normalizeResourceName(resource) === 'stream')
    .map(resourceEntryTypes)
    .filter((types): types is string[] => Array.isArray(types) && types.length > 0);
  if (streamResourceTypes.length > 0) {
    return streamResourceTypes.some(types => types.includes(normalizedType));
  }
  const manifestTypes = Array.isArray(addon?.manifest?.types)
    ? addon.manifest.types.map(type => String(type).trim().toLowerCase())
    : [];
  // No declared types — assume the addon serves everything.
  if (manifestTypes.length === 0) return true;
  return manifestTypes.includes(normalizedType);
}

export function getStreamCapableAddonsForType(addons: InstalledAddon[], nativeType: string): InstalledAddon[] {
  return addons.filter(addon => addon.enabled && addonSupportsStreamType(addon, nativeType));
}
