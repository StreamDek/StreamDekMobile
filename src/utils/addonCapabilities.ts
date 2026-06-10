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
