export function getProfileStorageOwnerId(
  userId: string | null | undefined,
  profileId: string | null | undefined,
): string | null {
  if (!userId) return null;
  return profileId ? `${userId}__profile__${profileId}` : userId;
}

export function profileScopedStorageKey(
  baseKey: string,
  userId: string | null | undefined,
  profileId: string | null | undefined,
): string {
  const ownerId = getProfileStorageOwnerId(userId, profileId);
  return ownerId ? `${baseKey}_${ownerId}` : `${baseKey}_guest`;
}

export function progressStorageKey(ownerId: string | null | undefined): string {
  return ownerId ? `streamdek_progress_v1_${ownerId}` : 'streamdek_progress_v1_guest';
}

export function progressIndexStorageKey(ownerId: string | null | undefined): string {
  return ownerId ? `streamdek_progress_index_${ownerId}` : 'streamdek_progress_index';
}

export function progressFileStorageKey(
  ownerId: string | null | undefined,
  itemKey: string,
): string {
  return ownerId ? `streamdek_progress_${ownerId}_${itemKey}` : `streamdek_progress_${itemKey}`;
}
