#!/usr/bin/env python3
"""Find `when` blocks over project enums that neither list every entry nor have an `else`."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/onemessages/app")

enums = {}
files = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if name.endswith(".kt"):
            files.append(os.path.join(dirpath, name))

for path in files:
    src = open(path, encoding="utf-8").read()
    for m in re.finditer(r"enum class (\w+)", src):
        brace = src.find("{", m.end())
        if brace < 0:
            continue
        depth, i = 0, brace
        while i < len(src):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = src[brace:i]
        body = body.split(";")[0]
        entries = [
            e.strip().split("(")[0].strip()
            for e in re.split(r"[,\n]", body)
            if re.match(r"^\s*[A-Z][A-Z0-9_]*\s*(?:\(.*)?$", e.strip()) and e.strip()
        ]
        if entries:
            enums[m.group(1)] = set(entries)

problems = []
for path in files:
    src = open(path, encoding="utf-8").read()
    src = re.sub(r"//[^\n]*", "", src)
    for m in re.finditer(r"\bwhen\s*(?:\([^)]*\))?\s*\{", src):
        start = m.end() - 1
        depth, i = 0, start
        while i < len(src):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        block = src[start:i]
        if re.search(r"^\s*else\s*->", block, re.MULTILINE):
            continue
        for enum, entries in enums.items():
            used = set(re.findall(r"\b" + enum + r"\.([A-Z][A-Z0-9_]*)", block))
            if len(used) < 2:
                continue
            missing = entries - used
            if missing:
                line = src[: m.start()].count("\n") + 1
                problems.append(
                    f"{os.path.relpath(path, ROOT)}:{line}: when over {enum} misses {sorted(missing)}"
                )

for p in sorted(set(problems)):
    print(p)
print(f"\n{len(set(problems))} non-exhaustive when blocks")
sys.exit(1 if problems else 0)
