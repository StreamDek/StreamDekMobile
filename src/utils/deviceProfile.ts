import { Dimensions, Platform } from 'react-native';

export type DevicePerformanceClass = 'low' | 'medium' | 'high';

export interface DeviceProfile {
  isTv: boolean;
  performanceClass: DevicePerformanceClass;
  heroPrefetchCount: number;
  sectionPrefetchCount: number;
  enableHeavyBlur: boolean;
  enableExtendedAnimations: boolean;
}

let cachedProfile: DeviceProfile | null = null;

export function getDeviceProfile(): DeviceProfile {
  if (cachedProfile) return cachedProfile;

  const { width, height } = Dimensions.get('window');
  const isTv = Platform.isTV === true || (Platform.OS === 'android' && Math.max(width, height) >= 960);

  let performanceClass: DevicePerformanceClass = 'high';
  if (isTv) {
    performanceClass = 'low';
  } else if (Math.max(width, height) < 900) {
    performanceClass = 'medium';
  }

  cachedProfile = {
    isTv,
    performanceClass,
    heroPrefetchCount: performanceClass === 'low' ? 4 : performanceClass === 'medium' ? 6 : 8,
    sectionPrefetchCount: performanceClass === 'low' ? 12 : performanceClass === 'medium' ? 24 : 36,
    enableHeavyBlur: performanceClass !== 'low',
    enableExtendedAnimations: performanceClass === 'high',
  };

  return cachedProfile;
}
