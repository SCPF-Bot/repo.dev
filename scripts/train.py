#!/usr/bin/env python3
"""
MLBB Unified Training Pipeline (Focused)
========================================
1. Downloads CDN hero portraits.
2. Generates YOLO dataset focused ONLY on detecting heroes in:
   - Banned slots (Top)
   - Ally pick slots (Left)
   - Enemy pick slots (Right)
3. Trains YOLOv8-Nano and exports to TFLite.
4. Generates MobileNet dataset and trains Hero Classifier.
"""

# ==============================================================================
# CRITICAL CI/CD FIXES: Prevent thread oversubscription and CUDA segfaults
# Must be set BEFORE importing torch, tensorflow, or ultralytics
# ==============================================================================
import os
os.environ["CUDA_VISIBLE_DEVICES"] = "-1"       # Hide GPUs to prevent CUDA stub segfaults
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
os.environ["VECLIB_MAXIMUM_THREADS"] = "1"
os.environ["NUMEXPR_NUM_THREADS"] = "1"

import io
import json
import random
import yaml
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
from ultralytics import YOLO

CONFIG = {
    'json_path': 'app/src/main/res/raw/default_heroes.json',
    'output_dir': 'app/src/main/assets',
    'portraits_dir': 'app/src/main/assets/portraits',
    'yolo_temp_dir': 'scripts/temp',
    
    'size_main': 224,
    'epochs_mobilenet': 30,
    'batch_size_mobilenet': 8,
    'lr_mobilenet': 0.0005,
    'augmentation_factor': 20,
    
    'num_yolo_images': 2000,
    'epochs_yolo': 50,
    'imgsz_yolo': 416,
    
    'mobilenet_tflite': 'mlbb_hero_classifier.tflite',
    'mobilenet_labels': 'hero_classifier_labels.txt',
    'yolo_tflite': 'mlbb_ui_detector.tflite',
}

def load_heroes():
    print(f"\n[1/6] Loading hero data...")
    with open(CONFIG['json_path'], 'r', encoding='utf-8') as f:
        raw_data = json.load(f)
    return [{k.strip(): v.strip() if isinstance(v, str) else v for k, v in item.items()} for item in raw_data]

def download_portraits(heroes):
    print(f"\n[2/6] Downloading portraits from CDN...")
    portraits_dir = Path(CONFIG['portraits_dir'])
    main_dir = portraits_dir / 'main'
    main_dir.mkdir(parents=True, exist_ok=True)
    
    headers = {'User-Agent': 'Mozilla/5.0'}
    valid_heroes = []
    
    for hero in tqdm(heroes, desc="Downloading"):
        hero_id, hero_name, image_url = hero.get('id'), hero.get('name'), hero.get('imageUrl')
        if not hero_id or not image_url: continue
        
        try:
            main_path = main_dir / f"{hero_id}.png"
            if not main_path.exists():
                response = requests.get(image_url, headers=headers, timeout=10)
                response.raise_for_status()
                img = Image.open(io.BytesIO(response.content)).convert('RGB')
                img.save(main_path)
            valid_heroes.append({'id': hero_id, 'name': hero_name})
        except Exception as e:
            print(f"\n✗ Failed {hero_name}: {e}")
            
    print(f"✓ Prepared {len(valid_heroes)} heroes")
    return valid_heroes

def apply_screen_capture_artifacts(img):
    img = img.copy()
    quality = random.randint(70, 95)
    buffer = io.BytesIO()
    img.save(buffer, format='JPEG', quality=quality)
    buffer.seek(0)
    img = Image.open(buffer).convert('RGB')
    
    img = ImageEnhance.Brightness(img).enhance(1.0 + random.uniform(-0.15, 0.15))
    img = ImageEnhance.Contrast(img).enhance(1.0 + random.uniform(-0.1, 0.1))
    
    if random.random() < 0.3:
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.5, 1.5)))
    if random.random() < 0.2:
        arr = np.array(img)
        noise = np.random.normal(0, 10, arr.shape).astype(np.uint8)
        img = Image.fromarray(np.clip(arr.astype(np.int16) + noise, 0, 255).astype(np.uint8))
    return img

def generate_and_train_yolo(valid_heroes):
    print(f"\n[3/6] Generating Focused YOLO Dataset & Training...")
    
    yolo_dir = Path(CONFIG['yolo_temp_dir'])
    img_dir, lbl_dir = yolo_dir / 'images', yolo_dir / 'labels'
    img_dir.mkdir(parents=True, exist_ok=True)
    lbl_dir.mkdir(parents=True, exist_ok=True)
    
    # ONLY the 3 classes we care about
    CLASSES = ['banned_hero', 'ally_hero', 'enemy_hero']
    main_dir = Path(CONFIG['portraits_dir']) / 'main'
    
    # Load a subset of hero portraits to paste
    hero_imgs = [Image.open(main_dir / f"{h['id']}.png").convert('RGB') for h in valid_heroes[:20]]
    
    # Coordinates for 1280x720 canvas (cx, cy, w, h)
    BAN_SLOTS = [(540, 50, 45, 45), (595, 50, 45, 45), (640, 50, 45, 45), (685, 50, 45, 45), (740, 50, 45, 45)]
    ALLY_SLOTS = [(80, 150, 90, 90), (80, 270, 90, 90), (80, 390, 90, 90), (80, 510, 90, 90), (80, 630, 90, 90)]
    ENEMY_SLOTS = [(1200, 150, 90, 90), (1200, 270, 90, 90), (1200, 390, 90, 90), (1200, 510, 90, 90), (1200, 630, 90, 90)]

    for i in tqdm(range(CONFIG['num_yolo_images']), desc="YOLO Data"):
        # Dark draft background
        bg = (random.randint(10, 30), random.randint(10, 30), random.randint(20, 40))
        img = Image.new('RGB', (1280, 720), color=bg)
        labels = []
        
        # Helper to process a list of slots for a specific class
        def process_slots(slots, class_name):
            for cx, cy, w, h in slots:
                # Randomly decide if this slot is active (has a hero) in this frame
                if random.random() > 0.4: 
                    jx = random.randint(-8, 8)
                    jy = random.randint(-8, 8)
                    final_cx, final_cy = cx + jx, cy + jy
                    
                    if hero_imgs:
                        hero_img = random.choice(hero_imgs).resize((w, h), Image.LANCZOS)
                        hero_img = apply_screen_capture_artifacts(hero_img)
                        img.paste(hero_img, (final_cx - w//2, final_cy - h//2))
                    
                    class_id = CLASSES.index(class_name)
                    labels.append(f"{class_id} {final_cx/1280:.4f} {final_cy/720:.4f} {w/1280:.4f} {h/720:.4f}")

        process_slots(BAN_SLOTS, 'banned_hero')
        process_slots(ALLY_SLOTS, 'ally_hero')
        process_slots(ENEMY_SLOTS, 'enemy_hero')
                
        img.save(img_dir / f"syn_{i:04d}.jpg")
        with open(lbl_dir / f"syn_{i:04d}.txt", 'w') as f:
            f.write('\n'.join(labels))

    dataset_yaml = yolo_dir / 'dataset.yaml'
    with open(dataset_yaml, 'w') as f:
        yaml.dump({'path': str(yolo_dir.absolute()), 'train': 'images', 'val': 'images', 'nc': len(CLASSES), 'names': CLASSES}, f)

    print("Training YOLOv8-Nano (Focused)...")
    yolo_model = YOLO('yolov8n.pt')
    
    yolo_model.train(
        data=str(dataset_yaml),
        epochs=CONFIG['epochs_yolo'],
        imgsz=CONFIG['imgsz_yolo'],
        batch=4,
        workers=0,
        device='cpu',
        project=str(yolo_dir),
        name='yolo_train',
        exist_ok=True,
        verbose=False
    )
    
    # FIX: Get the exact path to the best weights directly from the trainer object.
    # This completely bypasses Ultralytics' internal 'runs_dir' path resolution quirks.
    best_pt = yolo_model.trainer.best
    print(f"✓ Training complete. Best model located at: {best_pt}")
    
    # Load the best model and export to TFLite
    yolo_model = YOLO(str(best_pt))
    yolo_model.export(format='tflite', int8=True)
    
    # The .tflite file is generated in the exact same directory as the .pt file
    generated_tflite = Path(str(best_pt)).with_suffix('.tflite')
    final_yolo_path = Path(CONFIG['output_dir']) / CONFIG['yolo_tflite']
    
    # Ensure the final output directory exists before moving the file
    final_yolo_path.parent.mkdir(parents=True, exist_ok=True)
    
    generated_tflite.rename(final_yolo_path)
    print(f"✓ YOLO TFLite successfully moved to {final_yolo_path}")

def create_ban_overlay(img):
    img = img.copy()
    draw = ImageDraw.Draw(img)
    img = ImageEnhance.Brightness(img).enhance(0.7)
    size = img.size[0]
    o_size = int(size * 0.35)
    o_x, o_y = size - o_size - int(size * 0.05), size - o_size - int(size * 0.05)
    draw.ellipse([o_x, o_y, o_x + o_size, o_y + o_size], fill=(220, 50, 50, 200), outline=(180, 30, 30, 255), width=3)
    draw.line([o_x + int(o_size*0.2), o_y + int(o_size*0.8), o_x + int(o_size*0.8), o_y + int(o_size*0.2)], fill=(255,255,255,255), width=4)
    return img

def create_pick_overlay(img):
    img = img.copy()
    draw = ImageDraw.Draw(img)
    size = img.size[0]
    f_size, f_x, f_y = int(size * 0.25), int(size * 0.05), int(size * 0.05)
    colors = random.choice([[(255,0,0),(255,255,255),(0,0,255)], [(255,215,0),(255,0,0)]])
    for i, c in enumerate(colors):
        sh = f_size // len(colors)
        draw.rectangle([f_x, f_y + i*sh, f_x + f_size, f_y + (i+1)*sh], fill=c)
    b_size, b_x, b_y = int(size * 0.2), size - int(size * 0.2) - int(size * 0.05), int(size * 0.05)
    draw.ellipse([b_x, b_y, b_x + b_size, b_y + b_size], fill=(255, 215, 0), outline=(200, 170, 0), width=2)
    return img

def train_mobilenet(valid_heroes):
    print(f"\n[4/6] Preparing MobileNet Dataset...")
    valid_heroes.sort(key=lambda x: x['id'])
    labels = [h['name'] for h in valid_heroes]
    main_dir = Path(CONFIG['portraits_dir']) / 'main'
    
    images, targets = [], []
    for idx, hero in enumerate(tqdm(valid_heroes, desc="MobileNet Data")):
        img = Image.open(main_dir / f"{hero['id']}.png").convert('RGB').resize((CONFIG['size_main'], CONFIG['size_main']), Image.LANCZOS)
        num_samples = CONFIG['augmentation_factor']
        
        for _ in range(int(num_samples * 0.4)):
            images.append(np.array(apply_screen_capture_artifacts(img))); targets.append(idx)
        for _ in range(int(num_samples * 0.3)):
            images.append(np.array(apply_screen_capture_artifacts(create_ban_overlay(img)))); targets.append(idx)
        for _ in range(int(num_samples * 0.3)):
            images.append(np.array(apply_screen_capture_artifacts(create_pick_overlay(img)))); targets.append(idx)

    X = (np.array(images, dtype=np.float32) / 127.5) - 1.0
    y = np.array(targets, dtype=np.int32)
    p = np.random.permutation(len(X))
    X_train, X_val, y_train, y_val = train_test_split(X[p], y[p], test_size=0.1, random_state=42)

    print(f"\n[5/6] Training MobileNetV3Small...")
    base_model = applications.MobileNetV3Small(input_shape=(CONFIG['size_main'], CONFIG['size_main'], 3), include_top=False, weights='imagenet')
    base_model.trainable = False
    
    model = keras.Sequential([
        base_model, layers.GlobalAveragePooling2D(), layers.BatchNormalization(),
        layers.Dense(256, activation='relu'), layers.Dropout(0.5), layers.Dense(len(labels), activation='softmax')
    ])
    
    model.compile(optimizer=Adam(learning_rate=CONFIG['lr_mobilenet']), loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    model.fit(X_train, y_train, validation_data=(X_val, y_val), epochs=CONFIG['epochs_mobilenet']//2, batch_size=CONFIG['batch_size_mobilenet'], verbose=1)
    
    base_model.trainable = True
    for layer in base_model.layers[:-10]: layer.trainable = False
    model.compile(optimizer=Adam(learning_rate=CONFIG['lr_mobilenet']/10), loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    model.fit(X_train, y_train, validation_data=(X_val, y_val), epochs=CONFIG['epochs_mobilenet']//2, batch_size=CONFIG['batch_size_mobilenet'], verbose=1)

    print(f"\n[6/6] Exporting MobileNet to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    out_path = Path(CONFIG['output_dir']) / CONFIG['mobilenet_tflite']
    with open(out_path, 'wb') as f: f.write(tflite_model)
    print(f"✓ MobileNet TFLite saved to {out_path}")
    
    labels_path = Path(CONFIG['output_dir']) / CONFIG['mobilenet_labels']
    with open(labels_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(labels))
    print(f"✓ Labels saved to {labels_path}")

def main():
    print("=" * 60)
    print("MLBB FOCUSED TRAINING PIPELINE")
    print("=" * 60)
    
    heroes = load_heroes()
    if not heroes: return 1
    
    valid_heroes = download_portraits(heroes)
    if not valid_heroes: return 1
    
    generate_and_train_yolo(valid_heroes)
    train_mobilenet(valid_heroes)
    
    print("\n" + "=" * 60)
    print("✅ ALL MODELS TRAINED AND EXPORTED SUCCESSFULLY")
    print("=" * 60)
    return 0

if __name__ == '__main__':
    exit(main())
