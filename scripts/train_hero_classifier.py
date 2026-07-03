import os
import json
import requests
import numpy as np
from PIL import Image
from tqdm import tqdm
import tensorflow as tf
from tensorflow.keras import layers, models, applications
from tensorflow.keras.optimizers import Adam

# ==========================================
# CONFIGURATION & PATHS
# ==========================================
# Detect project root (assuming script is run from project root or scripts/ folder)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..'))

# Input/Output Paths
JSON_PATH = os.path.join(PROJECT_ROOT, 'app/src/main/res/raw/default_heroes.json')
ASSETS_DIR = os.path.join(PROJECT_ROOT, 'app/src/main/assets')
PORTRAITS_DIR = os.path.join(ASSETS_DIR, 'portraits')

# TFLite Outputs
TFLITE_MODEL_PATH = os.path.join(ASSETS_DIR, 'mlbb_hero_classifier.tflite')
LABELS_FILE_PATH = os.path.join(ASSETS_DIR, 'hero_classifier_labels.txt')

# Image Dimensions (per misc.md §13 and TD-18)
SIZE_MAIN = 224  # For TFLite training (MobileNetV3Small input)
SIZE_PICK = 128  # TD-18: hero.pick.png
SIZE_BAN = 64    # TD-18: hero.ban.png

# Training Hyperparameters
EPOCHS = 50
BATCH_SIZE = 16
LEARNING_RATE = 0.0005
AUGMENTATION_FACTOR = 15  # Augment single CDN image to prevent overfitting

# ==========================================
# STEP 1: LOAD & CLEAN JSON DATA
# ==========================================
def load_heroes():
    """Loads default_heroes.json and strips whitespace from keys/values."""
    print(f"[1/4] Loading hero data from {JSON_PATH}...")
    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        raw_data = json.load(f)
    
    # The provided JSON has trailing spaces in keys (e.g., "id ", "imageUrl ")
    cleaned_data = []
    for item in raw_data:
        cleaned_item = {
            k.strip(): v.strip() if isinstance(v, str) else v 
            for k, v in item.items()
        }
        cleaned_data.append(cleaned_item)
        
    print(f"[1/4] Loaded {len(cleaned_data)} heroes.")
    return cleaned_data

# ==========================================
# STEP 2: DOWNLOAD & GENERATE VARIANTS (TD-18)
# ==========================================
def download_and_generate_variants(heroes):
    """Downloads CDN portraits and creates main/pick/ban variants."""
    print(f"[2/4] Downloading portraits and generating TD-18 variants...")
    
    main_dir = os.path.join(PORTRAITS_DIR, 'main')
    pick_dir = os.path.join(PORTRAITS_DIR, 'pick')
    ban_dir = os.path.join(PORTRAITS_DIR, 'ban')
    
    for d in [main_dir, pick_dir, ban_dir]:
        os.makedirs(d, exist_ok=True)
        
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    valid_heroes = []
    
    for hero in tqdm(heroes, desc="Processing"):
        hero_id = hero.get('id')
        hero_name = hero.get('name')
        image_url = hero.get('imageUrl')
        
        if not hero_id or not image_url:
            continue
            
        # Download main portrait if not exists
        main_path = os.path.join(main_dir, f"{hero_id}.png")
        if not os.path.exists(main_path):
            try:
                response = requests.get(image_url, headers=headers, timeout=10)
                response.raise_for_status()
                with open(main_path, 'wb') as f:
                    f.write(response.content)
            except Exception as e:
                print(f"\nFailed to download {hero_name} (ID {hero_id}): {e}")
                continue
        
        # Generate pick and ban variants
        try:
            img = Image.open(main_path).convert('RGB')
            
            # hero.pick.png (128x128)
            pick_img = img.resize((SIZE_PICK, SIZE_PICK), Image.LANCZOS)
            pick_img.save(os.path.join(pick_dir, f"{hero_id}.png"))
            
            # hero.ban.png (64x64)
            ban_img = img.resize((SIZE_BAN, SIZE_BAN), Image.LANCZOS)
            ban_img.save(os.path.join(ban_dir, f"{hero_id}.png"))
            
            valid_heroes.append({'id': hero_id, 'name': hero_name})
        except Exception as e:
            print(f"\nError processing variants for {hero_name}: {e}")
            
    print(f"[2/4] Successfully prepared {len(valid_heroes)} heroes.")
    return valid_heroes

# ==========================================
# STEP 3: PREPARE DATASET & AUGMENT
# ==========================================
def prepare_dataset(main_dir, valid_heroes):
    """Loads images and applies heavy augmentation."""
    print(f"[3/4] Preparing dataset with augmentation...")
    
    # Sort by ID to ensure label index matches output neuron index (misc.md §13)
    valid_heroes.sort(key=lambda x: x['id'])
    labels = [h['name'] for h in valid_heroes]
    
    images = []
    targets = []
    
    for idx, hero in enumerate(valid_heroes):
        img_path = os.path.join(main_dir, f"{hero['id']}.png")
        img = Image.open(img_path).convert('RGB')
        img_array = np.array(img)
        images.append(img_array)
        targets.append(idx)
        
    base_images = np.array(images)
    base_targets = np.array(targets)
    
    # Data Augmentation Layer
    aug_model = tf.keras.Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.15),
        layers.RandomZoom(0.15),
        layers.RandomContrast(0.2),
        layers.RandomBrightness(0.2),
    ])
    
    augmented_images = []
    augmented_targets = []
    
    print("Generating augmented samples...")
    for i in tqdm(range(len(base_images))):
        img = base_images[i]
        target = base_targets[i]
        
        # Add original
        augmented_images.append(img)
        augmented_targets.append(target)
        
        # Add augmented versions
        img_tensor = tf.expand_dims(img, 0)
        for _ in range(AUGMENTATION_FACTOR):
            aug_img = aug_model(img_tensor, training=True)
            augmented_images.append(aug_img.numpy()[0])
            augmented_targets.append(target)
            
    X = np.array(augmented_images, dtype=np.float32)
    y = np.array(augmented_targets, dtype=np.int32)
    
    # Normalize to [-1, 1] as per misc.md §13
    X = (X / 127.5) - 1.0
    
    # Shuffle and Split
    p = np.random.permutation(len(X))
    X, y = X[p], y[p]
    
    split_idx = int(len(X) * 0.9)
    X_train, X_val = X[:split_idx], X[split_idx:]
    y_train, y_val = y[:split_idx], y[split_idx:]
    
    print(f"[3/4] Dataset ready: {len(X_train)} train, {len(X_val)} val samples.")
    return (X_train, y_train), (X_val, y_val), labels

# ==========================================
# STEP 4: TRAIN & CONVERT TO TFLITE
# ==========================================
def train_and_convert(train_data, val_data, labels):
    """Trains MobileNetV3Small and exports to float16 TFLite."""
    print(f"[4/4] Training MobileNetV3Small...")
    
    X_train, y_train = train_data
    X_val, y_val = val_data
    num_classes = len(labels)
    
    # Build Model (MobileNetV3Small as per misc.md §13)
    base_model = applications.MobileNetV3Small(
        input_shape=(SIZE_MAIN, SIZE_MAIN, 3),
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False  # Freeze base initially
    
    model = models.Sequential([
        base_model,
        layers.GlobalAveragePooling2D(),
        layers.BatchNormalization(),
        layers.Dense(256, activation='relu'),
        layers.Dropout(0.5),
        layers.Dense(num_classes, activation='softmax')
    ])
    
    model.compile(
        optimizer=Adam(learning_rate=LEARNING_RATE),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # Phase 1: Train top layers
    model.fit(X_train, y_train, 
              validation_data=(X_val, y_val),
              epochs=EPOCHS // 2, 
              batch_size=BATCH_SIZE,
              verbose=1)
    
    # Phase 2: Fine-tune last 10 layers of base model
    base_model.trainable = True
    for layer in base_model.layers[:-10]:
        layer.trainable = False
        
    model.compile(
        optimizer=Adam(learning_rate=LEARNING_RATE / 10),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    model.fit(X_train, y_train, 
              validation_data=(X_val, y_val),
              epochs=EPOCHS // 2, 
              batch_size=BATCH_SIZE,
              verbose=1)
              
    # Convert to TFLite (Float16 quantization as per misc.md §13)
    print("Converting to TFLite (float16)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    # Save TFLite model
    with open(TFLITE_MODEL_PATH, 'wb') as f:
        f.write(tflite_model)
    print(f"Model saved to {TFLITE_MODEL_PATH} ({len(tflite_model) / 1024 / 1024:.2f} MB)")
    
    # Save Labels file (misc.md §13: line index n maps to output neuron n, sorted by ID)
    with open(LABELS_FILE_PATH, 'w', encoding='utf-8') as f:
        f.write(f"# MLBB Hero Classifier Labels\n")
        f.write(f"# Total classes: {num_classes}\n")
        for label in labels:
            f.write(f"{label}\n")
    print(f"Labels saved to {LABELS_FILE_PATH}")

# ==========================================
# MAIN EXECUTION
# ==========================================
if __name__ == '__main__':
    if not os.path.exists(JSON_PATH):
        print(f"ERROR: {JSON_PATH} not found.")
        exit(1)
        
    # 1. Load JSON
    heroes = load_heroes()
    if not heroes:
        print("ERROR: No valid heroes loaded.")
        exit(1)
        
    # 2. Download & Generate Variants
    valid_heroes = download_and_generate_variants(heroes)
    if not valid_heroes:
        print("ERROR: No valid heroes processed.")
        exit(1)
        
    # 3. Prepare Dataset
    main_dir = os.path.join(PORTRAITS_DIR, 'main')
    train_data, val_data, labels = prepare_dataset(main_dir, valid_heroes)
    
    # 4. Train & Export
    train_and_convert(train_data, val_data, labels)
    
    print("\n✅ PIPELINE COMPLETE.")
    print("️  IMPORTANT: Ensure `androidResources { noCompress += listOf(\"tflite\") }` is in app/build.gradle.kts")
