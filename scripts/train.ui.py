import os
import yaml
from ultralytics import YOLO
from pathlib import Path

# --- Configuration ---
# Pointing to the scripts/temp directory as requested
DATASET_DIR = "./scripts/temp" 
IMAGES_DIR = os.path.join(DATASET_DIR, "images")
LABELS_DIR = os.path.join(DATASET_DIR, "labels")
DATASET_YAML = "dataset.yaml"
OUTPUT_DIR = "./runs/detect/mlbb_yolo_v2"

# Classes defined in Phase 2: The CV Revolution
CLASSES = ['ban_slot', 'pick_slot', 'timer', 'phase_banner', 'enemy_hero', 'ally_hero']

def setup_dataset():
    """Creates the dataset.yaml file required by YOLO."""
    data = {
        'path': os.path.abspath(DATASET_DIR), # Root directory for the dataset
        'train': 'images', 
        'val': 'images',   
        'nc': len(CLASSES),
        'names': CLASSES
    }
    
    with open(DATASET_YAML, 'w') as f:
        yaml.dump(data, f, sort_keys=False)
    print(f"Created {DATASET_YAML}")

def train_model():
    print("Starting YOLOv8-Nano training (Phase 2: CV Revolution)...")
    
    # Load the Nano model as mandated by the Master Plan
    model = YOLO('yolov8n.pt')  

    # Train the model
    results = model.train(
        data=DATASET_YAML,
        epochs=100,       # Adjust based on dataset size (1000+ images recommended)
        imgsz=640,        # Standard input size
        batch=16,         # Increase if you have GPU VRAM
        name='mlbb_yolo_v2', 
        project='./runs/detect',
        patience=20,      
        save=True,
        # Optimizations for mobile deployment
        cos_lr=True,      # Cosine learning rate scheduler
        warmup_epochs=5,  # Warmup for stability
    )
    
    print(f"Training complete! Model saved at {OUTPUT_DIR}/weights/best.pt")
    return model

def export_to_tflite(model_path):
    """
    Exports the trained model to TFLite with INT8 quantization.
    This is required for Hardware-Accelerated Inference (TFLite GPU Delegates) 
    as per Pillar I of the Master Plan.
    """
    print(f"Exporting model to TFLite (INT8 Quantized)...")
    
    # Load the best trained model
    model = YOLO(model_path)
    
    # Export to TFLite
    # int8=True enables INT8 quantization for smaller size and faster inference on mobile
    model.export(format='tflite', int8=True)
    
    tflite_path = f"{model_path.replace('.pt', '.tflite')}"
    print(f"Export complete! TFLite model saved at {tflite_path}")
    print("-> NEXT STEP: Copy this .tflite file to :core:cv/src/main/assets/mlbb_yolo_v2.tflite")

if __name__ == '__main__':
    # 1. Setup Directory Structure
    os.makedirs(IMAGES_DIR, exist_ok=True)
    os.makedirs(LABELS_DIR, exist_ok=True)
    setup_dataset()
    
    # 2. Check for labels
    label_files = [f for f in os.listdir(LABELS_DIR) if f.endswith('.txt')]
    if len(label_files) == 0:
        print("WARNING: No label files found in /temp/labels/.")
        print("Please annotate your screenshots using Roboflow or LabelImg before training.")
        print("Classes to annotate:", CLASSES)
    else:
        print(f"Found {len(label_files)} label files. Ready to train.")
        
        # 3. Train
        trained_model = train_model()
        
        # 4. Export for Android Integration
        best_pt_path = f"{OUTPUT_DIR}/weights/best.pt"
        if os.path.exists(best_pt_path):
            export_to_tflite(best_pt_path)
        else:
            print("Could not find best.pt for export. Check training logs.")
