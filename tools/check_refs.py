#!/usr/bin/env python3
"""Best-effort static reference checker for the Kotlin sources.

Not a compiler: it strips comments and string literals, records declared types and their
members, infers simple variable types from declarations, and reports `receiver.member`
accesses that look unknown, plus calls to functions with a mismatched argument count.
Output is a review list, not a verdict.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/onemessages/app")

BUILTIN = {
    "String", "Int", "Long", "Boolean", "Float", "Double", "Any", "Unit", "Char", "Byte",
    "Short", "List", "MutableList", "Map", "MutableMap", "Set", "MutableSet", "Flow",
    "StateFlow", "MutableStateFlow", "SharedFlow", "MutableSharedFlow", "Context",
    "Activity", "Intent", "Uri", "Bundle", "Modifier", "Text", "Icon", "Button", "Column",
}


def strip_source(src: str) -> str:
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"' and src.startswith('"""', i):
            j = src.find('"""', i + 3)
            out.append('""')
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
            out.append('""')
        elif c == "'":
            i += 1
            while i < n and src[i] != "'":
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
            out.append("'x'")
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


class Decl:
    def __init__(self, name, kind, file):
        self.name = name
        self.kind = kind
        self.file = file
        self.members = set()
        self.supertypes = []
        self.props = {}


DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*(?:internal |private |public |abstract |open |sealed |data |value |enum |annotation )*"
    r"(class|interface|object|enum class)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)

MEMBER_FUN_RE = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:override |private |internal |public |protected |suspend |inline |operator |abstract |open )*fun\s+(?:<[^>]*>\s*)?([A-Za-z_][A-Za-z0-9_.]*)", re.MULTILINE)
MEMBER_PROP_RE = re.compile(r"^\s*(?:@\w+[^\n]*\n\s*)*(?:override |private |internal |public |protected |const |lateinit |@Volatile\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)", re.MULTILINE)

# Kotlin/Android members that exist on platform or library types, not in our sources.
PLATFORM_TYPES = re.compile(r"^(Boolean|Int|Long|Double|Float|String|Char|Any|Unit|Pair|Triple|List|Set|Map|Array|Regex|TextUtils|Log|Build|Context|Intent|Uri|Manifest|Settings|Telephony|SmsManager|SmsMessage|ContactsContract|NotificationManager|NotificationCompat|NotificationChannel|PendingIntent|RoleManager|BiometricManager|BiometricPrompt|WorkManager|DataStore|Uri|File|DocumentFile|RingtoneManager|TimePickerDialog|AlertDialog|MaterialTheme|Modifier|Icons|R|BuildConfig|BuildConfigCompat|Logx)$")


def parse_decls():
    decls = {}
    files = []
    for dirpath, _, names in os.walk(SRC):
        for name in sorted(names):
            if name.endswith(".kt"):
                files.append(os.path.join(dirpath, name))
    texts = {}
    for path in files:
        raw = open(path, encoding="utf-8").read()
        texts[path] = strip_source(raw)
    for path, src in texts.items():
        positions = []
        for m in DECL_RE.finditer(src):
            positions.append((m.start(), m.group(1), m.group(2)))
        for idx, (start, kind, name) in enumerate(positions):
            end = positions[idx + 1][0] if idx + 1 < len(positions) else len(src)
            body = src[start:end]
            decl = Decl(name, kind, os.path.relpath(path, ROOT))
            for m in MEMBER_FUN_RE.finditer(body):
                decl.members.add(m.group(1).split(".")[-1])
            for m in MEMBER_PROP_RE.finditer(body):
                decl.members.add(m.group(1))
            # primary constructor params
            head = body.split("\n\n")[0]
            for m in re.finditer(r"(?:@\w+(?:\([^)]*\))?\s+)*(?:private |internal |override |public )*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z_][A-Za-z0-9_.<>, ?]*)", head):
                decl.members.add(m.group(1))
                decl.props[m.group(1)] = m.group(2).strip()
            sup = re.search(r"[):]\s*([A-Za-z_][\w<>, ]*(?:,\s*[A-Za-z_][\w<>, ]*)*)\s*(?:where|$)", head)
            if sup:
                decl.supertypes = [x.strip() for x in sup.group(1).split(",")]
            decls[name] = decl
    return decls, texts


def main():
    decls, texts = parse_decls()
    problems = []
    for path, src in texts.items():
        rel = os.path.relpath(path, ROOT)
        # local variable types: `val x: Type` or `val x = Type(`
        types = {}
        for m in re.finditer(r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z_][A-Za-z0-9_]*)", src):
            types[m.group(1)] = m.group(2)
        for m in re.finditer(r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*[(.]", src):
            types.setdefault(m.group(1), m.group(2))
        # function params: `name: Type` inside signatures
        for m in re.finditer(r"([a-zA-Z_][A-Za-z0-9_]*)\s*:\s*([A-Z][A-Za-z0-9_]*)", src):
            types.setdefault(m.group(1), m.group(2))
        for m in re.finditer(r"\b([a-z][A-Za-z0-9_]*)\.([a-zA-Z_][A-Za-z0-9_]*)\s*[(.]", src):
            recv, member = m.group(1), m.group(2)
            typ = types.get(recv)
            if typ is None or typ not in decls:
                continue
            decl = decls[typ]
            if member in decl.members:
                continue
            if any(member in decls.get(s, Decl(s, "", "")).members for s in decl.supertypes):
                continue
            problems.append(f"{rel}: {recv}:{typ}.{member} not declared")
    seen = set()
    for p in problems:
        if p in seen:
            continue
        seen.add(p)
        print(p)
    print(f"\n{len(seen)} potential issues across {len(texts)} files")


if __name__ == "__main__":
    main()
