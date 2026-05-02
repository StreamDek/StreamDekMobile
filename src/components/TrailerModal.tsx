import React, { useMemo } from 'react';
import {
  Modal, View, StyleSheet, TouchableOpacity, Text,
  ActivityIndicator, StatusBar, Dimensions, Linking,
} from 'react-native';
import { WebView } from 'react-native-webview';
import YoutubePlayer from 'react-native-youtube-iframe';
import { useTheme, ThemeColors } from '../context/ThemeContext';

interface TrailerModalProps {
  visible: boolean;
  trailerKey: string | null;
  trailerKeys?: string[];
  trailerSite?: string | null;
  vimeoKey?: string | null;
  onClose: () => void;
}

const { width } = Dimensions.get('window');
const PLAYER_HEIGHT = (width - 32) * 0.5625; // 16:9

const makeStyles = (c: ThemeColors, isLightAppearance: boolean) => StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: isLightAppearance ? 'rgba(15,23,42,0.62)' : 'rgba(0,0,0,0.92)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  container: {
    width: width - 32,
    borderRadius: 18,
    backgroundColor: c.cardBg,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: c.border,
    shadowColor: '#000',
    shadowOpacity: isLightAppearance ? 0.14 : 0.32,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 20,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    backgroundColor: isLightAppearance ? c.bgMid : '#181818',
    borderBottomWidth: 1,
    borderBottomColor: c.border,
  },
  title: { color: c.textPrimary, fontSize: 16, fontWeight: '700' },
  closeBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: isLightAppearance ? c.inputBg : '#ffffff14',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: isLightAppearance ? 1 : 0,
    borderColor: isLightAppearance ? c.border : 'transparent',
  },
  closeText: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
  playerWrapper: { height: PLAYER_HEIGHT, backgroundColor: isLightAppearance ? c.bg : '#000' },
  player: { flex: 1 },
  hidden: { opacity: 0 },
  loader: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: isLightAppearance ? c.bg : '#000',
  },
  loaderText: { color: c.textSecondary, marginTop: 12, fontSize: 13 },
  noTrailer: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  noTrailerText: { color: c.textSecondary, fontSize: 15, textAlign: 'center', marginBottom: 20 },
  openBtn: {
    backgroundColor: c.accent + '18',
    borderWidth: 1,
    borderColor: c.accent + '44',
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  openBtnText: { color: c.accentSoft, fontWeight: '700', fontSize: 14 },
});

export const TrailerModal: React.FC<TrailerModalProps> = ({
  visible, trailerKey, trailerKeys = [], trailerSite, vimeoKey, onClose,
}) => {
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const isLightAppearance = resolvedAppearance === 'light';
  const themeStyles = useMemo(() => makeStyles(colors, isLightAppearance), [colors, isLightAppearance]);
  const [ready, setReady] = React.useState(false);
  const [keyIndex, setKeyIndex] = React.useState(0);
  const [blocked, setBlocked] = React.useState(false);

  // Build the full ordered list of YouTube keys to try
  const allKeys = React.useMemo(() => {
    const seen = new Set<string>();
    const list: string[] = [];
    for (const k of [trailerKey, ...trailerKeys]) {
      if (k && !seen.has(k)) { seen.add(k); list.push(k); }
    }
    return list;
  }, [trailerKey, trailerKeys]);

  const activeYtKey = allKeys[keyIndex] ?? null;

  React.useEffect(() => {
    if (visible) { setReady(false); setKeyIndex(0); setBlocked(false); }
  }, [visible, trailerKey, vimeoKey]);

  const handleYtError = (e: string) => {
    if (e === 'embed_not_allowed' || e === '101' || e === '150') {
      if (keyIndex + 1 < allKeys.length) {
        setReady(false);
        setKeyIndex(i => i + 1);
      } else {
        setBlocked(true);
      }
    }
  };

  const openInYouTube = () => {
    if (activeYtKey) Linking.openURL(`https://www.youtube.com/watch?v=${activeYtKey}`);
  };

  // Prefer Vimeo — no sign-in wall, reliable embedding
  const useVimeo = !!(vimeoKey || trailerSite === 'Vimeo');
  const vimeoUri = useVimeo && (vimeoKey || trailerKey)
    ? `https://player.vimeo.com/video/${vimeoKey || trailerKey}?autoplay=1&playsinline=1&color=6c63ff&title=0&byline=0`
    : null;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <StatusBar backgroundColor={isLightAppearance ? colors.bg : 'rgba(0,0,0,0.95)'} barStyle={isLightAppearance ? 'dark-content' : 'light-content'} />
      <View style={[styles.backdrop, themeStyles.backdrop]}>
        <View style={[styles.container, themeStyles.container]}>
          <View style={[styles.header, themeStyles.header]}>
            <Text style={[styles.title, themeStyles.title]}>Trailer</Text>
            <TouchableOpacity onPress={onClose} style={[styles.closeBtn, themeStyles.closeBtn]} activeOpacity={0.7}>
              <Text style={[styles.closeText, themeStyles.closeText]}>x</Text>
            </TouchableOpacity>
          </View>

          <View style={[styles.playerWrapper, themeStyles.playerWrapper]}>
            {!ready && !blocked && (
              <View style={[styles.loader, themeStyles.loader]}>
                <ActivityIndicator size="large" color={colors.accent} />
                <Text style={[styles.loaderText, themeStyles.loaderText]}>Loading trailer...</Text>
              </View>
            )}

            {blocked ? (
              <View style={styles.noTrailer}>
                <Text style={[styles.noTrailerText, themeStyles.noTrailerText]}>Embedding disabled by publisher</Text>
                {activeYtKey && (
                  <TouchableOpacity style={[styles.openBtn, themeStyles.openBtn]} onPress={openInYouTube} activeOpacity={0.8}>
                    <Text style={[styles.openBtnText, themeStyles.openBtnText]}>Open in YouTube</Text>
                  </TouchableOpacity>
                )}
              </View>
            ) : useVimeo && vimeoUri ? (
              <WebView
                source={{ uri: vimeoUri }}
                style={[styles.player, !ready && styles.hidden]}
                allowsInlineMediaPlayback
                mediaPlaybackRequiresUserAction={false}
                javaScriptEnabled
                domStorageEnabled
                onLoad={() => setReady(true)}
              />
            ) : activeYtKey ? (
              <YoutubePlayer
                key={activeYtKey}
                height={PLAYER_HEIGHT}
                width={width - 32}
                videoId={activeYtKey}
                play={visible && ready === false}
                onReady={() => setReady(true)}
                onError={handleYtError}
                webViewProps={{
                  allowsInlineMediaPlayback: true,
                  mediaPlaybackRequiresUserAction: false,
                  domStorageEnabled: true,
                  thirdPartyCookiesEnabled: true,
                  userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
                }}
              />
            ) : (
              <View style={styles.noTrailer}>
                <Text style={[styles.noTrailerText, themeStyles.noTrailerText]}>No trailer available</Text>
              </View>
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.92)',
    justifyContent: 'center', alignItems: 'center',
  },
  container: {
    width: width - 32, borderRadius: 18,
    backgroundColor: '#0f0f0f', overflow: 'hidden',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)',
  },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 20, paddingVertical: 16,
    backgroundColor: '#181818', borderBottomWidth: 1, borderBottomColor: '#282828',
  },
  title: { color: '#fff', fontSize: 16, fontWeight: '700' },
  closeBtn: {
    width: 34, height: 34, borderRadius: 17, backgroundColor: '#ffffff14',
    justifyContent: 'center', alignItems: 'center',
  },
  closeText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  playerWrapper: { height: PLAYER_HEIGHT, backgroundColor: '#000' },
  player: { flex: 1 },
  hidden: { opacity: 0 },
  loader: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
    justifyContent: 'center', alignItems: 'center', backgroundColor: '#000',
  },
  loaderText: { color: '#7070a0', marginTop: 12, fontSize: 13 },
  noTrailer: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  noTrailerText: { color: '#5555a0', fontSize: 15, textAlign: 'center', marginBottom: 20 },
  openBtn: {
    backgroundColor: '#ff000022', borderWidth: 1, borderColor: '#ff000055',
    borderRadius: 12, paddingHorizontal: 24, paddingVertical: 12,
  },
  openBtnText: { color: '#c47070', fontWeight: '700', fontSize: 14 },
});
