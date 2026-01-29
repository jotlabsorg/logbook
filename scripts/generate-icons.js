#!/usr/bin/env node
/**
 * Generates PNG icons from SVG sources using sharp.
 * Run: npm run icons
 */

const fs = require('fs');
const path = require('path');

// Check if sharp is available, otherwise provide instructions
let sharp;
try {
  sharp = require('sharp');
} catch (e) {
  console.log('Sharp not installed. Installing...');
  const { execSync } = require('child_process');
  execSync('npm install sharp --save-dev', { stdio: 'inherit' });
  sharp = require('sharp');
}

const sizes = [16, 32, 48, 128];
const iconsDir = path.join(__dirname, '..', 'resources', 'icons');

async function generateIcons() {
  for (const size of sizes) {
    const svgPath = path.join(iconsDir, `icon${size}.svg`);
    const pngPath = path.join(iconsDir, `icon${size}.png`);
    
    if (fs.existsSync(svgPath)) {
      await sharp(svgPath)
        .resize(size, size)
        .png()
        .toFile(pngPath);
      console.log(`Generated ${pngPath}`);
    } else {
      console.warn(`SVG not found: ${svgPath}`);
    }
  }
  console.log('Done generating icons!');
}

generateIcons().catch(console.error);
