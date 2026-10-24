#!/usr/bin/env python3
"""Verify `ProjectObject.member` references exist (e.g. PhoneNumbers.digitsOnly, Logx.w)."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/onemessages/app")

files = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if name.endswith(".kt"):
            files.append(os.path.join(dirpath, name))


def strip(src):
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return src


objects = {}
for path in files:
    src = strip(open(path, encoding="utf-8").read())
    for m in re.finditer(r"\bobject (\w+)\s*(?::\s*[\w<>., ]+)?\s*\{", src):
        name = m.group(1)
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
        body = src[start:i]
        members = set(re.findall(r"\bfun\s+(?:<[^>]*>\s*)?(\w+)\s*\(", body))
        members |= set(re.findall(r"\b(?:val|var|const val)\s+(\w+)\s*[:=]", body))
        members |= set(re.findall(r"\bconst val (\w+)", body))
        members |= set(re.findall(r"\b(?:data )?class (\w+)", body))
        members |= set(re.findall(r"\b(?:enum )?class (\w+)", body))
        members |= set(re.findall(r"\binterface (\w+)", body))
        objects[name] = members

problems = []
for path in files:
    src = strip(open(path, encoding="utf-8").read())
    rel = os.path.relpath(path, ROOT)
    own = set(re.findall(r"\bobject (\w+)", src))
    for m in re.finditer(r"(?<![.\w])([A-Z]\w+)\.(\w+)", src):
        obj, member = m.group(1), m.group(2)
        if obj in own or obj not in objects:
            continue
        if member in objects[obj]:
            continue
        if re.match(r"^[A-Z_]+$", member):
            continue  # nested enum/const access such as Type.CONST
        problems.append(f"{rel}: {obj}.{member} is not declared in object {obj}")

for p in sorted(set(problems)):
    print(p)
print(f"\n{len(set(problems))} unresolved object references")
sys.exit(1 if problems else 0)
