#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Local manual test runner for ai_issue_analyze.py

Usage:
  export GITHUB_TOKEN=$(gh auth token)
  export DEEPSEEK_API_KEY=...
  export DEEPSEEK_BASE_URL=https://api.deepseek.com
  export DEEPSEEK_MODEL=deepseek-chat
  python .github/test_analyze_local.py

Notes:
  - Run from the repository root directory.
  - This script simulates 6 issues (4 Chinese, 2 English) with free-form user text.
  - It writes temporary event payloads under .github/.ai/tmp_event_*.json
  - It executes .github/ai_issue_analyze.py per sample and prints the result path/title/labels.
"""

import json, os, subprocess, sys, pathlib, shutil

ROOT = pathlib.Path(__file__).resolve().parents[2]
GITHUB_DIR = ROOT / ".github"
AI_DIR = GITHUB_DIR / ".ai"
ANALYZER = GITHUB_DIR / "scripts" / "ai_issue_analyze.py"

def ensure_env(var: str):
    v = os.environ.get(var)
    if not v:
        print(f"[ERROR] Missing env: {var}", file=sys.stderr)
        sys.exit(1)
    return v

def main():
    print("== Local AI analyze test ==")
    ensure_env("GITHUB_TOKEN")
    ensure_env("DEEPSEEK_API_KEY")
    ensure_env("DEEPSEEK_BASE_URL")
    ensure_env("DEEPSEEK_MODEL")

    if not ANALYZER.exists():
        print(f"[ERROR] Analyzer not found: {ANALYZER}", file=sys.stderr)
        sys.exit(1)

    # Infer repo owner/name via `gh` if possible; otherwise require env
    owner = os.environ.get("TEST_OWNER")
    name = os.environ.get("TEST_REPO")
    if not owner or not name:
        # try gh
        try:
            out = subprocess.check_output(["gh", "repo", "view", "--json", "nameWithOwner"], text=True)
            nwo = json.loads(out)["nameWithOwner"]
            owner, name = nwo.split("/", 1)
        except Exception:
            print("[WARN] Could not infer repo from gh. Set TEST_OWNER and TEST_REPO.")
            owner = ensure_env("TEST_OWNER")
            name = ensure_env("TEST_REPO")

    # 6 realistic samples (4 zh, 2 en), short + long, meaningful
    samples = [
        # 1) CN · bug-ish teleport drop
        (9001, "传送到最近村庄却摔死",
         "我用 /ai 让你把我传送到最近的村庄，结果落点在半空直接摔死了。"
         "建议：传送前判断脚下是否安全，如果不安全，请在脚下几格生成平台再落地。服务器为 Fabric 1.21.8，开启了离线模式。"),

        # 2) CN · feature request
        (9002, "希望支持“最近的村庄”智能传送",
         "希望直接说“带我去最近的村庄”就能自动寻找最近村庄并传送，且要避开岩浆、悬崖、虚空。"
         "另外能否返回村庄坐标、朝向，以及路径长度的估计？"),

        # 3) CN · enhancement / performance
        (9003, "世界分析工具搜索范围太小太慢",
         "世界分析工具在 128 格以外几乎找不到结构，且扫描时服务端 TPS 会掉到 5-8。"
         "希望优化扫描算法或分片执行，避免卡顿。"),

        # 4) CN · question
        (9004, "客户端必须装这个 mod 吗？",
         "如果服务器装了这个 AI mod，但客户端没装，能正常进入吗？是否会有协议不兼容或崩溃？"),

        # 5) EN · bug · dependency crash
        (9005, "NoClassDefFoundError after upgrading AI deps",
         "Server crashes on startup after upgrading to spring-ai-chat 1.0.1:\n"
         "NoClassDefFoundError: org/springframework/ai/template/StTemplateRenderer\n"
         "Env: Fabric 1.21.8, Loom 1.8.x. Please advise missing runtime deps or include transitive ones."),

        # 6) EN · feature
        (9006, "Per-player language detection & localization",
         "Please add per-player language detection (from client options) and localize AI replies accordingly. "
         "We need command outputs in EN/ZH based on each player, not server-wide.")
    ]

    AI_DIR.mkdir(parents=True, exist_ok=True)

    for num, title, body in samples:
        tmp_event = AI_DIR / f"tmp_event_{num}.json"
        payload = {
            "issue": {
                "number": num,
                "title": title,
                "body": body,
                # labels in event are irrelevant for Step A; Step A reads repo labels separately
                "labels": [{"name": "ai:triage"}]
            },
            "repository": {
                "name": name,
                "owner": {"login": owner}
            }
        }
        tmp_event.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

        env = os.environ.copy()
        env["GITHUB_EVENT_PATH"] = str(tmp_event)

        print(f"\n--- Running analyze for sample #{num}: {title} ---")
        # Call the analyzer; capture stdout/stderr so failures are visible
        proc = subprocess.run([sys.executable, str(ANALYZER)], env=env)
        if proc.returncode != 0:
            print(f"[FAIL] analyze failed for #{num}")
            sys.exit(proc.returncode)

        out_json = GITHUB_DIR / ".ai" / f"issue_{num}.json"
        if not out_json.exists():
            print(f"[FAIL] result json not found: {out_json}")
            sys.exit(1)

        data = json.loads(out_json.read_text(encoding="utf-8"))
        print(f"[OK] -> {out_json}")
        print(f"     template_file: {data.get('template_file')}")
        print(f"     title        : {data.get('title')}")
        print(f"     labels       : {data.get('labels')}")
        # Avoid backslashes inside f-string expressions: use splitlines instead of "\n" replacement
        preview = ' '.join(((data.get('markdown_body') or '')[:160]).splitlines())
        print(f"     body preview : {preview} ...")

    print("\n== All samples analyzed successfully ==")

if __name__ == "__main__":
    main()
