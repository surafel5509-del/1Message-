#!/usr/bin/env python3
"""Check that stringResource()/pluralStringResource() calls pass the number of format
arguments the resource declares. A mismatch is a runtime crash, not a compile error, so it is
worth catching statically."""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
SRC = os.path.join(ROOT, "app/src/main/java")

strings = {}
plurals = {}
for node in ET.parse(RES).getroot():
    if node.tag == "string" and node.text:
        strings[node.attrib["name"]] = 0 if node.attrib.get("formatted") == "false" else node.text
    elif node.tag == "plurals":
        plurals[node.attrib["name"]] = [item.text or "" for item in node]


def arg_count(fmt: str) -> int:
    """Number of format specifiers, ignoring %% and positional duplicates."""
    positions = set()
    plain = 0
    for m in re.finditer(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])", fmt or ""):
        if m.group(2) == "%":
            continue
        if m.group(1):
            positions.add(int(m.group(1)))
        else:
            plain += 1
    return max(len(positions), plain)


def split_args(text: str):
    """Split a Kotlin call argument list, respecting nested parens/strings."""
    args, depth, current, in_string = [], 0, "", False
    i = 0
    while i < len(text):
        c = text[i]
        if c == '"' and (i == 0 or text[i - 1] != "\\"):
            in_string = not in_string
        if not in_string:
            if c in "([{":
                depth += 1
            elif c in ")]}":
                if depth == 0:
                    break
                depth -= 1
            elif c == "," and depth == 0:
                args.append(current.strip())
                current = ""
                i += 1
                continue
        current += c
        i += 1
    if current.strip():
        args.append(current.strip())
    return args


problems = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(dirpath, name)
        src = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, ROOT)
        for m in re.finditer(r"pluralStringResource\(\s*R\.plurals\.(\w+)\s*,", src):
            rest = src[m.end():].lstrip()
            rest = rest[1:] if rest.startswith(",") else rest
            args = split_args(rest)
            # first arg after the resource is the quantity
            resource_args = max(arg_count(a) for a in plurals.get(m.group(1), [""]))
            if len(args) < 1 + resource_args:
                problems.append(
                    f"{rel}: plurals.{m.group(1)} needs {resource_args} arg(s), call has {max(len(args)-1, 0)}"
                )
        for m in re.finditer(r"stringResource\(\s*R\.string\.(\w+)", src):
            key = m.group(1)
            if key not in strings:
                continue
            text = strings[key]
            if text == 0:
                continue
            needed = arg_count(text)
            rest = src[m.end():].lstrip()
            rest = rest[1:] if rest.startswith(",") else rest
            args = split_args(rest)
            given = len(args)
            if needed != given:
                problems.append(f"{rel}: R.string.{key} needs {needed} arg(s), call has {given}")

for p in sorted(set(problems)):
    print(p)
print(f"\n{len(set(problems))} argument mismatches")
sys.exit(1 if problems else 0)
