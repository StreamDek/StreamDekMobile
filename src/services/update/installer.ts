import { installApk } from './nativeBridge';

export async function launchUpdateInstaller(filePath: string) {
  return installApk(filePath);
}
