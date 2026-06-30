import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { WebView } from 'react-native-webview';
import { Ionicons } from '@expo/vector-icons';

type HeroTrailerBackgroundProps = {
  videoId: string;
  muted: boolean;
  play: boolean;
  ready: boolean;
  badgeTop: number;
  onReady: () => void;
  onEnded: () => void;
  onError: () => void;
  onToggleMute: () => void;
};

type LayoutSize = {
  width: number;
  height: number;
};

const FADE_DURATION_MS = 420;
const AUTOPLAY_KICK_MS = 4300;
const AUTOPLAY_RETRY_MS = 1500;
const MAX_AUTOPLAY_RETRIES = 4;

function buildYouTubeHeroHtml(videoId: string): string {
  const safeVideoId = JSON.stringify(videoId);
  return `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
    <style>
      html, body {
        margin: 0;
        padding: 0;
        width: 100%;
        height: 100%;
        background: transparent;
        overflow: hidden;
      }
      #shell {
        position: fixed;
        inset: 0;
        overflow: hidden;
        background: transparent;
      }
      #player {
        position: absolute;
        top: 50%;
        left: 50%;
        width: 177.78vh;
        height: 100vh;
        min-width: 100vw;
        min-height: 56.25vw;
        transform: translate(-50%, -50%);
        pointer-events: none;
      }
      iframe {
        width: 100% !important;
        height: 100% !important;
        border: 0 !important;
        pointer-events: none !important;
      }
    </style>
  </head>
  <body>
    <div id="shell"><div id="player"></div></div>
    <script>
      var tag = document.createElement('script');
      tag.src = 'https://www.youtube.com/iframe_api';
      document.head.appendChild(tag);

      var player = null;
      var ready = false;
      var post = function(payload) {
        window.ReactNativeWebView && window.ReactNativeWebView.postMessage(JSON.stringify(payload));
      };

      function onYouTubeIframeAPIReady() {
        player = new YT.Player('player', {
          videoId: ${safeVideoId},
          playerVars: {
            autoplay: 0,
            controls: 0,
            rel: 0,
            fs: 0,
            iv_load_policy: 3,
            cc_load_policy: 0,
            playsinline: 1,
            modestbranding: 1,
            disablekb: 1,
          },
          events: {
            onReady: function() {
              ready = true;
              try { player.mute(); } catch (error) {}
              post({ type: 'ready' });
            },
            onStateChange: function(event) {
              post({ type: 'state', state: event.data });
            },
            onError: function(event) {
              post({ type: 'error', error: String(event.data || 'unknown') });
            },
          }
        });
      }

      function handleCommand(raw) {
        if (!player || !ready) return;
        var message = {};
        try {
          message = JSON.parse(raw);
        } catch (error) {
          return;
        }
        if (message.type === 'play') {
          try {
            player.seekTo(0, true);
            player.playVideo();
          } catch (error) {}
          return;
        }
        if (message.type === 'mute') {
          try { player.mute(); } catch (error) {}
          return;
        }
        if (message.type === 'unmute') {
          try { player.unMute(); } catch (error) {}
          return;
        }
        if (message.type === 'stop') {
          try { player.stopVideo(); } catch (error) {}
        }
      }

      document.addEventListener('message', function(event) { handleCommand(event.data); });
      window.addEventListener('message', function(event) { handleCommand(event.data); });
    </script>
  </body>
</html>`;
}

export function HeroTrailerBackground({
  videoId,
  muted,
  play,
  ready,
  badgeTop,
  onReady,
  onEnded,
  onError,
  onToggleMute,
}: HeroTrailerBackgroundProps) {
  const [layout, setLayout] = useState<LayoutSize>({ width: 0, height: 0 });
  const [shouldRenderPlayer, setShouldRenderPlayer] = useState(false);
  const [playbackStarted, setPlaybackStarted] = useState(false);
  const [playerReady, setPlayerReady] = useState(false);
  const [autoplayRetryCount, setAutoplayRetryCount] = useState(0);
  const trailerOpacity = useRef(new Animated.Value(0)).current;
  const hasCompletedRef = useRef(false);
  const webViewRef = useRef<WebView | null>(null);

  const html = useMemo(() => buildYouTubeHeroHtml(videoId), [videoId]);
  const isSized = layout.width >= 200 && layout.height >= 200;

  useEffect(() => {
    hasCompletedRef.current = false;
    setPlaybackStarted(false);
    setPlayerReady(false);
    setAutoplayRetryCount(0);
    trailerOpacity.setValue(0);
    setShouldRenderPlayer(Boolean(play && isSized));
  }, [isSized, play, trailerOpacity, videoId]);

  const sendCommand = (payload: Record<string, unknown>) => {
    webViewRef.current?.postMessage(JSON.stringify(payload));
  };

  useEffect(() => {
    if (!shouldRenderPlayer || !play || !playerReady || playbackStarted) return;
    const timer = setTimeout(() => sendCommand({ type: 'play' }), AUTOPLAY_KICK_MS);
    return () => clearTimeout(timer);
  }, [play, playbackStarted, playerReady, shouldRenderPlayer, videoId]);

  useEffect(() => {
    if (!shouldRenderPlayer || !play || !playerReady || playbackStarted || autoplayRetryCount >= MAX_AUTOPLAY_RETRIES) {
      return;
    }
    const retryTimer = setTimeout(() => {
      setAutoplayRetryCount(current => current + 1);
      sendCommand({ type: 'play' });
    }, AUTOPLAY_RETRY_MS);
    return () => clearTimeout(retryTimer);
  }, [autoplayRetryCount, play, playbackStarted, playerReady, shouldRenderPlayer]);

  useEffect(() => {
    if (!shouldRenderPlayer || !playerReady) return;
    sendCommand({ type: muted ? 'mute' : 'unmute' });
  }, [muted, playerReady, shouldRenderPlayer]);

  useEffect(() => {
    if (!shouldRenderPlayer) {
      sendCommand({ type: 'stop' });
    }
  }, [shouldRenderPlayer]);

  const handleLayout = (event: LayoutChangeEvent) => {
    const nextWidth = Math.round(event.nativeEvent.layout.width);
    const nextHeight = Math.round(event.nativeEvent.layout.height);
    if (nextWidth === layout.width && nextHeight === layout.height) return;
    setLayout({ width: nextWidth, height: nextHeight });
  };

  const fadeTrailerTo = (toValue: number, onComplete?: () => void) => {
    Animated.timing(trailerOpacity, {
      toValue,
      duration: FADE_DURATION_MS,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) onComplete?.();
    });
  };

  const completePlayback = (callback: () => void) => {
    if (hasCompletedRef.current) return;
    hasCompletedRef.current = true;
    fadeTrailerTo(0, () => {
      setShouldRenderPlayer(false);
      callback();
    });
  };

  return (
    <Pressable style={styles.container} onLayout={handleLayout} onPress={playbackStarted ? onToggleMute : undefined}>
      {isSized && shouldRenderPlayer ? (
        <Animated.View style={[styles.playerCrop, { opacity: trailerOpacity }]}>
          <WebView
            ref={webViewRef}
            source={{ html }}
            style={styles.webView}
            containerStyle={styles.playerCrop}
            scrollEnabled={false}
            bounces={false}
            originWhitelist={['*']}
            allowsInlineMediaPlayback
            mediaPlaybackRequiresUserAction={false}
            javaScriptEnabled
            domStorageEnabled
            mixedContentMode="always"
            onMessage={(event) => {
              try {
                const payload = JSON.parse(event.nativeEvent.data || '{}');
                if (payload.type === 'ready') {
                  setPlayerReady(true);
                  if (!ready) onReady();
                  return;
                }
                if (payload.type === 'state') {
                  const state = Number(payload.state);
                  if (state === 1 && !playbackStarted) {
                    setPlaybackStarted(true);
                    fadeTrailerTo(1);
                    return;
                  }
                  if (state === 0) {
                    completePlayback(onEnded);
                  }
                  return;
                }
                if (payload.type === 'error') {
                  completePlayback(onError);
                }
              } catch {
                completePlayback(onError);
              }
            }}
            onError={() => completePlayback(onError)}
            setSupportMultipleWindows={false}
            allowsBackForwardNavigationGestures={false}
            androidLayerType="hardware"
            webviewDebuggingEnabled={false}
          />
        </Animated.View>
      ) : null}
      {playbackStarted ? (
        <View style={[styles.audioBadge, { top: badgeTop }]} pointerEvents="none">
          <Ionicons name={muted ? 'volume-mute-outline' : 'volume-high-outline'} size={18} color="#ffffff" />
          <Text style={styles.audioBadgeText}>{muted ? 'Tap for sound' : 'Tap to mute'}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    overflow: 'hidden',
  },
  playerCrop: {
    ...StyleSheet.absoluteFillObject,
    overflow: 'hidden',
    backgroundColor: 'transparent',
  },
  webView: {
    flex: 1,
    backgroundColor: 'transparent',
  },
  audioBadge: {
    position: 'absolute',
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    backgroundColor: 'rgba(15,23,42,0.56)',
  },
  audioBadgeText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
});
