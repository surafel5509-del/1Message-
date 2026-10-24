#!/usr/bin/env python3
"""Check that project types used in each file are either declared in that file, in the same
package, or imported. Catches the most common 'unresolved reference' mistake without a
compiler."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")

files = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if name.endswith(".kt"):
            files.append(os.path.join(dirpath, name))

declared = {}      # type name -> set of fully qualified names
per_file = {}      # path -> (package, imports, declared names, source)
for path in files:
    src = open(path, encoding="utf-8").read()
    pkg = re.search(r"^package\s+([\w.]+)", src, re.MULTILINE).group(1)
    imports = {m.group(1) for m in re.finditer(r"^import\s+([\w.]+)", src, re.MULTILINE)}
    names = set()
    for regex in (
        r"^(?:@\w+[^\n]*\n\s*)*(?:internal |private |public |abstract |open |sealed |data |value |enum |annotation )*(?:class|interface|object|enum class)\s+([A-Za-z_][A-Za-z0-9_]*)",
        r"^(?:internal |private |public |suspend |inline |operator )*fun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.<>?, ]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(",
        r"^(?:internal |private |public |const )*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)",
    ):
        for m in re.finditer(regex, src, re.MULTILINE):
            names.add(m.group(1))
    per_file[path] = (pkg, imports, names, src)
    for name in names:
        declared.setdefault(name, set()).add(f"{pkg}.{name}")

# Also record member declarations of classes (properties) so `state.field` style access can be
# checked separately; here we only care about type names.

IGNORED = {"R", "Unit", "Context", "Activity", "Intent", "Uri", "Bundle", "Build", "Log"}

errors = []
for path, (pkg, imports, names, src) in per_file.items():
    rel = os.path.relpath(path, ROOT)
    body = re.sub(r"^(?:package|import).*$", "", src, flags=re.MULTILINE)
    body = re.sub(r"//[^\n]*", "", body)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r'"(?:[^"\\]|\\.)*"', '""', body)  # string literals are not references
    fq_used = {m.group(1) for m in re.finditer(r"(?<![\w.])(?:[a-z_]\w*\.)+([A-Z]\w*)", body)}
    body = re.sub(r"(?<![\w.])(?:[a-z_]\w*\.)+[A-Z]\w*", " ", body)
    used = set(re.findall(r"\b([A-Z][A-Za-z0-9_]{2,})\b", body))
    for name in used:
        fqns = declared.get(name)
        if not fqns:
            continue
        if name in names or name in fq_used:
            continue
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", name):
            continue  # enum constants / const names
        if any(imp.endswith("." + name) for imp in imports):
            continue  # resolved by a library import
        if name in IGNORED:
            continue
        home = f"{pkg}.{name}"
        if home in fqns:
            continue
        if any(imp == fqn or imp.endswith("." + name) for imp in imports for fqn in fqns):
            continue
        if any(imp.endswith(".*") and imp.rsplit(".", 1)[0] == fqn.rsplit(".", 1)[0] for imp in imports for fqn in fqns):
            continue
        errors.append(f"{rel}: uses {name} ({', '.join(sorted(fqns))}) without import")

# Import targets must exist inside the project.
for path, (pkg, imports, names, src) in per_file.items():
    rel = os.path.relpath(path, ROOT)
    for imp in imports:
        if not imp.startswith("com.onemessages.app"):
            continue
        simple = imp.rsplit(".", 1)[-1]
        if simple == "*":
            continue
        if simple in ("R", "BuildConfig"):
            continue
        if not declared.get(simple) or imp not in declared[simple]:
            errors.append(f"{rel}: import {imp} does not resolve to a declared type")

for error in sorted(set(errors)):
    print(error)
print(f"\n{len(set(errors))} import issues")
sys.exit(1 if errors else 0)
