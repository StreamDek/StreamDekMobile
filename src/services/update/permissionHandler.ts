import { canRequestPackageInstalls, openUnknownAppSourcesSettings } from './nativeBridge';

export async function ensureInstallPermission(): Promise<{ granted: true } | { granted: false; openedSettings: boolean }> {
  const granted = await canRequestPackageInstalls();
  if (granted) {
    return { granted: true };
  }

  await openUnknownAppSourcesSettings();
  return { granted: false, openedSettings: true };
}
