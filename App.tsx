import React from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import { BlurTargetView } from 'expo-blur';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { NavigationContainer, DarkTheme, DefaultTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StatusBar } from 'expo-status-bar';
import { HomeScreen } from './src/screens/HomeScreen';
import { PlayerScreen } from './src/screens/PlayerScreen';
import { BrowseScreen } from './src/screens/BrowseScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { SettingsShellScreen } from './src/screens/SettingsShellScreen';
import { MediaDetailScreen } from './src/screens/MediaDetailScreen';
import { WatchlistScreen } from './src/screens/WatchlistScreen';
import { SearchScreen } from './src/screens/SearchScreen';
import { AddonsScreen } from './src/screens/AddonsScreen';
import { AuthScreen } from './src/screens/AuthScreen';
import { LinkTvScreen } from './src/screens/LinkTvScreen';
import { TraktSettingsScreen } from './src/screens/TraktSettingsScreen';
import { ContinueWatchingScreen } from './src/screens/ContinueWatchingScreen';
import { TraktCollectionScreen } from './src/screens/TraktCollectionScreen';
import { EpisodeStreamsScreen } from './src/screens/EpisodeStreamsScreen';
import { MpvPlayerScreen } from './src/screens/MpvPlayerScreen';
import { BottomNavBar } from './src/components/BottomNavBar';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import { ThemeProvider } from './src/context/ThemeContext';
import { TraktProvider } from './src/context/TraktContext';
import { DebridProvider } from './src/context/DebridContext';
import { AddonProvider } from './src/context/AddonContext';
import { LanguageProvider } from './src/context/LanguageContext';
import { WatchedProvider } from './src/context/WatchedContext';
import { WatchProgressProvider } from './src/context/WatchProgressContext';
import { AppReadyProvider } from './src/context/AppReadyContext';
import { AnimatedSplash } from './src/components/AnimatedSplash';
import { RadialLoaderScreen } from './src/components/RadialLoaderScreen';
import { TorrentServerProvider } from './src/context/TorrentServerContext';
import { StreamSelectionProvider } from './src/context/StreamSelectionContext';
import { PlaybackSettingsProvider } from './src/context/PlaybackSettingsContext';
import { SubtitleProvider } from './src/context/SubtitleContext';
import { UIStyleProvider } from './src/context/UIStyleContext';
import { DisplaySettingsProvider } from './src/context/DisplaySettingsContext';
import { TmdbApiKeyProvider } from './src/context/TmdbApiKeyContext';
import { ProfileProvider, useProfile } from './src/context/ProfileContext';
import { AppLifecycleProvider } from './src/context/AppLifecycleContext';
import { ProfileSwitcherScreen } from './src/screens/ProfileSwitcherScreen';
import { ManageProfilesScreen } from './src/screens/ManageProfilesScreen';
import { EditProfileScreen } from './src/screens/EditProfileScreen';
import { useTheme } from './src/context/ThemeContext';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

function MainTabs({ blurTargetRef }: { blurTargetRef: React.RefObject<View | null> }) {
  return (
    <Tab.Navigator
      tabBar={() => null}
      backBehavior="history"
      screenOptions={{ headerShown: false }}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Search" component={SearchScreen} />
      <Tab.Screen name="ContinueWatching" component={ContinueWatchingScreen} />
      <Tab.Screen name="Watchlist" component={WatchlistScreen} />
      <Tab.Screen name="Settings" component={SettingsShellScreen} />
    </Tab.Navigator>
  );
}

function AppNavigation() {
  const appBlurTargetRef = React.useRef<View | null>(null);
  const { theme, resolvedAppearance } = useTheme();
  const { user, authLoading } = useAuth();
  const { activeProfile, profilesReady, profileSwitching } = useProfile();
  const showProfileSwitcher = !authLoading && !!user && profilesReady && !activeProfile;
  const shouldBlockInitialAppReveal = authLoading || (!!user && !profilesReady);
  const appearanceFade = React.useRef(new Animated.Value(1)).current;
  const profileOverlayOpacity = React.useRef(new Animated.Value(showProfileSwitcher || profileSwitching ? 1 : 0)).current;
  const profileContentFade = React.useRef(new Animated.Value(showProfileSwitcher || profileSwitching ? 0.96 : 1)).current;
  const [profileOverlayMounted, setProfileOverlayMounted] = React.useState(showProfileSwitcher || profileSwitching);
  const showProfileLoaderOverlay = profileSwitching || (profileOverlayMounted && !showProfileSwitcher);
  const previousOverlayActiveRef = React.useRef(showProfileSwitcher || profileSwitching);
  const launchCoverOpacity = React.useRef(new Animated.Value(shouldBlockInitialAppReveal ? 1 : 0)).current;
  const linking = React.useMemo(() => ({
    prefixes: ['streamdek://'],
    config: {
      screens: {
        Main: '',
        Detail: 'detail',
        Player: 'player',
        MpvPlayer: 'mpv-player',
        LegacyPlayer: 'legacy-player',
        Browse: 'browse',
        Addons: 'addons',
        Auth: 'auth',
        TraktSettings: 'trakt-settings',
        TraktCollection: 'trakt-collection',
        EpisodeStreams: 'episode-streams',
        LinkTv: {
          path: 'link-tv',
          parse: {
            code: (value: string) => String(value ?? ''),
          },
        },
      },
    },
  }), []);
  const navTheme = React.useMemo(
    () => ({
      ...(resolvedAppearance === 'light' ? DefaultTheme : DarkTheme),
      colors: {
        ...(resolvedAppearance === 'light' ? DefaultTheme.colors : DarkTheme.colors),
        background: theme.colors.bg,
        card: theme.colors.cardBg,
        border: theme.colors.border,
        primary: theme.colors.accent,
        text: theme.colors.textPrimary,
      },
    }),
    [resolvedAppearance, theme.colors],
  );

  React.useEffect(() => {
    appearanceFade.setValue(0.92);
    Animated.timing(appearanceFade, {
      toValue: 1,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [appearanceFade, resolvedAppearance, theme.id]);

  React.useEffect(() => {
    launchCoverOpacity.stopAnimation();
    if (shouldBlockInitialAppReveal) {
      launchCoverOpacity.setValue(1);
      return;
    }

    Animated.timing(launchCoverOpacity, {
      toValue: 0,
      duration: 220,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [launchCoverOpacity, shouldBlockInitialAppReveal]);

  React.useEffect(() => {
    const overlayActive = showProfileSwitcher || profileSwitching;
    const wasOverlayActive = previousOverlayActiveRef.current;
    previousOverlayActiveRef.current = overlayActive;

    if (overlayActive) {
      setProfileOverlayMounted(true);
      profileOverlayOpacity.stopAnimation();
      profileContentFade.stopAnimation();
      profileOverlayOpacity.setValue(1);
      if (!wasOverlayActive) {
        profileContentFade.setValue(0.98);
        Animated.timing(profileContentFade, {
          toValue: 0.96,
          duration: 220,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }).start();
      } else {
        profileContentFade.setValue(0.96);
      }
      return;
    }

    if (!profileOverlayMounted) {
      profileContentFade.setValue(1);
      return;
    }

    Animated.parallel([
      Animated.timing(profileOverlayOpacity, {
        toValue: 0,
        duration: 320,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(profileContentFade, {
        toValue: 1,
        duration: 340,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start(() => {
      setProfileOverlayMounted(false);
      profileOverlayOpacity.setValue(0);
    });
  }, [
    profileContentFade,
    profileOverlayMounted,
    profileOverlayOpacity,
    profileSwitching,
    showProfileSwitcher,
  ]);

  return (
    <NavigationContainer theme={navTheme} linking={linking}>
      <StatusBar style={resolvedAppearance === 'light' ? 'dark' : 'light'} />
      <BlurTargetView ref={appBlurTargetRef} style={{ flex: 1 }}>
        <Animated.View style={{ flex: 1, opacity: Animated.multiply(appearanceFade, profileContentFade) }}>
          <Stack.Navigator
            screenOptions={{
              headerStyle: { backgroundColor: theme.colors.bgHeaderSolid },
              headerShadowVisible: false,
              headerTintColor: theme.colors.textPrimary,
              headerTitleStyle: { fontWeight: '700', fontSize: 17, color: theme.colors.textPrimary },
              contentStyle: { backgroundColor: theme.colors.bg },
              animation: 'fade',
              animationDuration: 280,
            }}
          >
            <Stack.Screen name="Main" options={{ headerShown: false }}>
              {() => <MainTabs blurTargetRef={appBlurTargetRef} />}
            </Stack.Screen>
            <Stack.Screen name="Detail" component={MediaDetailScreen} options={{ headerShown: false, animationDuration: 280 }} />
            <Stack.Screen
              name="Player"
              component={PlayerScreen}
              options={{ headerShown: false, animation: 'fade', animationDuration: 260 }}
            />
            <Stack.Screen
              name="MpvPlayer"
              component={MpvPlayerScreen}
              options={{ headerShown: false, animation: 'fade', animationDuration: 260 }}
            />
            <Stack.Screen
              name="LegacyPlayer"
              component={PlayerScreen}
              options={{ headerShown: false, animation: 'fade', animationDuration: 260 }}
            />
            <Stack.Screen name="Browse" component={BrowseScreen} options={{ headerShown: false, animationDuration: 280 }} />
            <Stack.Screen
              name="Addons"
              component={AddonsScreen}
              options={{ headerShown: false, presentation: 'modal', animation: 'slide_from_bottom', animationDuration: 300 }}
            />
            <Stack.Screen name="SettingsDetail" component={SettingsScreen} options={{ headerShown: false, animationDuration: 260 }} />
            <Stack.Screen name="Auth" component={AuthScreen} options={{ headerShown: false, presentation: 'modal', animation: 'slide_from_bottom', animationDuration: 300 }} />
            <Stack.Screen name="LinkTv" component={LinkTvScreen} options={{ headerShown: false, presentation: 'modal', animation: 'slide_from_bottom', animationDuration: 300 }} />
            <Stack.Screen
              name="TraktSettings"
              component={TraktSettingsScreen}
              options={{ headerShown: false, presentation: 'modal', animation: 'slide_from_bottom', animationDuration: 300 }}
            />
            <Stack.Screen name="TraktCollection" component={TraktCollectionScreen} options={{ headerShown: false, animationDuration: 280 }} />
            <Stack.Screen name="EpisodeStreams" component={EpisodeStreamsScreen} options={{ headerShown: false, animationDuration: 280 }} />
            <Stack.Screen name="ManageProfiles" component={ManageProfilesScreen} options={{ headerShown: false, animationDuration: 280 }} />
            <Stack.Screen
              name="ProfileSwitcher"
              component={ProfileSwitcherScreen}
              options={{ headerShown: false, presentation: 'transparentModal', animation: 'fade', animationDuration: 220 }}
            />
            <Stack.Screen
              name="EditProfile"
              component={EditProfileScreen}
              options={{ headerShown: false, animationDuration: 280 }}
            />
          </Stack.Navigator>
        </Animated.View>
      </BlurTargetView>
      {profileOverlayMounted && (
        <Animated.View style={[StyleSheet.absoluteFillObject, { opacity: profileOverlayOpacity, backgroundColor: '#000000' }]}>
          {showProfileLoaderOverlay ? (
            <View style={styles.profileLoaderOverlay}>
              <RadialLoaderScreen />
            </View>
          ) : (
            <ProfileSwitcherScreen asOverlay />
          )}
        </Animated.View>
      )}
      <Animated.View
        pointerEvents="none"
        style={[
          StyleSheet.absoluteFillObject,
          {
            opacity: launchCoverOpacity,
            backgroundColor: '#000000',
          },
        ]}
      />
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  profileLoaderOverlay: {
    flex: 1,
    backgroundColor: '#000000',
  },
});

export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
      <AppReadyProvider>
        <AuthProvider>
          <ProfileProvider>
            <ThemeProvider>
              <UIStyleProvider>
              <DisplaySettingsProvider>
              <LanguageProvider>
                <TraktProvider>
                  <AppLifecycleProvider>
                    <WatchProgressProvider>
                      <WatchedProvider>
                        <TorrentServerProvider>
                          <StreamSelectionProvider>
                            <PlaybackSettingsProvider>
                              <SubtitleProvider>
                              <DebridProvider>
                                <AddonProvider>
                                  <TmdbApiKeyProvider>
                                    <AppNavigation />
                                    <AnimatedSplash />
                                  </TmdbApiKeyProvider>
                                </AddonProvider>
                              </DebridProvider>
                              </SubtitleProvider>
                            </PlaybackSettingsProvider>
                          </StreamSelectionProvider>
                        </TorrentServerProvider>
                      </WatchedProvider>
                    </WatchProgressProvider>
                  </AppLifecycleProvider>
                </TraktProvider>
              </LanguageProvider>
              </DisplaySettingsProvider>
              </UIStyleProvider>
            </ThemeProvider>
          </ProfileProvider>
        </AuthProvider>
      </AppReadyProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
