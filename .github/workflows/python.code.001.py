#!/usr/bin/env python3
"""
AI batch edit script for Kotlin Android project files.
Processes files in either ascending or descending order based on environment variable ORDER.
Each file is sent to GitHub's free AI inference API for improvement.
"""

import os
import sys
import time
import random
import requests
from pathlib import Path

# ======================== CONFIGURATION ========================
API_URL = "https://models.github.ai/inference/chat/completions"
MODEL_NAME = "gpt-4o"
MAX_TOKENS = 16000
TEMPERATURE = 0.1

# File extensions to process (non‑media, code/text)
EXTENSIONS = [
    "*.kt", "*.kts", "*.java", "*.gradle", "*.aidl",
    "*.xml", "*.properties", "*.pro"
]
SPECIAL_FILES = [".gitignore"]  # exact filenames
# ================================================================

def get_file_list(repo_path):
    """Collect all target files in the repository."""
    files = []
    root = Path(repo_path)
    for ext in EXTENSIONS:
        files.extend(root.rglob(ext))
    for name in SPECIAL_FILES:
        f = root / name
        if f.exists():
            files.append(f)
    # Remove duplicates and convert to absolute path strings
    files = sorted(set(files))
    return files

def improve_file(file_path, token, retries=3):
    """Send file content to AI, overwrite if improved."""
    with open(file_path, "r", encoding="utf-8") as f:
        original = f.read()

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    # System prompt (generic for all file types)
    system_prompt = {
        "role": "system",
        "content": (
            "You are an expert developer. Improve the provided content for correctness, performance, "
            "readability, and maintainability. Fix bugs, update deprecated syntax, and follow language best practices. "
            "Return ONLY the improved content – no explanations, no extra text."
        )
    }

    payload = {
        "model": MODEL_NAME,
        "messages": [
            system_prompt,
            {"role": "user", "content": original}
        ],
        "max_tokens": MAX_TOKENS,
        "temperature": TEMPERATURE
    }

    for attempt in range(retries):
        try:
            resp = requests.post(API_URL, headers=headers, json=payload)
            if resp.status_code == 429:
                wait = (2 ** attempt) + random.uniform(0, 1)
                print(f"⚠️ Rate limited on {file_path}. Retrying in {wait:.2f}s...")
                time.sleep(wait)
                continue
            resp.raise_for_status()
            data = resp.json()
            improved = data["choices"][0]["message"]["content"]

            if improved and improved != original:
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(improved)
                print(f"✅ Updated: {file_path}")
            else:
                print(f"⏭️  No change: {file_path}")
            return True

        except Exception as e:
            print(f"❌ Error on attempt {attempt+1} for {file_path}: {e}", file=sys.stderr)
            if attempt == retries - 1:
                return False
            time.sleep(2 ** attempt)
    return False

def main():
    repo_path = os.environ.get("REPO_PATH", ".")
    order = os.environ.get("ORDER", "ascending").lower()
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("❌ GITHUB_TOKEN environment variable not set", file=sys.stderr)
        sys.exit(1)

    files = get_file_list(repo_path)
    if order == "descending":
        files.reverse()
        print(f"📁 Processing {len(files)} files in DESCENDING order")
    else:
        print(f"📁 Processing {len(files)} files in ASCENDING order")

    for i, file in enumerate(files):
        print(f"Processing ({i+1}/{len(files)}): {file}")
        improve_file(file, token)
        delay = 6 + random.uniform(0, 2)
        print(f"Sleeping for {delay:.2f}s before next file...")
        time.sleep(delay)

    print("🎉 Batch edit finished.")

if __name__ == "__main__":
    main()
