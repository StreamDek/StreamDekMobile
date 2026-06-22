import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, Animated, Easing,
  TouchableOpacity, FlatList, Dimensions,
} from 'react-native';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';
import { useLanguage } from '../context/LanguageContext';
import { tmdbFetch } from '../utils/tmdbFetch';

const PHOTO_SIZE = 130;

function formatBirthday(dateStr: string | null, deathday: string | null): string {
  if (!dateStr) return '';
  const birth = new Date(dateStr);
  const month = birth.toLocaleString('en-US', { month: 'short' });
  const day = birth.getDate();
  const year = birth.getFullYear();
  if (deathday) {
    const death = new Date(deathday);
    const age = death.getFullYear() - birth.getFullYear() -
      (death < new Date(death.getFullYear(), birth.getMonth(), birth.getDate()) ? 1 : 0);
    return `Born ${month} ${day}, ${year} (${age} at death)`;
  }
  const now = new Date();
  const age = now.getFullYear() - birth.getFullYear() -
    (now < new Date(now.getFullYear(), birth.getMonth(), birth.getDate()) ? 1 : 0);
  return `Born ${month} ${day}, ${year}(age ${age})`;
}

export const PersonDetailScreen = ({ route, navigation }: any) => {
  const { personId, name: initialName, photo: initialPhoto } = route.params || {};
  const { theme, resolvedAppearance } = useTheme();
  const { t } = useLanguage();
  const insets = useSafeAreaInsets();
  const isLight = resolvedAppearance === 'light';
  const c = theme.colors;

  const [person, setPerson] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [bioExpanded, setBioExpanded] = useState(false);

  const photoScale = useRef(new Animated.Value(0.35)).current;
  const photoOpacity = useRef(new Animated.Value(0)).current;
  const contentOpacity = useRef(new Animated.Value(0)).current;
  const headerOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(photoScale, {
        toValue: 1,
        tension: 55,
        friction: 7,
        useNativeDriver: true,
      }),
      Animated.timing(photoOpacity, {
        toValue: 1,
        duration: 180,
        useNativeDriver: true,
      }),
      Animated.timing(headerOpacity, {
        toValue: 1,
        duration: 220,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const res = await tmdbFetch(`/tmdb/person/${personId}`);
      if (cancelled) return;
      if (res.ok) {
        const data = await res.json();
        setPerson(data);
      }
      setLoading(false);
      Animated.timing(contentOpacity, {
        toValue: 1,
        duration: 300,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }).start();
    };
    void load();
    return () => { cancelled = true; };
  }, [personId]);

  const displayName = person?.name ?? initialName ?? '';
  const displayPhoto = person?.photo ?? initialPhoto ?? null;

  const renderWorkCard = ({ item }: { item: any }) => (
    <TouchableOpacity
      style={styles.workCard}
      activeOpacity={0.75}
      onPress={() => navigation.navigate('Detail', { movieId: item.tmdbId, type: item.mediaType })}
    >
      {item.poster ? (
        <Image
          source={{ uri: item.poster }}
          style={styles.workPoster}
          contentFit="cover"
          transition={200}
        />
      ) : (
        <View style={[styles.workPoster, { backgroundColor: c.cardBg, justifyContent: 'center', alignItems: 'center' }]}>
          <Ionicons name="film-outline" size={28} color={c.mutedText} />
        </View>
      )}
      <Text style={[styles.workTitle, { color: isLight ? c.textPrimary : '#e8e8f0' }]} numberOfLines={2}>
        {item.title}
      </Text>
      {item.year ? (
        <Text style={[styles.workYear, { color: c.mutedText }]}>{item.year}</Text>
      ) : null}
    </TouchableOpacity>
  );

  const bioText = person?.biography ?? '';
  const bioLong = bioText.length > 240;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]}>
      {/* Back button */}
      <Animated.View style={[styles.backBtnWrapper, { top: insets.top + 6, opacity: headerOpacity }]}>
        <TouchableOpacity
          style={styles.backBtn}
          onPress={() => navigation.goBack()}
          hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
        >
          <Ionicons name="chevron-back" size={28} color={isLight ? c.textPrimary : '#ffffff'} />
        </TouchableOpacity>
      </Animated.View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={[styles.scrollContent, { paddingTop: insets.top + 48, paddingBottom: insets.bottom + 40 }]}
      >
        {/* Profile photo — animates in with zoom */}
        <Animated.View
          style={[
            styles.photoWrapper,
            {
              opacity: photoOpacity,
              transform: [{ scale: photoScale }],
              shadowColor: c.accent,
            },
          ]}
        >
          {displayPhoto ? (
            <Image source={{ uri: displayPhoto }} style={styles.photo} contentFit="cover" />
          ) : (
            <View style={[styles.photo, { backgroundColor: c.cardBg, justifyContent: 'center', alignItems: 'center' }]}>
              <Text style={{ fontSize: 52 }}>🎭</Text>
            </View>
          )}
        </Animated.View>

        {/* Name visible as soon as screen opens */}
        <Animated.Text
          style={[styles.name, { color: isLight ? c.textPrimary : '#ffffff', opacity: photoOpacity }]}
        >
          {displayName}
        </Animated.Text>

        {/* Rest of content fades in after data loads */}
        <Animated.View style={{ opacity: contentOpacity }}>
          {person?.birthday ? (
            <Text style={[styles.metaLine, { color: isLight ? c.textSecondary : c.subText }]}>
              {formatBirthday(person.birthday, person.deathday)}
            </Text>
          ) : null}

          {person?.placeOfBirth ? (
            <Text style={[styles.metaLine, { color: isLight ? c.textSecondary : c.subText }]}>
              {person.placeOfBirth}
            </Text>
          ) : null}

          {person?.knownFor ? (
            <Text style={[styles.metaLine, { color: isLight ? c.textSecondary : c.subText }]}>
              Known for: {person.knownFor}
            </Text>
          ) : null}

          {/* Biography */}
          {bioText ? (
            <View style={styles.bioSection}>
              <Text
                style={[styles.bio, { color: isLight ? c.textPrimary : '#c8c8d8' }]}
                numberOfLines={bioExpanded ? undefined : 6}
              >
                {bioText}
              </Text>
              {bioLong && (
                <TouchableOpacity onPress={() => setBioExpanded(v => !v)} activeOpacity={0.7}>
                  <Text style={[styles.bioToggle, { color: c.accent }]}>
                    {bioExpanded ? 'Show less' : 'Read more'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          ) : null}

          {/* Loading skeletons */}
          {loading && (
            <View style={styles.skeletonRow}>
              {[0, 1, 2].map(i => (
                <View key={i} style={[styles.skeletonCard, { backgroundColor: c.cardBg }]} />
              ))}
            </View>
          )}

          {/* Popular works */}
          {!loading && person?.popularWorks?.length > 0 && (
            <View style={styles.section}>
              <Text style={[styles.sectionTitle, { color: isLight ? c.textPrimary : '#ffffff' }]}>Popular</Text>
              <FlatList
                horizontal
                data={person.popularWorks}
                keyExtractor={(item: any) => `pop-${item.mediaType}-${item.id}`}
                showsHorizontalScrollIndicator={false}
                style={{ marginHorizontal: -16 }}
                contentContainerStyle={styles.worksRow}
                renderItem={renderWorkCard}
              />
            </View>
          )}

          {/* Latest works */}
          {!loading && person?.latestWorks?.length > 0 && (
            <View style={styles.section}>
              <Text style={[styles.sectionTitle, { color: isLight ? c.textPrimary : '#ffffff' }]}>Latest</Text>
              <FlatList
                horizontal
                data={person.latestWorks}
                keyExtractor={(item: any) => `lat-${item.mediaType}-${item.id}`}
                showsHorizontalScrollIndicator={false}
                style={{ marginHorizontal: -16 }}
                contentContainerStyle={styles.worksRow}
                renderItem={renderWorkCard}
              />
            </View>
          )}
        </Animated.View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    alignItems: 'center',
  },
  backBtnWrapper: {
    position: 'absolute',
    left: 16,
    zIndex: 10,
  },
  backBtn: {
    padding: 4,
  },
  photoWrapper: {
    borderRadius: PHOTO_SIZE / 2,
    overflow: 'hidden',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.35,
    shadowRadius: 14,
    elevation: 10,
    marginBottom: 16,
  },
  photo: {
    width: PHOTO_SIZE,
    height: PHOTO_SIZE,
    borderRadius: PHOTO_SIZE / 2,
  },
  name: {
    fontSize: 28,
    fontWeight: '700',
    textAlign: 'center',
    letterSpacing: -0.4,
    marginHorizontal: 24,
    marginBottom: 6,
  },
  metaLine: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 3,
    marginHorizontal: 24,
  },
  bioSection: {
    alignSelf: 'stretch',
    marginTop: 18,
    marginHorizontal: 16,
  },
  bio: {
    fontSize: 14,
    lineHeight: 22,
  },
  bioToggle: {
    marginTop: 8,
    fontSize: 14,
    fontWeight: '600',
  },
  section: {
    alignSelf: 'stretch',
    marginTop: 28,
    paddingHorizontal: 16,
  },
  sectionTitle: {
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.3,
    marginBottom: 2,
  },
  worksRow: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
    gap: 12,
  },
  workCard: {
    width: 116,
  },
  workPoster: {
    width: 116,
    height: 164,
    borderRadius: 10,
  },
  workTitle: {
    fontSize: 12,
    fontWeight: '600',
    marginTop: 7,
    lineHeight: 16,
  },
  workYear: {
    fontSize: 11,
    marginTop: 2,
  },
  skeletonRow: {
    flexDirection: 'row',
    gap: 12,
    paddingHorizontal: 16,
    marginTop: 32,
    alignSelf: 'stretch',
  },
  skeletonCard: {
    width: 116,
    height: 164,
    borderRadius: 10,
    opacity: 0.5,
  },
});
