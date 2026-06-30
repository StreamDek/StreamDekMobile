const fs = require('fs');
const path = 'src/screens/MediaDetailScreen.tsx';
let text = fs.readFileSync(path, 'utf8');
const before = text;
text = text.replace(/detailContentBody:\s*\{\s*flex: 1,\s*backgroundColor: 'transparent',\s*zIndex: 1,\s*\},/, `detailContentBody: {\n    flex: 1,\n    backgroundColor: detailContentTint,\n    zIndex: 1,\n  },`);
text = text.replace(/\s*\{!isLightAppearance && \(\s*<LinearGradient\s*colors=\{\['transparent', colors\.bg\]\}\s*locations=\{\[0, 1\]\}\s*style=\{styles\.backdropGradient\}\s*pointerEvents="none"\s*\/\>\s*\)\}/m, '');
text = text.replace(/\s*<LinearGradient\s*colors=\{isLightAppearance\s*\? \['rgba\(255,255,255,0\.00\)', 'rgba\(255,255,255,0\.14\)', detailContentTint\]\s*: \['rgba\(7,8,12,0\.00\)', 'rgba\(7,8,12,0\.40\)', detailContentTint\]\}\s*locations=\{\[0, 0\.56, 1\]\}\s*style=\{\{ position: 'absolute', left: 0, right: 0, bottom: 0, height: heroLayerTintHeight \}\}\s*pointerEvents="none"\s*\/\>\s*\{!isLightAppearance && \(\s*<>[\s\S]*?<\/>
\s*\)\}/m, '');
text = text.replace(/detailContentTopTint: \{ position: 'absolute', top: 0, left: 0, right: 0, height: 132, zIndex: 0 \},/, `detailContentTopTint: { position: 'absolute', top: 0, left: 0, right: 0, height: 164, zIndex: 0 },`);
text = text.replace(/colors=\{isLightAppearance\s*\? \[detailContentTint, 'rgba\(255,255,255,0\.14\)', 'rgba\(255,255,255,0\.00\)'\]\s*: \[detailContentTint, 'rgba\(7,8,12,0\.40\)', 'rgba\(7,8,12,0\.00\)'\]\}\s*locations=\{\[0, 0\.44, 1\]\}/m, `colors={isLightAppearance\n              ? ['rgba(255,255,255,0.82)', detailContentTint, detailContentTint]\n              : ['rgba(7,8,12,0.88)', detailContentTint, detailContentTint]}\n            locations={[0, 0.34, 1]}`);
if (text === before) throw new Error('No changes applied');
fs.writeFileSync(path, text);
