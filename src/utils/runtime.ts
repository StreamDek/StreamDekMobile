import Constants from 'expo-constants';

export function isExpoGoRuntime(): boolean {
  return Constants.executionEnvironment === 'storeClient';
}

