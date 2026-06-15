import { profileScopedStorageKey } from './profileStorage';
import type { HomeCatalogSection } from './homeCatalogSections';

export function getHomeSectionStorageKeys(userId: string | null | undefined, profileId: string | null | undefined): string[] {
  const keys = [
    profileScopedStorageKey('home_sections', userId, profileId),
  ];
  if (userId && profileId) {
    keys.push(profileScopedStorageKey('home_sections', userId, null));
  }
  keys.push('home_sections');
  return Array.from(new Set(keys));
}

export function mergeSavedHomeSections(
  savedSections: any[],
  defaultSections: HomeCatalogSection[],
  preferredProvider: 'cinemeta' | 'tmdb',
): HomeCatalogSection[] {
  const savedMap = new Map(savedSections.map(section => [String(section?.id ?? ''), section]));
  const mappedIds = new Set<string>();

  const resolveSectionId = (id: string): string | null => {
    if (defaultSections.some(section => section.id === id)) return id;

    const suffixMatches = defaultSections.filter(section => section.id.endsWith(`:${id}`));
    if (suffixMatches.length === 1) return suffixMatches[0].id;
    if (suffixMatches.length > 1) {
      return suffixMatches.find(section => section.provider === preferredProvider)?.id ?? suffixMatches[0].id;
    }
    return null;
  };

  const known = savedSections.flatMap<HomeCatalogSection>(section => {
    const resolvedId = resolveSectionId(String(section?.id ?? ''));
    if (!resolvedId) return [];
    if (mappedIds.has(resolvedId)) return [];
    mappedIds.add(resolvedId);
    const match = defaultSections.find(defaultSection => defaultSection.id === resolvedId);
    if (!match) return [];
    return [{
      ...match,
      enabled: Boolean(section?.enabled),
    }];
  });

  const newOnes = defaultSections.filter(section => !mappedIds.has(section.id) && !savedMap.has(section.id));
  return [...known, ...newOnes];
}
