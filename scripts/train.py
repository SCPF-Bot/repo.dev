#!/usr/bin/env python3
"""
MLBB Hero Portrait TFLite Training Pipeline (Enhanced)
=======================================================
Downloads CDN portraits, generates synthetic ban/pick slot variants,
applies realistic screen-capture artifacts, and trains MobileNetV3Small.

Key enhancement: Synthetic data generation to simulate:
- Ban slot: red prohibition overlay + darkening
- Pick slot: country flag + rank badge + spell icon overlays
- Screen capture: JPEG compression, brightness shifts, slight blur, noise
"""

import os
import io
import json
import random
import requests
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
from pathlib import Path
from tqdm import tqdm
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, applications
from tensorflow.keras.optimizers import Adam
from sklearn.model_selection import train_test_split

# Configuration
CONFIG = {
    'json_path': 'app/src/main/res/raw/default_heroes.json',
    'output_dir': 'app/src/main/assets',
    'portraits_dir': 'app/src/main/assets/portraits',
    
    # Image dimensions
    'size_main': 224,  # MobileNetV3Small input
    'size_pick': 128,  # TD-18
    'size_ban': 64,    # TD-18
    
    # Training hyperparameters
    'epochs': 60,
    'batch_size': 16,
    'learning_rate': 0.0005,
    
    # Synthetic data generation
    'augmentation_factor': 20,  # Total augmented samples per hero
    'ban_variant_ratio': 0.3,   # 30% of training data simulates ban slots
    'pick_variant_ratio': 0.3,  # 30% simulates pick slots
    'clean_ratio': 0.4,         # 40% clean CDN portraits
    
    # Screen capture simulation
    'jpeg_quality_range': (70, 95),
    'brightness_shift_range': (-0.15, 0.15),
    'contrast_shift_range': (-0.1, 0.1),
    'blur_probability': 0.3,
    'noise_probability': 0.2,
    
    # TFLite outputs
    'tflite_model_path': 'mlbb_hero_classifier.tflite',
    'labels_file_path': 'hero_classifier_labels.txt',
}

def load_heroes():
    """Load and clean hero data from default_heroes.json"""
    print(f"[1/6] Loading hero data from {CONFIG['json_path']}...")
    
    with open(CONFIG['json_path'], 'r', encoding='utf-8') as f:
        raw_data = json.load(f)
    
    cleaned_data = []
    for item in raw_data:
        cleaned_item = {
            k.strip(): v.strip() if isinstance(v, str) else v 
            for k, v in item.items()
        }
        cleaned_data.append(cleaned_item)
    
    print(f"✓ Loaded {len(cleaned_data)} heroes")
    return cleaned_data

def download_portraits(heroes):
    """Download official CDN portraits"""
    print(f"\n[2/6] Downloading portraits from CDN...")
    
    portraits_dir = Path(CONFIG['portraits_dir'])
    main_dir = portraits_dir / 'main'
    pick_dir = portraits_dir / 'pick'
    ban_dir = portraits_dir / 'ban'
    
    for d in [main_dir, pick_dir, ban_dir]:
        d.mkdir(parents=True, exist_ok=True)
    
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    valid_heroes = []
    
    for hero in tqdm(heroes, desc="Downloading"):
        hero_id = hero.get('id')
        hero_name = hero.get('name')
        image_url = hero.get('imageUrl')
        
        if not hero_id or not image_url:
            continue
        
        try:
            main_path = main_dir / f"{hero_id}.png"
            if not main_path.exists():
                response = requests.get(image_url, headers=headers, timeout=10)
                response.raise_for_status()
                img_bytes = io.BytesIO(response.content)
                img = Image.open(img_bytes).convert('RGB')
            else:
                img = Image.open(main_path).convert('RGB')
            
            # Generate variants
            main_img = img.resize((CONFIG['size_main'], CONFIG['size_main']), Image.LANCZOS)
            main_img.save(main_path)
            
            pick_img = main_img.resize((CONFIG['size_pick'], CONFIG['size_pick']), Image.LANCZOS)
            pick_img.save(pick_dir / f"{hero_id}.png")
            
            ban_img = main_img.resize((CONFIG['size_ban'], CONFIG['size_ban']), Image.LANCZOS)
            ban_img.save(ban_dir / f"{hero_id}.png")
            
            valid_heroes.append({'id': hero_id, 'name': hero_name})
            
        except Exception as e:
            print(f"\n✗ Failed to process {hero_name} (ID {hero_id}): {e}")
            continue
    
    print(f"✓ Prepared {len(valid_heroes)} heroes with variants")
    return valid_heroes

def create_ban_overlay(img):
    """Create a synthetic ban slot variant with red prohibition overlay"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    
    # Darken the image (ban slots appear darker)
    enhancer = ImageEnhance.Brightness(img)
    img = enhancer.enhance(0.7)
    
    # Add red prohibition circle overlay (bottom-right)
    size = img.size[0]
    overlay_size = int(size * 0.35)
    overlay_x = size - overlay_size - int(size * 0.05)
    overlay_y = size - overlay_size - int(size * 0.05)
    
    # Draw red circle
    draw.ellipse(
        [overlay_x, overlay_y, overlay_x + overlay_size, overlay_y + overlay_size],
        fill=(220, 50, 50, 200),
        outline=(180, 30, 30, 255),
        width=3
    )
    
    # Draw diagonal line (prohibition symbol)
    draw.line(
        [overlay_x + int(overlay_size * 0.2), overlay_y + int(overlay_size * 0.8),
         overlay_x + int(overlay_size * 0.8), overlay_y + int(overlay_size * 0.2)],
        fill=(255, 255, 255, 255),
        width=4
    )
    
    return img

def create_pick_slot_overlay(img):
    """Create a synthetic pick slot variant with UI chrome overlays"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    
    size = img.size[0]
    
    # Add country flag (top-left) - simplified as colored rectangle
    flag_size = int(size * 0.25)
    flag_x = int(size * 0.05)
    flag_y = int(size * 0.05)
    
    # Random flag colors (simulating different countries)
    flag_colors = [
        [(255, 0, 0), (255, 255, 255), (0, 0, 255)],  # Red/White/Blue
        [(255, 215, 0), (255, 0, 0)],  # Gold/Red
        [(0, 128, 0), (255, 255, 255), (255, 0, 0)],  # Green/White/Red
    ]
    colors = random.choice(flag_colors)
    
    for i, color in enumerate(colors):
        stripe_height = flag_size // len(colors)
        draw.rectangle(
            [flag_x, flag_y + i * stripe_height, flag_x + flag_size, flag_y + (i + 1) * stripe_height],
            fill=color
        )
    
    # Add rank badge (top-right) - circular badge
    badge_size = int(size * 0.2)
    badge_x = size - badge_size - int(size * 0.05)
    badge_y = int(size * 0.05)
    
    # Gold badge
    draw.ellipse(
        [badge_x, badge_y, badge_x + badge_size, badge_y + badge_size],
        fill=(255, 215, 0),
        outline=(200, 170, 0),
        width=2
    )
    
    # Add spell icon (top-right, below badge)
    spell_size = int(size * 0.18)
    spell_x = size - spell_size - int(size * 0.05)
    spell_y = badge_y + badge_size + int(size * 0.05)
    
    # Orange spell icon
    draw.ellipse(
        [spell_x, spell_y, spell_x + spell_size, spell_y + spell_size],
        fill=(255, 140, 0),
        outline=(200, 110, 0),
        width=2
    )
    
    return img

def apply_screen_capture_artifacts(img):
    """Simulate screen capture degradation"""
    img = img.copy()
    
    # JPEG compression artifacts
    quality = random.randint(*CONFIG['jpeg_quality_range'])
    buffer = io.BytesIO()
    img.save(buffer, format='JPEG', quality=quality)
    buffer.seek(0)
    img = Image.open(buffer).convert('RGB')
    
    # Brightness shift
    brightness_shift = random.uniform(*CONFIG['brightness_shift_range'])
    enhancer = ImageEnhance.Brightness(img)
    img = enhancer.enhance(1.0 + brightness_shift)
    
    # Contrast shift
    contrast_shift = random.uniform(*CONFIG['contrast_shift_range'])
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(1.0 + contrast_shift)
    
    # Occasional blur
    if random.random() < CONFIG['blur_probability']:
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.5, 1.5)))
    
    # Occasional noise
    if random.random() < CONFIG['noise_probability']:
        img_array = np.array(img)
        noise = np.random.normal(0, 10, img_array.shape).astype(np.uint8)
        img_array = np.clip(img_array.astype(np.int16) + noise, 0, 255).astype(np.uint8)
        img = Image.fromarray(img_array)
    
    return img

def prepare_dataset(valid_heroes):
    """Load images and generate synthetic variants"""
    print(f"\n[3/6] Preparing dataset with synthetic variants...")
    
    valid_heroes.sort(key=lambda x: x['id'])
    labels = [h['name'] for h in valid_heroes]
    
    main_dir = Path(CONFIG['portraits_dir']) / 'main'
    images = []
    targets = []
    
    print("Generating synthetic training data...")
    for idx, hero in enumerate(tqdm(valid_heroes)):
        img_path = main_dir / f"{hero['id']}.png"
        img = Image.open(img_path).convert('RGB')
        img = img.resize((CONFIG['size_main'], CONFIG['size_main']), Image.LANCZOS)
        
        # Determine variant distribution
        num_samples = CONFIG['augmentation_factor']
        num_clean = int(num_samples * CONFIG['clean_ratio'])
        num_ban = int(num_samples * CONFIG['ban_variant_ratio'])
        num_pick = num_samples - num_clean - num_ban
        
        # Clean variants with screen capture artifacts
        for _ in range(num_clean):
            augmented = apply_screen_capture_artifacts(img)
            images.append(np.array(augmented))
            targets.append(idx)
        
        # Ban slot variants
        for _ in range(num_ban):
            ban_variant = create_ban_overlay(img)
            augmented = apply_screen_capture_artifacts(ban_variant)
            images.append(np.array(augmented))
            targets.append(idx)
        
        # Pick slot variants
        for _ in range(num_pick):
            pick_variant = create_pick_slot_overlay(img)
            augmented = apply_screen_capture_artifacts(pick_variant)
            images.append(np.array(augmented))
            targets.append(idx)
    
    X = np.array(images, dtype=np.float32)
    y = np.array(targets, dtype=np.int32)
    
    # Normalize to [-1, 1]
    X = (X / 127.5) - 1.0
    
    # Shuffle and split
    p = np.random.permutation(len(X))
    X, y = X[p], y[p]
    
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.1, random_state=42
    )
    
    print(f"✓ Dataset ready: {len(X_train)} train, {len(X_val)} val samples")
    print(f"  Distribution: {CONFIG['clean_ratio']*100:.0f}% clean, {CONFIG['ban_variant_ratio']*100:.0f}% ban, {CONFIG['pick_variant_ratio']*100:.0f}% pick")
    print(f"  Sample shape: {X_train[0].shape}")
    
    return (X_train, y_train), (X_val, y_val), labels

def build_model(num_classes):
    """Build MobileNetV3Small model"""
    input_shape = (CONFIG['size_main'], CONFIG['size_main'], 3)
    
    base_model = applications.MobileNetV3Small(
        input_shape=input_shape,
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False
    
    model = keras.Sequential([
        base_model,
        layers.GlobalAveragePooling2D(),
        layers.BatchNormalization(),
        layers.Dense(256, activation='relu'),
        layers.Dropout(0.5),
        layers.Dense(num_classes, activation='softmax')
    ])
    
    return model

def train_model(train_data, val_data, labels):
    """Train the model in two phases"""
    print(f"\n[4/6] Training MobileNetV3Small...")
    
    X_train, y_train = train_data
    X_val, y_val = val_data
    num_classes = len(labels)
    
    model = build_model(num_classes)
    
    # Phase 1: Train top layers
    model.compile(
        optimizer=Adam(learning_rate=CONFIG['learning_rate']),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    print("\nPhase 1: Training top layers...")
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=CONFIG['epochs'] // 2,
        batch_size=CONFIG['batch_size'],
        verbose=1
    )
    
    # Phase 2: Fine-tune last 10 layers
    base_model = model.layers[0]
    base_model.trainable = True
    for layer in base_model.layers[:-10]:
        layer.trainable = False
    
    model.compile(
        optimizer=Adam(learning_rate=CONFIG['learning_rate'] / 10),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    print("\nPhase 2: Fine-tuning base model...")
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=CONFIG['epochs'] // 2,
        batch_size=CONFIG['batch_size'],
        verbose=1
    )
    
    return model

def convert_to_tflite(model, labels):
    """Convert to float16 TFLite"""
    print(f"\n[5/6] Converting to TFLite (float16)...")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    tflite_path = Path(CONFIG['output_dir']) / CONFIG['tflite_model_path']
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    print(f"✓ Model saved to {tflite_path} ({len(tflite_model) / 1024 / 1024:.2f} MB)")
    
    labels_path = Path(CONFIG['output_dir']) / CONFIG['labels_file_path']
    with open(labels_path, 'w', encoding='utf-8') as f:
        f.write("# MLBB Hero Classifier Labels\n")
        f.write(f"# Total classes: {len(labels)}\n")
        f.write("# Line index n maps to output neuron n (sorted by hero ID)\n")
        for label in labels:
            f.write(f"{label}\n")
    print(f"✓ Labels saved to {labels_path}")
    
    return tflite_model

def generate_sample_previews(valid_heroes):
    """Generate preview images showing the synthetic variants"""
    print(f"\n[6/6] Generating sample previews...")
    
    previews_dir = Path(CONFIG['portraits_dir']) / 'previews'
    previews_dir.mkdir(parents=True, exist_ok=True)
    
    # Pick first 3 heroes for preview
    preview_heroes = valid_heroes[:3]
    
    main_dir = Path(CONFIG['portraits_dir']) / 'main'
    
    for hero in preview_heroes:
        img_path = main_dir / f"{hero['id']}.png"
        img = Image.open(img_path).convert('RGB')
        img = img.resize((CONFIG['size_main'], CONFIG['size_main']), Image.LANCZOS)
        
        # Create variants
        clean = apply_screen_capture_artifacts(img)
        ban = create_ban_overlay(img)
        ban_augmented = apply_screen_capture_artifacts(ban)
        pick = create_pick_slot_overlay(img)
        pick_augmented = apply_screen_capture_artifacts(pick)
        
        # Save previews
        hero_name = hero['name'].replace(' ', '_')
        clean.save(previews_dir / f"{hero_name}_clean.png")
        ban_augmented.save(previews_dir / f"{hero_name}_ban.png")
        pick_augmented.save(previews_dir / f"{hero_name}_pick.png")
    
    print(f"✓ Previews saved to {previews_dir}")

def main():
    print("=" * 60)
    print("MLBB Hero Portrait TFLite Training Pipeline (Enhanced)")
    print("=" * 60)
    
    # Step 1: Load heroes
    heroes = load_heroes()
    if not heroes:
        print("ERROR: No valid heroes loaded")
        return 1
    
    # Step 2: Download portraits
    valid_heroes = download_portraits(heroes)
    if not valid_heroes:
        print("ERROR: No valid heroes processed")
        return 1
    
    # Step 3: Prepare dataset with synthetic variants
    train_data, val_data, labels = prepare_dataset(valid_heroes)
    
    # Step 4: Train model
    model = train_model(train_data, val_data, labels)
    
    # Step 5: Convert to TFLite
    convert_to_tflite(model, labels)
    
    # Step 6: Generate previews
    generate_sample_previews(valid_heroes)
    
    print("\n" + "=" * 60)
    print("✅ PIPELINE COMPLETE")
    print("=" * 60)
    print("\nKey improvements:")
    print("• 30% ban slot variants (red prohibition overlay + darkening)")
    print("• 30% pick slot variants (country flag + rank badge + spell icon)")
    print("• Realistic screen capture artifacts (JPEG, brightness, blur, noise)")
    print("• Model trained to recognize heroes DESPITE overlays")
    print("\nNext steps:")
    print("1. Check previews in app/src/main/assets/portraits/previews/")
    print("2. Ensure `androidResources { noCompress += listOf(\"tflite\") }` in build.gradle.kts")
    print("3. Rebuild Android project")
    
    return 0

if __name__ == '__main__':
    exit(main())
