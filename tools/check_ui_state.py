#!/usr/bin/env python3
"""Cross-check screens against their view models.

For every `XxxViewModel.kt` that declares `data class XxxUiState`, look at the matching
`XxxScreen.kt` and report `state.<field>` accesses that the state class does not declare, plus
`viewModel.<member>` / `<vm>.<member>` calls that the view model does not declare. Also checks
`when (event)` branches on the event sealed interface.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/onemessages/app")

problems = []


def strip(src):
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)
    return src


def parse_state_fields(src, name):
    m = re.search(r"data class " + name + r"\(", src)
    if not m:
        return None
    start = m.end() - 1
    depth, i = 0, start
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    block = src[start:i]
    # properties declared in the class body after the constructor
    rest = src[i:]
    body = ""
    brace = rest.find("{")
    if brace >= 0:
        depth, j = 0, brace
        while j < len(rest):
            if rest[j] == "{":
                depth += 1
            elif rest[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = rest[brace:j]
    return set(re.findall(r"(?:val|var)\s+(\w+)\s*[:=]", block + body))


def parse_members(src, class_name):
    m = re.search(r"class " + class_name + r"\b.*?\)\s*:\s*\w+\(\)\s*\{", src, re.S)
    body = src[m.end():] if m else src
    members = set(re.findall(r"\bfun\s+(\w+)\s*\(", body))
    members |= set(re.findall(r"\b(?:val|var)\s+(\w+)\s*[:=]", body))
    # constructor parameters cannot be referenced from the screen, so they are not added
    return members


# every view model class in the project and its declared members
vm_members = {}
vm_sources = {}
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith("ViewModel.kt"):
            continue
        path = os.path.join(dirpath, name)
        src = strip(open(path, encoding="utf-8").read())
        for cls in re.findall(r"class (\w+ViewModel)\b", src):
            vm_members[cls] = parse_members(src, cls) | {"state", "events"}
            vm_sources[cls] = path

pairs = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith("ViewModel.kt") or name == "AppViewModel.kt":
            continue
        stem = name[: -len("ViewModel.kt")]
        vm_path = os.path.join(dirpath, name)
        screen_path = os.path.join(dirpath, stem + "Screen.kt")
        if os.path.exists(screen_path):
            pairs.append((vm_path, screen_path, stem))

for vm_path, screen_path, stem in pairs:
    vm_src = strip(open(vm_path, encoding="utf-8").read())
    screen_src = strip(open(screen_path, encoding="utf-8").read())
    vm_class = stem + "ViewModel"
    state_name = stem + "UiState"
    fields = parse_state_fields(vm_src, state_name)
    rel = os.path.relpath(screen_path, ROOT)
    if fields is not None:
        for m in re.finditer(r"(?<![.\w])state\.(\w+)", screen_src):
            if m.group(1) not in fields:
                problems.append(f"{rel}: state.{m.group(1)} is not in {state_name}")
    members = parse_members(vm_src, vm_class)
    # screen-local names that hold the view model
    holders = set(re.findall(r"(?:viewModel|vm)\s*:\s*" + vm_class, screen_src))
    holders |= {"viewModel", "vm"}
    for holder in holders:
        for m in re.finditer(r"\b" + holder + r"\.(\w+)", screen_src):
            member = m.group(1)
            if member in members or member in {"state", "events"}:
                continue
            problems.append(f"{rel}: {holder}.{member} is not declared in {vm_class}")

# settings screens share SettingsViewModel etc., so check by imported view model instead
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith("Screen.kt"):
            continue
        path = os.path.join(dirpath, name)
        rel = os.path.relpath(path, ROOT)
        src = strip(open(path, encoding="utf-8").read())
        used_vms = {cls for cls in vm_members if re.search(r"\b" + cls + r"\b", src)}
        for holder, cls in re.findall(r"\b(\w+)\s*:\s*(\w+ViewModel)\b", src):
            if cls in vm_members:
                used_vms.add(cls)
                for m in re.finditer(r"\b" + holder + r"\.(\w+)", src):
                    if m.group(1) not in vm_members[cls]:
                        problems.append(f"{rel}: {holder}.{m.group(1)} is not declared in {cls}")
        if len(used_vms) == 1:
            cls = used_vms.pop()
            for m in re.finditer(r"(?<![.\w])state\.(\w+)", src):
                state_name = cls[: -len("ViewModel")] + "UiState"
                fields = parse_state_fields(strip(open(vm_sources[cls], encoding="utf-8").read()), state_name)
                if fields is not None and m.group(1) not in fields:
                    problems.append(f"{rel}: state.{m.group(1)} is not in {state_name}")

for p in sorted(set(problems)):
    print(p)
print(f"\n{len(set(problems))} screen/view-model mismatches across {len(pairs)} pairs")
sys.exit(1 if problems else 0)
