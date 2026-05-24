#!/usr/bin/env python3

import os
import sys
import re
import subprocess
import time
from pathlib import Path
from typing import List, Optional, Tuple

from openai import OpenAI
from github import Github, GithubException


DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
REPO_FULL_NAME = os.environ.get("REPO_FULL_NAME")
BASE_BRANCH = os.environ.get("BASE_BRANCH", "main")
WORKSPACE = os.environ.get("GITHUB_WORKSPACE") or os.getcwd()
NEW_BRANCH = "deepseek-systemic-review"
MODEL = "deepseek-chat"          # or "deepseek-reasoner"
TEMPERATURE = 0.2


SYSTEM_PROMPT_FULL = """You are an expert Android Kotlin engineer. For every file you review, you will output the **complete corrected file content**.
If the file is already perfect, output the original content unchanged.
Always prefix the output with `=== FULL FILE: path/to/file.kt ===` on its own line, then the entire file content.
Do not output diffs or explanations outside of that block.
Focus on fixing bugs, improving logic, optimizing performance, and adhering to 2026 Android/Kotlin best practices.
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


def phase1_analysis(client: OpenAI, files: List[str]) -> str:
    dirs = {}
    for f in files:
        parts = f.split('/')
        if len(parts) > 1:
            top_dir = parts[0]
            dirs.setdefault(top_dir, []).append(f)
    summary = "Repository structure:\n"
    for d, flist in list(dirs.items())[:10]:
        summary += f"- {d}/ ({len(flist)} files)\n"
    gradle_files = [f for f in files if f.endswith('.gradle') or f.endswith('.gradle.kts')]
    summary += "\nBuild files:\n"
    for gf in gradle_files[:3]:
        content = read_file_content(gf)
        if content:
            summary += f"### {gf}\n{content[:1500]}\n...\n\n"
    prompt = f"""Analyze the following codebase summary and produce a short (200 words) diagnosis:
- Main architectural patterns observed.
- Potential cross-cutting issues.
- Recommendations for systematic fixes.

{summary}
"""
    try:
        resp = client.chat.completions.create(
            model=MODEL,
            messages=[
                {"role": "system", "content": "You are an Android architecture expert. Provide concise analysis."},
                {"role": "user", "content": prompt}
            ],
            temperature=0.3,
            max_tokens=1000
        )
        return resp.choices[0].message.content
    except Exception as e:
        return f"[Analysis failed: {e}]"


def review_and_fix_file_full(client: OpenAI, rel_path: str, content: str) -> Tuple[Optional[str], Optional[str]]:
    user_prompt = f"""Provide the complete corrected version of the following file:
File: {rel_path}
Current content:
```kotlin
{content}
```

Output exactly as:
=== FULL FILE: {rel_path} ===
<new file content>
"""
try:
resp = client.chat.completions.create(
model=MODEL,
messages=[
{"role": "system", "content": SYSTEM_PROMPT_FULL},
{"role": "user", "content": user_prompt}
],
temperature=TEMPERATURE,
max_tokens=6000
)
result = resp.choices[0].message.content
marker = f"=== FULL FILE: {rel_path} ==="
if marker in result:
new_content = result.split(marker, 1)[1].strip()
if new_content.startswith(""):
                new_content = new_content.split("", 1)[1].split("```", 1)[0].strip()
return new_content, None
else:
return None, "Marker not found in response"
except Exception as e:
return None, str(e)

def git_commit_and_push():
subprocess.run(["git", "checkout", "-b", NEW_BRANCH], cwd=WORKSPACE, check=True)
subprocess.run(["git", "add", "."], cwd=WORKSPACE, check=True)
subprocess.run(["git", "commit", "-m", "DeepSeek systemic improvements"], cwd=WORKSPACE, check=True)
subprocess.run(["git", "push", "origin", NEW_BRANCH, "--force"], cwd=WORKSPACE, check=True)
print(f"✅ Pushed branch {NEW_BRANCH}")

def create_pr():
if not GITHUB_TOKEN or not REPO_FULL_NAME:
print("Missing GitHub token or repo name, cannot create PR automatically.")
return
try:
g = Github(GITHUB_TOKEN)
repo = g.get_repo(REPO_FULL_NAME)
pr = repo.create_pull(
title="[AI] DeepSeek systemic code review & fixes",
body="This PR was generated by an automated DeepSeek script. It reviews all files and applies logic refinements.\n\nPlease verify carefully before merging.",
head=NEW_BRANCH,
base=BASE_BRANCH
)
print(f"✅ Pull Request created: {pr.html_url}")
except GithubException as e:
print(f"❌ Failed to create PR: {e}")

def main():
if not DEEPSEEK_API_KEY:
print("ERROR: DEEPSEEK_API_KEY not set")
sys.exit(1)

if name == "main":
main()
