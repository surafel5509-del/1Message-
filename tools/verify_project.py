#!/usr/bin/env python3
"""Static sanity checks for the 1Messages Android project.

The sandbox has no Android SDK, so this script verifies the things that can be checked
without a compiler: resource references, XML well-formedness, manifest wiring, package vs
path consistency, brace balance and obvious Kotlin mistakes.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/main")
JAVA = os.path.join(MAIN, "java/com/onemessages/app")
RES = os.path.join(MAIN, "res")

errors = []
warnings = []


def parse_strings(path):
    strings, plurals = set(), set()
    tree = ET.parse(path)
    for node in tree.getroot():
        tag = node.tag
        name = node.attrib.get("name")
        if tag == "string":
            strings.add(name)
        elif tag == "plurals":
            plurals.add(name)
        elif tag == "item":
            # string-array items etc: ignore
            pass
    return strings, plurals


def duplicate_resource_names(path):
    """aapt2 refuses to merge two <string>/<plurals> with the same name in one file.

    Our own string table is generated in several passes, so guard against duplicates here
    instead of discovering them only when CI reaches the resource merger.
    """
    seen, dups = set(), []
    tree = ET.parse(path)
    for node in tree.getroot():
        if node.tag not in ("string", "plurals", "color", "dimen"):
            continue
        name = node.attrib.get("name")
        if name is None:
            continue
        key = (node.tag, name)
        if key in seen:
            dups.append(f"{node.tag}/{name}")
        seen.add(key)
    return dups


def main():
    for rel in ("values/strings.xml", "values-am/strings.xml"):
        path = os.path.join(RES, rel)
        if not os.path.exists(path):
            continue
        for dup in duplicate_resource_names(path):
            errors.append(f"{rel}: duplicate resource {dup}")

    en_strings, en_plurals = parse_strings(os.path.join(RES, "values/strings.xml"))
    am_path = os.path.join(RES, "values-am/strings.xml")
    am_strings = set()
    if os.path.exists(am_path):
        am_strings, _ = parse_strings(am_path)
        missing_am = en_strings - am_strings
        if missing_am:
            warnings.append(f"Amharic missing {len(missing_am)} strings: {sorted(missing_am)[:8]}")
        extra_am = am_strings - en_strings
        if extra_am:
            warnings.append(f"Amharic has {len(extra_am)} unknown strings: {sorted(extra_am)[:8]}")

    kotlin_files = []
    for dirpath, _, filenames in os.walk(JAVA):
        for f in filenames:
            if f.endswith(".kt"):
                kotlin_files.append(os.path.join(dirpath, f))
    if not kotlin_files:
        errors.append("no Kotlin sources found")

    ref_strings, ref_plurals = set(), set()
    for path in kotlin_files:
        with open(path, encoding="utf-8") as fh:
            src = fh.read()
        ref_strings |= set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", src))
        ref_plurals |= set(re.findall(r"R\.plurals\.([A-Za-z0-9_]+)", src))

    for name in sorted(ref_strings - en_strings):
        errors.append(f"missing string resource: R.string.{name}")
    for name in sorted(ref_plurals - en_plurals):
        errors.append(f"missing plurals resource: R.plurals.{name}")

    # XML resources must at least be well-formed and reference existing names.
    defined_drawables = set()
    defined_mipmaps = set()
    defined_colors = set()
    defined_styles = set()
    for dirpath, _, filenames in os.walk(RES):
        folder = os.path.basename(dirpath)
        for f in filenames:
            if not f.endswith(".xml"):
                continue
            path = os.path.join(dirpath, f)
            base = os.path.splitext(f)[0]
            try:
                ET.parse(path)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"malformed XML {path}: {exc}")
                continue
            if folder.startswith("drawable"):
                defined_drawables.add(base)
            elif folder.startswith("mipmap"):
                defined_mipmaps.add(base)
            elif folder.startswith("values"):
                tree = ET.parse(path)
                for node in tree.getroot():
                    if node.tag == "color":
                        defined_colors.add(node.attrib.get("name"))
                    elif node.tag == "style":
                        defined_styles.add(node.attrib.get("name"))
    for mipmap in ("ic_launcher", "ic_launcher_round"):
        if mipmap not in defined_mipmaps:
            errors.append(f"missing mipmap {mipmap}")
    for drawable in ("ic_splash_logo", "ic_launcher_foreground", "ic_notification"):
        if drawable not in defined_drawables:
            errors.append(f"missing drawable {drawable}")

    # Manifest: referenced components and resources exist.
    manifest_path = os.path.join(MAIN, "AndroidManifest.xml")
    ET.parse(manifest_path)
    with open(manifest_path, encoding="utf-8") as fh:
        manifest = fh.read()
    for match in re.findall(r'android:name="\.([A-Za-z0-9_.]+)"', manifest):
        class_path = os.path.join(JAVA, *match.split(".")) + ".kt"
        if not os.path.exists(class_path):
            if match in {"MainActivity", "OneMessagesApp"}:
                continue
            errors.append(f"manifest references missing class: .{match}")
    for res_ref in re.findall(r'@(?:drawable|mipmap|xml|style|color|string)/([A-Za-z0-9_]+)', manifest):
        pass
    for theme in re.findall(r'@style/([A-Za-z0-9_.]+)', manifest):
        if theme not in defined_styles:
            errors.append(f"manifest references missing style: {theme}")
    for drawable in re.findall(r'@drawable/([A-Za-z0-9_]+)', manifest):
        if drawable not in defined_drawables:
            errors.append(f"manifest references missing drawable: {drawable}")
    for mipmap in re.findall(r'@mipmap/([A-Za-z0-9_]+)', manifest):
        if mipmap not in defined_mipmaps:
            errors.append(f"manifest references missing mipmap: {mipmap}")
    if "android.permission.INTERNET" in manifest:
        errors.append("INTERNET permission must not be declared (privacy promise)")

    # Package declaration must match directory structure.
    for path in kotlin_files:
        rel = os.path.relpath(path, JAVA)
        expected_pkg = "com.onemessages.app." + os.path.dirname(rel).replace(os.sep, ".")
        expected_pkg = expected_pkg.rstrip(".")
        with open(path, encoding="utf-8") as fh:
            src = fh.read()
        match = re.search(r"^package\s+([\w.]+)", src, re.MULTILINE)
        if not match:
            errors.append(f"no package declaration: {rel}")
            continue
        if match.group(1) != expected_pkg:
            errors.append(f"package mismatch: {rel} declares {match.group(1)} (expected {expected_pkg})")
        # Balance check (rough, ignores strings/comments imperfectly but catches gross errors)
        if src.count("{") != src.count("}"):
            errors.append(f"unbalanced braces in {rel}: {src.count('{')} vs {src.count('}')}")
        if re.search(r"\bTODO\b", src):
            warnings.append(f"TODO left in {rel}")

    # Build files
    for required in ("settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle/libs.versions.toml"):
        if not os.path.exists(os.path.join(ROOT, required)):
            errors.append(f"missing build file {required}")

    print(f"Kotlin files: {len(kotlin_files)}")
    print(f"String resources: {len(en_strings)} (am: {len(am_strings)})")
    print(f"String references: {len(ref_strings)}, plurals: {len(ref_plurals)}")
    for warning in warnings:
        print("WARN:", warning)
    if errors:
        print("\nERRORS:")
        for error in errors:
            print(" -", error)
        sys.exit(1)
    print("OK: static checks passed")


if __name__ == "__main__":
    main()
