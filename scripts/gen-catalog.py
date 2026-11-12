#!/usr/bin/env python3
"""Generate the TV app store catalog from the two APK libraries on oracle-1.

Walks /srv/tvapp/tvapp_store and /srv/tvapp/tvapp-xyzzb, extracts metadata
with aapt, hashes every APK with sha256, dedupes by package name (keeping the
highest versionCode), and writes:

  catalog.json      - one entry per package (the store catalog)
  catalog-all.json  - every APK scanned, with a "kept" flag

Usage (on oracle-1):
    python3 gen-catalog.py [--root /srv/tvapp] [--out-dir /srv/tvapp]

Requirements: aapt, python3.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import quote

BASE_URL = "https://apk.ciweihaha.win"

# Category normalization: tvapp_store uses English dir names, tvapp-xyzzb uses
# Chinese. Both map to one shared Chinese category set for the store.
CATEGORY_MAP = {
    "live": "电视直播",
    "video": "影视",
    "ktv": "K歌",
    "entertainment": "娱乐",
    "education": "教育",
    "movement": "运动健身",
    "utility": "工具",
    # xyzzb categories are already Chinese
    "电视直播": "电视直播",
    "影视": "影视",
    "K歌": "K歌",
    "TVBox": "TVBox",
}

# Tie-break for identical package+versionCode: prefer the curated library.
SOURCE_PRIORITY = {"tvapp_store": 0, "tvapp-xyzzb": 1}

PKG_RE = re.compile(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'")
LABEL_RE = re.compile(r"^application-label:'(.*)'$")
LABEL2_RE = re.compile(r"^application: label='(.*)'")


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def aapt_info(path):
    """Return (package, version_code, version_name, label) or None on failure."""
    try:
        proc = subprocess.run(
            ["aapt", "dump", "badging", path],
            capture_output=True, text=True, timeout=120,
        )
    except Exception:
        return None
    pkg = vc = vn = label = None
    for line in proc.stdout.splitlines():
        m = PKG_RE.match(line)
        if m:
            pkg, vc, vn = m.group(1), int(m.group(2)), m.group(3)
            continue
        m = LABEL_RE.match(line) or LABEL2_RE.match(line)
        if m and not label:
            label = m.group(1)
    if pkg is None:
        return None
    return pkg, vc, vn, label


def collect_icons(root):
    """Best-effort icon map: app name -> public icon URL (tvapp_store/icons)."""
    icons = {}
    icon_dir = os.path.join(root, "tvapp_store", "icons")
    if not os.path.isdir(icon_dir):
        return icons
    for name in os.listdir(icon_dir):
        stem, ext = os.path.splitext(name)
        if ext.lower() in (".png", ".webp", ".jpg", ".jpeg"):
            icons.setdefault(stem, BASE_URL + "/tvapp_store/icons/" + quote(name))
    return icons


def scan(root):
    """Scan both libraries. Returns (entries, aapt_failures)."""
    icons = collect_icons(root)
    entries = []
    failures = []
    for source in ("tvapp_store", "tvapp-xyzzb"):
        lib_root = os.path.join(root, source)
        for dirpath, _dirnames, filenames in os.walk(lib_root):
            for filename in sorted(filenames):
                if not filename.lower().endswith(".apk"):
                    continue
                full = os.path.join(dirpath, filename)
                rel = os.path.relpath(full, lib_root)
                parts = rel.split(os.sep)
                if source == "tvapp_store":
                    # app/<category>/<appname>/.../file.apk
                    raw_cat = parts[1] if len(parts) > 2 and parts[0] == "app" else None
                    app_dir = parts[2] if len(parts) > 3 and parts[0] == "app" else None
                else:
                    # <category>/<appname>/.../file.apk, or file.apk at root
                    raw_cat = parts[0] if len(parts) > 1 else None
                    app_dir = parts[1] if len(parts) > 2 else None
                category = CATEGORY_MAP.get(raw_cat, "未分类") if raw_cat else "未分类"
                entries.append({
                    "path": full,
                    "file": filename,
                    "rel": rel,
                    "source": source,
                    "category": category,
                    "sourceCategory": raw_cat,
                    "appDir": app_dir,
                })
    total = len(entries)
    print(f"found {total} APKs", flush=True)
    for i, e in enumerate(entries, 1):
        if i % 50 == 0 or i == total:
            print(f"  ... {i}/{total}", flush=True)
        e["size"] = os.path.getsize(e["path"])
        e["sha256"] = sha256_of(e["path"])
        info = aapt_info(e["path"])
        if info is None:
            failures.append(e["rel"])
            e["package"] = None
            e["versionCode"] = 0
            e["versionName"] = ""
            e["label"] = os.path.splitext(e["file"])[0]
        else:
            e["package"], e["versionCode"], e["versionName"], label = info
            e["label"] = label or os.path.splitext(e["file"])[0]
        # Best-effort icon: match by label, then by app folder name.
        icon = icons.get(e["label"]) or (icons.get(e["appDir"]) if e["appDir"] else None)
        if icon:
            e["icon"] = icon
        e["url"] = BASE_URL + "/" + e["source"] + "/" + "/".join(
            quote(p) for p in e["rel"].split(os.sep)
        )
    return entries, failures


def dedupe(entries):
    """Keep one entry per package: highest versionCode wins.

    Ties: prefer tvapp_store, then larger file, then filename (deterministic).
    Entries without a package name (aapt failed) are never deduped.
    """
    best = {}
    kept_ids = set()
    for e in entries:
        pkg = e["package"]
        if pkg is None:
            kept_ids.add(id(e))
            continue
        cur = best.get(pkg)
        if cur is None:
            best[pkg] = e
            continue
        key_new = (e["versionCode"], -SOURCE_PRIORITY[e["source"]], e["size"])
        key_cur = (cur["versionCode"], -SOURCE_PRIORITY[cur["source"]], cur["size"])
        if key_new > key_cur or (key_new == key_cur and e["file"] < cur["file"]):
            best[pkg] = e
    for e in best.values():
        kept_ids.add(id(e))
    return kept_ids


def public_entry(e):
    out = {
        "package": e["package"],
        "label": e["label"],
        "versionName": e["versionName"],
        "versionCode": e["versionCode"],
        "size": e["size"],
        "sha256": e["sha256"],
        "category": e["category"],
        "source": e["source"],
        "url": e["url"],
        "file": e["file"],
    }
    if e.get("sourceCategory"):
        out["sourceCategory"] = e["sourceCategory"]
    if e.get("icon"):
        out["icon"] = e["icon"]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="/srv/tvapp")
    ap.add_argument("--out-dir", default="/srv/tvapp")
    args = ap.parse_args()

    t0 = time.time()
    entries, failures = scan(args.root)
    kept_ids = dedupe(entries)

    kept = sorted(
        (e for e in entries if id(e) in kept_ids),
        key=lambda e: (e["category"], e["label"]),
    )
    all_sorted = sorted(
        entries,
        key=lambda e: (e["category"], e["label"], e["source"], e["file"]),
    )

    categories = {}
    for e in kept:
        categories[e["category"]] = categories.get(e["category"], 0) + 1
    sources = {}
    for e in kept:
        sources[e["source"]] = sources.get(e["source"], 0) + 1

    generated_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    stats = {
        "total_apks": len(entries),
        "unique_packages": len(kept),
        "dropped_duplicates": len(entries) - len(kept),
        "aapt_failed": len(failures),
        "categories": categories,
        "sources": sources,
    }

    catalog = {
        "generated_at": generated_at,
        "base_url": BASE_URL,
        "stats": stats,
        "categories": sorted(categories),
        "apps": [public_entry(e) for e in kept],
    }
    catalog_all = {
        "generated_at": generated_at,
        "base_url": BASE_URL,
        "stats": stats,
        "apps": [
            {**public_entry(e), "kept": id(e) in kept_ids} for e in all_sorted
        ],
    }

    os.makedirs(args.out_dir, exist_ok=True)
    with open(os.path.join(args.out_dir, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    with open(os.path.join(args.out_dir, "catalog-all.json"), "w", encoding="utf-8") as f:
        json.dump(catalog_all, f, ensure_ascii=False, indent=2)

    print(f"done in {time.time() - t0:.1f}s", flush=True)
    print(json.dumps(stats, ensure_ascii=False, indent=2), flush=True)
    if failures:
        print(f"aapt failed on {len(failures)} files:", flush=True)
        for rel in failures:
            print(f"  - {rel}", flush=True)


if __name__ == "__main__":
    main()
