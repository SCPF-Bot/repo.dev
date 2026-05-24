#!/usr/bin/env python3
import os
import sys
import subprocess
import time
import random
import re
from pathlib import Path
from typing import List, Optional, Tuple

# Try to import the new google-genai package, fallback to the old one
try:
    from google import genai
    from google.genai import types
    USE_NEW_PACKAGE = True
    print("Using google-genai (new package)")
except ImportError:
    import google.generativeai as genai
    USE_NEW_PACKAGE = False
    print("Using google-generativeai (legacy package)")

from github import Github, GithubException

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
REPO_FULL_NAME = os.environ.get("REPO_FULL_NAME")
BASE_BRANCH = os.environ.get("BASE_BRANCH", "main")
WORKSPACE = os.environ.get("GITHUB_WORKSPACE") or os.getcwd()
NEW_BRANCH = "gemini-systemic-review"

MODEL_NAME = "gemini-2.5-flash"
TEMPERATURE = 0.1
MAX_OUTPUT_TOKENS = 2048
BASE_DELAY_BETWEEN_FILES = 12   # seconds (respects 5 RPM free tier)

SYSTEM_PROMPT = """You are an expert Android Kotlin engineer. For every file you review, output the complete corrected file content.
If the file is perfect, output the original content unchanged.
Always prefix the output with `=== FULL FILE: path/to/file.kt ===` on its own line, then the entire file content.
Do not output diffs or explanations outside that block.
Focus on fixing bugs, improving logic, optimizing performance, and adhering to 2026 Android/Kotlin best practices.
Areas: null safety, coroutines, Jetpack Compose, MVVM, performance, security, Gradle version catalogs.
"""

def find_source_files(root_dir: str) -> List[str]:
    extensions = ('.kt', '.java', '.xml', '.gradle', '.kts', '.properties')
    root = Path(root_dir).resolve()
    files = []
    for ext in extensions:
        for p in root.rglob(f"*{ext}"):
            if any(part in p.parts for part in ['build', '.git', 'generated', 'tmp', 'node_modules']):
                continue
            if p.stat().st_size == 0:
                continue
            files.append(str(p.relative_to(root)))
    return sorted(files)

def read_file_content(rel_path: str) -> Optional[str]:
    full_path = os.path.join(WORKSPACE, rel_path)
    try:
        with open(full_path, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception:
        return None

def write_file_content(rel_path: str, content: str):
    full_path = os.path.join(WORKSPACE, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w', encoding='utf-8') as f:
        f.write(content)

def review_and_fix_file(rel_path: str, content: str) -> Tuple[Optional[str], Optional[str]]:
    user_prompt = f"""Provide the complete corrected version of the following file:
File: {rel_path}
Current content:
{content}

Output exactly as:
=== FULL FILE: {rel_path} ===
<new file content>
"""
    max_retries = 5
    base_delay = 2
    max_delay = 60
    jitter = 0.5

    for attempt in range(max_retries):
        try:
            if USE_NEW_PACKAGE:
                client = genai.Client(api_key=GEMINI_API_KEY)
                response = client.models.generate_content(
                    model=MODEL_NAME,
                    contents=user_prompt,
                    config=types.GenerateContentConfig(
                        system_instruction=SYSTEM_PROMPT,
                        temperature=TEMPERATURE,
                        max_output_tokens=MAX_OUTPUT_TOKENS,
                    )
                )
                result = response.text
            else:
                genai.configure(api_key=GEMINI_API_KEY)
                model = genai.GenerativeModel(
                    MODEL_NAME,
                    generation_config={
                        "temperature": TEMPERATURE,
                        "max_output_tokens": MAX_OUTPUT_TOKENS,
                    }
                )
                response = model.generate_content([SYSTEM_PROMPT, user_prompt])
                result = response.text

            if not result:
                return None, "Empty response text"

            marker = f"=== FULL FILE: {rel_path} ==="
            if marker in result:
                new_content = result.split(marker, 1)[1].strip()
                if new_content.startswith("```"):
                    new_content = new_content.split("```", 1)[1].split("```", 1)[0].strip()
                return new_content, None
            else:
                return None, "Marker not found in response"

        except Exception as e:
            error_str = str(e)
            is_retryable = False
            retry_after = None

            # Rate limit detection
            if "429" in error_str or "ResourceExhausted" in error_str or "quota" in error_str.lower():
                is_retryable = True
                delay_match = re.search(r'retry_delay[\s]*[\:][\s]*(\d+)', error_str)
                if delay_match:
                    retry_after = int(delay_match.group(1))

            # Also retry on server errors
            if hasattr(e, 'code') and 500 <= e.code < 600:
                is_retryable = True

            if not is_retryable:
                return None, error_str

            if retry_after:
                delay = retry_after
            else:
                delay = min(base_delay * (2 ** attempt), max_delay)
                delay += random.uniform(-jitter * delay, jitter * delay)
                delay = max(0.5, delay)

            if attempt < max_retries - 1:
                print(f"  Rate limited (attempt {attempt+1}/{max_retries}). Waiting {delay:.1f}s...")
                time.sleep(delay)
            else:
                return None, f"Rate limit persisted after {max_retries} retries: {error_str}"

    return None, "Max retries exceeded"

def git_commit_and_push():
    subprocess.run(["git", "checkout", "-b", NEW_BRANCH], cwd=WORKSPACE, check=True)
    subprocess.run(["git", "add", "."], cwd=WORKSPACE, check=True)
    subprocess.run(["git", "commit", "-m", "Gemini systemic improvements"], cwd=WORKSPACE, check=True)
    subprocess.run(["git", "push", "origin", NEW_BRANCH, "--force"], cwd=WORKSPACE, check=True)
    print(f"Pushed branch {NEW_BRANCH}")

def create_pr():
    if not GITHUB_TOKEN or not REPO_FULL_NAME:
        print("Missing GitHub token or repo name, cannot create PR automatically.")
        return
    try:
        g = Github(GITHUB_TOKEN)
        repo = g.get_repo(REPO_FULL_NAME)
        pr = repo.create_pull(
            title="[AI] Gemini systemic code review & fixes",
            body="This PR was generated by an automated Gemini script. It reviewed all files and applied logic refinements.\n\nPlease verify carefully before merging.",
            head=NEW_BRANCH,
            base=BASE_BRANCH
        )
        print(f"Pull Request created: {pr.html_url}")
    except GithubException as e:
        print(f"Failed to create PR: {e}")

def main():
    if not GEMINI_API_KEY:
        print("ERROR: GEMINI_API_KEY environment variable not set")
        sys.exit(1)

    print("Discovering source files...")
    files = find_source_files(WORKSPACE)
    print(f"Found {len(files)} files.")

    changes_made = 0
    for idx, rel_path in enumerate(files, 1):
        print(f"[{idx}/{len(files)}] Reviewing {rel_path}...")
        content = read_file_content(rel_path)
        if content is None:
            continue

        new_content, error = review_and_fix_file(rel_path, content)
        if error:
            print(f"  Error: {error}")
            continue
        if new_content is None:
            print("  No response, skipping")
            continue
        if new_content.strip() != content.strip():
            print("  Changes detected – applying")
            write_file_content(rel_path, new_content)
            changes_made += 1
        else:
            print("  No changes needed")

        if idx < len(files):
            print(f"  Waiting {BASE_DELAY_BETWEEN_FILES}s to respect rate limits...")
            time.sleep(BASE_DELAY_BETWEEN_FILES)

    if changes_made == 0:
        print("No changes were generated. Exiting without creating PR.")
        sys.exit(0)

    print(f"Applied changes to {changes_made} files.")
    print("Committing and pushing branch...")
    git_commit_and_push()
    print("Creating Pull Request...")
    create_pr()

if __name__ == "__main__":
    main()