# File: train_yolo.py
# Requires: pip install ultralytics torch tensorflow-cpu

from ultralytics import YOLO
import torch
import os

# --- Configuration ---
DATA_YAML = "data.yaml"      # Path to your YOLO dataset config
EPOCHS = 100
IMG_SIZE = 640
MODEL_NAME = "yolov8n.pt"   # Pretrained nano model
EXPORT_NAME = "hero_detector"

# --- 1. Train the model ---
print(">>> Starting training...")
model = YOLO(MODEL_NAME)
model.train(data=DATA_YAML, epochs=EPOCHS, imgsz=IMG_SIZE)
print(">>> Training complete.")

# --- 2. Load the best weights and export to TFLite ---
best_weights = "runs/detect/train/weights/best.pt"
if not os.path.exists(best_weights):
    print("Error: best.pt not found. Check training output.")
    exit()

trained_model = YOLO(best_weights)
print(">>> Exporting to TFLite...")
trained_model.export(format="tflite", imgsz=IMG_SIZE, int8=True)
print(f">>> TFLite model saved to: {EXPORT_NAME}_int8.tflite")