#!/usr/bin/env node
/**
 * download_portraits.js
 *
 * Downloads all hero portrait images from the MLBB CDN using URLs in
 * default_heroes.json, resizes them to fit within 224 px (preserving aspect
 * ratio), converts to WebP at quality 85, and writes the results to
 * app/src/main/assets/portraits/{heroId}.webp.
 *
 * Requirements: Node.js, ImageMagick 7 (`magick` binary).
 * Usage (from the repo.dev directory):
 *   node scripts/download_portraits.js
 */

'use strict';

const https    = require('https');
const http     = require('http');
const fs       = require('fs');
const path     = require('path');
const os       = require('os');
const { execFile } = require('child_process');

const REPO_ROOT   = path.resolve(__dirname, '..');
const JSON_PATH   = path.join(REPO_ROOT, 'app/src/main/res/raw/default_heroes.json');
const OUT_DIR     = path.join(REPO_ROOT, 'app/src/main/assets/portraits');
const CONCURRENCY = 8;   // parallel downloads
const MAX_DIM     = 224; // px — max dimension (ImageMagick preserves aspect ratio)
const WEBP_Q      = 85;  // WebP quality (0-100)

// User-Agent that the MLBB CDN accepts
const UA = 'Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36';

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

const heroes = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
console.log(`Found ${heroes.length} heroes. Output → ${OUT_DIR}\n`);

// ── helpers ──────────────────────────────────────────────────────────────────

function download(url, redirects = 0) {
    return new Promise((resolve, reject) => {
        if (redirects > 5) return reject(new Error('Too many redirects'));
        const client = url.startsWith('https') ? https : http;
        const req = client.get(url, { headers: { 'User-Agent': UA } }, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return download(res.headers.location, redirects + 1).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                res.resume();
                return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
            }
            const chunks = [];
            res.on('data', (c) => chunks.push(c));
            res.on('end',  ()  => resolve(Buffer.concat(chunks)));
            res.on('error', reject);
        });
        req.on('error', reject);
        req.setTimeout(20000, () => { req.destroy(); reject(new Error(`Timeout: ${url}`)); });
    });
}

/**
 * Convert raw image bytes → WebP via ImageMagick 7 (`magick`).
 * Writes input to a tmp file, runs magick, deletes tmp file.
 * -resize 224x224 fits within 224×224 preserving aspect ratio (no upscale for already-small images).
 */
function convertToWebP(inputBuf, outputPath) {
    return new Promise((resolve, reject) => {
        const tmpFile = path.join(os.tmpdir(), `portrait_${process.pid}_${Date.now()}.png`);
        try {
            fs.writeFileSync(tmpFile, inputBuf);
        } catch (e) {
            return reject(e);
        }
        // Use magick (IM v7). -resize 224x224 without > flag: ImageMagick will
        // scale to fit within the box, preserving aspect ratio. Images already
        // smaller than 224px are kept as-is (no upscale needed for CDN portraits).
        execFile('magick', [tmpFile, '-resize', `${MAX_DIM}x${MAX_DIM}`, '-quality', String(WEBP_Q), `webp:${outputPath}`],
            { timeout: 20000 },
            (err) => {
                try { fs.unlinkSync(tmpFile); } catch (_) {}
                if (err) reject(err); else resolve();
            }
        );
    });
}

// chunk array into groups of size n
function chunks(arr, n) {
    const result = [];
    for (let i = 0; i < arr.length; i += n) result.push(arr.slice(i, i + n));
    return result;
}

// ── main ─────────────────────────────────────────────────────────────────────

let done = 0, skipped = 0, failed = 0;

async function processHero(hero) {
    const outPath = path.join(OUT_DIR, `${hero.id}.webp`);
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 0) {
        process.stdout.write(`  [skip] ${hero.id} ${hero.name}\n`);
        skipped++;
        return;
    }
    if (!hero.imageUrl) {
        process.stdout.write(`  [WARN] ${hero.id} ${hero.name} — no imageUrl\n`);
        failed++;
        return;
    }
    try {
        const buf = await download(hero.imageUrl);
        await convertToWebP(buf, outPath);
        const stat = fs.statSync(outPath);
        if (stat.size === 0) throw new Error('Output file is empty');
        const kb = (stat.size / 1024).toFixed(1);
        process.stdout.write(`  [ok]   ${String(hero.id).padStart(3)} ${hero.name.padEnd(22)} ${kb} KB\n`);
        done++;
    } catch (e) {
        process.stdout.write(`  [FAIL] ${String(hero.id).padStart(3)} ${hero.name} — ${e.message}\n`);
        failed++;
    }
}

(async () => {
    const batches = chunks(heroes, CONCURRENCY);
    for (const batch of batches) {
        await Promise.all(batch.map(processHero));
    }
    console.log(`\nDone. Downloaded: ${done}  Skipped: ${skipped}  Failed: ${failed}`);
    const totalKB = fs.readdirSync(OUT_DIR)
        .filter(f => f.endsWith('.webp'))
        .reduce((sum, f) => sum + fs.statSync(path.join(OUT_DIR, f)).size, 0) / 1024;
    console.log(`Total assets size: ${(totalKB / 1024).toFixed(2)} MB`);
    if (failed > 0) process.exit(1);
})();
