import sys

path = r'src\screens\HomeScreen.tsx'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Normalise to LF for reliable matching
content = content.replace('\r\n', '\n')

# ── Fix 1: Add Trakt sync to toggleWatchlist ─────────────────────────────────
OLD1 = (
    "  const toggleWatchlist = useCallback(async (item: any) => {\n"
    "    if (!user) { navigation.navigate('Auth'); return; }\n"
    "    const key = `streamdek_watchlist_${user.uid}`;\n"
    "    const wl = await Storage.getItem(key);\n"
    "    const current: any[] = wl ? JSON.parse(wl) : [];\n"
    "    const itemId = String(item.id);\n"
    "    const exists = current.some((i: any) => String(i.id) === itemId);\n"
    "    const updated = exists\n"
    "      ? current.filter((i: any) => String(i.id) !== itemId)\n"
    "      : [...current, {\n"
    "          id: itemId, title: item.title, poster: item.poster,\n"
    "          type: item.type, year: item.year, rating: item.rating,\n"
    "        }];\n"
    "    await Storage.setItem(key, JSON.stringify(updated));\n"
    "    setWatchlist(updated);\n"
    "  }, [user, navigation]);"
)

NEW1 = (
    "  const toggleWatchlist = useCallback(async (item: any) => {\n"
    "    if (!user) { navigation.navigate('Auth'); return; }\n"
    "    const key = `streamdek_watchlist_${user.uid}`;\n"
    "    const wl = await Storage.getItem(key);\n"
    "    const current: any[] = wl ? JSON.parse(wl) : [];\n"
    "    const itemId = String(item.id);\n"
    "    const exists = current.some((i: any) => String(i.id) === itemId);\n"
    "    const updated = exists\n"
    "      ? current.filter((i: any) => String(i.id) !== itemId)\n"
    "      : [...current, {\n"
    "          id: itemId, title: item.title, poster: item.poster,\n"
    "          type: item.type, year: item.year, rating: item.rating,\n"
    "        }];\n"
    "    await Storage.setItem(key, JSON.stringify(updated));\n"
    "    setWatchlist(updated);\n"
    "\n"
    "    // Sync with Trakt if connected\n"
    "    if (traktConnected) {\n"
    "      const endpoint = exists ? '/trakt/sync/watchlist/remove' : '/trakt/sync/watchlist/add';\n"
    "      const entry = {\n"
    "        title: item.title,\n"
    "        year: parseInt(String(item.year)) || undefined,\n"
    "        ids: { tmdb: Number(itemId) },\n"
    "      };\n"
    "      const payload = item.type === 'movie'\n"
    "        ? { movies: [entry], shows: [] }\n"
    "        : { movies: [], shows: [entry] };\n"
    "      const tHdrs: Record<string, string> = { 'Content-Type': 'application/json', 'x-user-id': user.uid };\n"
    "      try {\n"
    "        await fetch(`${API_BASE}${endpoint}`, { method: 'POST', headers: tHdrs, body: JSON.stringify(payload) });\n"
    "        await refreshWatchlist();\n"
    "      } catch {}\n"
    "    }\n"
    "  }, [user, navigation, traktConnected, refreshWatchlist]);"
)

if OLD1 in content:
    content = content.replace(OLD1, NEW1, 1)
    print('Fix 1 (Trakt sync in toggleWatchlist): APPLIED')
else:
    print('Fix 1: NOT FOUND — check raw text', file=sys.stderr)

# ── Fix 2: Remove variant 'default' -> 'destructive' ─────────────────────────
OLD2 = (
    "      inWl\n"
    "        ? {\n"
    "            label:   t('card_watchlist_remove'),\n"
    "            icon:    'bookmark-outline' as const,\n"
    "            variant: 'default' as const,\n"
    "            onPress: () => toggleWatchlist(item),\n"
    "          }"
)

NEW2 = (
    "      inWl\n"
    "        ? {\n"
    "            label:   t('card_watchlist_remove'),\n"
    "            icon:    'bookmark-outline' as const,\n"
    "            variant: 'destructive' as const,\n"
    "            onPress: () => toggleWatchlist(item),\n"
    "          }"
)

if OLD2 in content:
    content = content.replace(OLD2, NEW2, 1)
    print("Fix 2 (destructive variant): APPLIED")
else:
    print("Fix 2: NOT FOUND — check raw text", file=sys.stderr)

# Restore CRLF and write back
with open(path, 'w', encoding='utf-8', newline='') as f:
    f.write(content.replace('\n', '\r\n'))

print('Done.')
