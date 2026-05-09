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
    heroPrefetchCount: performanceClass === 'low' ? 2 : performanceClass === 'medium' ? 4 : 6,
    sectionPrefetchCount: performanceClass === 'low' ? 6 : performanceClass === 'medium' ? 12 : 24,
    enableHeavyBlur: performanceClass === 'high',
    enableExtendedAnimations: performanceClass === 'high',
  };

  return cachedProfile;
}
