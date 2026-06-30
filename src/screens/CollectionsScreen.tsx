import React, { useMemo, useState } from 'react';
import { Alert, Modal, ScrollView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import DraggableFlatList, { RenderItemParams } from 'react-native-draggable-flatlist';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurTargetView } from 'expo-blur';
import { File } from 'expo-file-system';
import { StackBottomNav, BOTTOM_NAV_HEIGHT } from '../components/StackBottomNav';
import { ConfirmSheet } from '../components/ConfirmSheet';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { useCollections } from '../context/CollectionsContext';
import type { Collection } from '../utils/collections';

function makeStyles(c: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: c.bg },
    content: { flex: 1 },
    listContent: { paddingHorizontal: 20, paddingBottom: 120 },
    backRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 16 },
    backText: { color: c.textSecondary, fontSize: 14, fontWeight: '600' },
    title: { color: c.textPrimary, fontSize: 30, fontWeight: '900', letterSpacing: -0.6 },
    subtitle: { color: c.textSecondary, fontSize: 14, lineHeight: 20, marginTop: 8, marginBottom: 20 },
    card: { backgroundColor: c.cardBgElevated ?? c.cardBg, borderWidth: 1, borderColor: c.border, borderRadius: 22, overflow: 'hidden', marginBottom: 18 },
    summary: { color: c.textSecondary, fontSize: 14, lineHeight: 20 },
    actionStack: { gap: 10, marginBottom: 18 },
    actionBtn: { borderRadius: 16, paddingVertical: 14, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: c.border },
    actionBtnPrimary: { backgroundColor: c.accent, borderColor: c.accent },
    actionBtnGhost: { backgroundColor: c.cardBgElevated ?? c.cardBg },
    actionBtnText: { fontSize: 14, fontWeight: '800' },
    sectionTitle: { color: c.textPrimary, fontSize: 14, fontWeight: '800', letterSpacing: 0.4, marginBottom: 10 },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 16 },
    rowBody: { flex: 1 },
    rowTitle: { color: c.textPrimary, fontSize: 15, fontWeight: '700' },
    rowSub: { color: c.textSecondary, fontSize: 12, lineHeight: 18, marginTop: 3 },
    divider: { height: 1, backgroundColor: c.borderSoft, marginLeft: 54 },
    grip: { width: 28, alignItems: 'center' },
    modalBackdrop: { flex: 1, backgroundColor: 'rgba(2,6,23,0.72)', justifyContent: 'center', padding: 20 },
    modalCard: { backgroundColor: c.cardBgElevated ?? c.cardBg, borderRadius: 22, borderWidth: 1, borderColor: c.border, padding: 18 },
    modalTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800' },
    modalSub: { color: c.textSecondary, fontSize: 13, lineHeight: 19, marginTop: 8, marginBottom: 14 },
    input: { minHeight: 180, borderRadius: 16, borderWidth: 1, borderColor: c.border, backgroundColor: c.inputBg, color: c.textPrimary, padding: 14, textAlignVertical: 'top' },
    error: { color: '#ef4444', fontSize: 12, lineHeight: 18, marginTop: 10 },
    modalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 10, marginTop: 16 },
    modalBtn: { borderRadius: 14, paddingHorizontal: 16, paddingVertical: 12, borderWidth: 1, borderColor: c.border },
    emptyTitle: { color: c.textPrimary, fontSize: 18, fontWeight: '800', marginTop: 14, marginBottom: 8, textAlign: 'center' },
    emptySub: { color: c.textSecondary, fontSize: 13, lineHeight: 20, textAlign: 'center' },
  });
}

export function CollectionsScreen({ navigation }: any) {
  const blurTargetRef = React.useRef<View | null>(null);
  const insets = useSafeAreaInsets();
  const { theme: { colors }, resolvedAppearance } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { collections, exportToJson, importFromJson, moveCollection, removeCollection } = useCollections();
  const [showImportModal, setShowImportModal] = useState(false);
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importingFile, setImportingFile] = useState(false);
  const [pendingDeleteCollection, setPendingDeleteCollection] = useState<Collection | null>(null);

  const finishImport = (collectionCount: number, folderCount: number) => {
    setShowImportModal(false);
    setImportText('');
    setImportError(null);
    Alert.alert('Collections imported', `${collectionCount} collection${collectionCount === 1 ? '' : 's'} and ${folderCount} folder${folderCount === 1 ? '' : 's'} imported.`);
  };

  const handleCopy = () => {
    const json = exportToJson();
    Alert.alert('Collections JSON', 'Open the import sheet to copy or reuse your exported JSON.', [
      { text: 'Open JSON', onPress: () => { setImportText(json); setImportError(null); setShowImportModal(true); } },
      { text: 'Close', style: 'cancel' },
    ]);
  };

  const handleImport = async () => {
    const result = await importFromJson(importText);
    if (!result.valid) {
      setImportError(result.error ?? 'Unable to import collections.');
      return;
    }
    finishImport(result.collectionCount, result.folderCount);
  };

  const handleImportFile = async () => {
    if (importingFile) return;
    setImportingFile(true);
    try {
      const picked = await File.pickFileAsync(undefined, 'application/json');
      const selectedFile = Array.isArray(picked) ? picked[0] : picked;
      if (!selectedFile) return;
      const json = await selectedFile.text();
      const result = await importFromJson(json);
      if (!result.valid) {
        Alert.alert('Import failed', result.error ?? 'Unable to import collections from that file.');
        return;
      }
      finishImport(result.collectionCount, result.folderCount);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to pick that file.';
      if (/cancel/i.test(message)) return;
      Alert.alert('Import failed', message);
    } finally {
      setImportingFile(false);
    }
  };

  const renderHeader = () => (
    <View style={{ paddingTop: insets.top + 18 }}>
      <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.8} style={styles.backRow}>
        <Ionicons name="chevron-back" size={20} color={colors.textSecondary} />
        <Text style={styles.backText}>Back</Text>
      </TouchableOpacity>
      <Text style={styles.title}>Collections</Text>
      <Text style={styles.subtitle}>Import Nuvio-style collection JSON from a file or paste it directly, then expose the folders you want on the home layout.</Text>

      <View style={[styles.card, { padding: 18 }]}> 
        <Text style={styles.summary}>{collections.length} collection{collections.length === 1 ? '' : 's'} - {collections.reduce((sum, collection) => sum + collection.folders.length, 0)} folder{collections.reduce((sum, collection) => sum + collection.folders.length, 0) === 1 ? '' : 's'}</Text>
      </View>

      <View style={styles.actionStack}>
        <TouchableOpacity style={[styles.actionBtn, styles.actionBtnPrimary]} activeOpacity={0.85} onPress={() => { void handleImportFile(); }}>
          <Text style={[styles.actionBtnText, { color: colors.buttonText }]}>{importingFile ? 'Opening file picker...' : 'Import File'}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.actionBtn, styles.actionBtnGhost]} activeOpacity={0.85} onPress={() => { setImportText(''); setImportError(null); setShowImportModal(true); }}>
          <Text style={[styles.actionBtnText, { color: colors.textPrimary }]}>Paste JSON</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.actionBtn, styles.actionBtnGhost]} activeOpacity={0.85} onPress={handleCopy}>
          <Text style={[styles.actionBtnText, { color: colors.textPrimary }]}>View Export</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>Your Collections</Text>
    </View>
  );

  const renderCollectionRow = ({ item, drag, getIndex, isActive }: RenderItemParams<Collection>) => {
    const index = getIndex?.() ?? 0;
    const isLast = index === collections.length - 1;
    return (
      <View style={[styles.card, index > 0 && { marginTop: 0 }, isLast && { marginBottom: 0 }]}>
        <View style={styles.row}>
          <TouchableOpacity style={styles.grip} onLongPress={drag} delayLongPress={220}>
            <Ionicons name="reorder-three-outline" size={20} color={colors.placeholder} />
          </TouchableOpacity>
          <View style={styles.rowBody}>
            <Text style={styles.rowTitle}>{item.title}</Text>
            <Text style={styles.rowSub}>{item.folders.length} folder{item.folders.length === 1 ? '' : 's'}{item.pinToTop ? ' - pinned' : ''}</Text>
          </View>
          <TouchableOpacity
            onPress={() => navigation.navigate('CollectionFolder', { collectionId: item.id, folderId: item.folders[0]?.id })}
            disabled={!item.folders[0]?.id}
            style={{ padding: 6 }}
          >
            <Ionicons name="open-outline" size={18} color={colors.accentSoft} />
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setPendingDeleteCollection(item)} style={{ padding: 6 }}>
            <Ionicons name="trash-outline" size={18} color="#ef4444" />
          </TouchableOpacity>
        </View>
        {!isActive && !isLast ? <View style={styles.divider} /> : null}
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <BlurTargetView ref={blurTargetRef} style={{ flex: 1 }}>
        <GestureHandlerRootView style={styles.container}>
          <StatusBar barStyle={resolvedAppearance === 'light' ? 'dark-content' : 'light-content'} translucent backgroundColor="transparent" />
          <View style={styles.content}>
            {collections.length === 0 ? (
              <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={[styles.listContent, { paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}>
                {renderHeader()}
                <View style={[styles.card, { padding: 28, alignItems: 'center' }]}>
                  <Ionicons name="folder-open-outline" size={34} color={colors.placeholder} />
                  <Text style={styles.emptyTitle}>No collections imported</Text>
                  <Text style={styles.emptySub}>Choose a collections JSON file from your phone or paste a Nuvio export to add custom folders, then enable the folders you want from Home Layout settings.</Text>
                </View>
              </ScrollView>
            ) : (
              <DraggableFlatList
                data={collections}
                keyExtractor={item => item.id}
                renderItem={renderCollectionRow}
                onDragEnd={({ from, to }) => { void moveCollection(from, to); }}
                showsVerticalScrollIndicator={false}
                activationDistance={16}
                contentContainerStyle={[styles.listContent, { paddingBottom: BOTTOM_NAV_HEIGHT + insets.bottom + 24 }]}
                ListHeaderComponent={renderHeader}
              />
            )}
          </View>
        </GestureHandlerRootView>
      </BlurTargetView>
      <StackBottomNav activeTab="Settings" blurTarget={blurTargetRef} />

      <Modal visible={showImportModal} transparent animationType="fade" onRequestClose={() => setShowImportModal(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Collections JSON</Text>
            <Text style={styles.modalSub}>Paste the Nuvio collections export here. StreamDek now supports addon-backed, TMDB-backed, and Trakt-backed collection sources, and you can also import the same JSON from a file using the main Import File action.</Text>
            <TextInput
              value={importText}
              onChangeText={value => { setImportText(value); setImportError(null); }}
              multiline
              autoCapitalize="none"
              autoCorrect={false}
              style={styles.input}
              placeholder="[{ ... }]"
              placeholderTextColor={colors.placeholder}
            />
            {importError ? <Text style={styles.error}>{importError}</Text> : null}
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalBtn} onPress={() => setShowImportModal(false)}>
                <Text style={{ color: colors.textPrimary, fontWeight: '700' }}>Close</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalBtn, { backgroundColor: colors.accent, borderColor: colors.accent }]} onPress={() => { void handleImport(); }}>
                <Text style={{ color: colors.buttonText, fontWeight: '800' }}>Import</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <ConfirmSheet
        visible={!!pendingDeleteCollection}
        onClose={() => setPendingDeleteCollection(null)}
        title="Delete collection"
        message={pendingDeleteCollection ? `Remove \"${pendingDeleteCollection.title}\"?` : undefined}
        icon="trash-outline"
        iconColor="#ef4444"
        confirmLabel="Delete"
        cancelLabel="Cancel"
        variant="destructive"
        onConfirm={() => {
          if (!pendingDeleteCollection) return;
          void removeCollection(pendingDeleteCollection.id);
        }}
      />
    </View>
  );
}