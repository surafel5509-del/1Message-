#!/usr/bin/env python3
"""Catch the classic Compose 'unresolved reference' misses: a well-known symbol is used but no
import ending in that symbol exists (and the file is not in the same package as it)."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/onemessages/app")

# symbols that must be imported unless declared in the same file
SYMBOLS = {
    "remember": r"\bremember\s*[({]",
    "mutableStateOf": r"\bmutableStateOf\s*\(",
    "rememberSaveable": r"\brememberSaveable\s*[({]",
    "getValue": r"\bby\s+\w+",
    "setValue": r"\bvar\s+\w+\s+by\s+",
    "LaunchedEffect": r"\bLaunchedEffect\s*\(",
    "DisposableEffect": r"\bDisposableEffect\s*\(",
    "derivedStateOf": r"\bderivedStateOf\s*[({]",
    "collectAsState": r"\bcollectAsState\s*\(",
    "stringResource": r"\bstringResource\s*\(",
    "pluralStringResource": r"\bpluralStringResource\s*\(",
    "dimensionResource": r"\bdimensionResource\s*\(",
    "LocalContext": r"\bLocalContext\b",
    "LocalConfiguration": r"\bLocalConfiguration\b",
    "LocalClipboardManager": r"\bLocalClipboardManager\b",
    "Text": r"\bText\s*\(",
    "Icon": r"\bIcon\s*\(",
    "Button": r"\bButton\s*[({]",
    "OutlinedButton": r"\bOutlinedButton\s*[({]",
    "TextButton": r"\bTextButton\s*[({]",
    "IconButton": r"\bIconButton\s*[({]",
    "FilledIconButton": r"\bFilledIconButton\s*[({]",
    "FilledTonalButton": r"\bFilledTonalButton\s*[({]",
    "OutlinedTextField": r"\bOutlinedTextField\s*\(",
    "TextField": r"\bTextField\s*\(",
    "Switch": r"\bSwitch\s*\(",
    "Checkbox": r"\bCheckbox\s*\(",
    "RadioButton": r"\bRadioButton\s*\(",
    "Slider": r"\bSlider\s*\(",
    "Card": r"\bCard\s*[({]",
    "Scaffold": r"\bScaffold\s*\(",
    "Surface": r"\bSurface\s*[({]",
    "LazyColumn": r"\bLazyColumn\s*\(",
    "LazyRow": r"\bLazyRow\s*\(",
    "items": r"\bitems\s*\(",
    "AlertDialog": r"\bAlertDialog\s*\(",
    "ModalBottomSheet": r"\bModalBottomSheet\s*\(",
    "TopAppBar": r"\bTopAppBar\s*\(",
    "HorizontalDivider": r"\bHorizontalDivider\s*\(",
    "CircularProgressIndicator": r"\bCircularProgressIndicator\s*\(",
    "LinearProgressIndicator": r"\bLinearProgressIndicator\s*\(",
    "DropdownMenu": r"\bDropdownMenu\s*\(",
    "DropdownMenuItem": r"\bDropdownMenuItem\s*\(",
    "Modifier": r"\bModifier\b",
    "dp": r"\d+\.?\d*\.dp\b",
    "sp": r"\d+\.?\d*\.sp\b",
    "Arrangement": r"\bArrangement\.",
    "Alignment": r"\bAlignment\.",
    "FontWeight": r"\bFontWeight\.",
    "TextAlign": r"\bTextAlign\.",
    "TextOverflow": r"\bTextOverflow\.",
    "ContentScale": r"\bContentScale\.",
    "ContentDescription": r"\bcontentDescription\b",
    "Color": r"\bColor\s*\(",
    "RoundedCornerShape": r"\bRoundedCornerShape\s*\(",
    "CircleShape": r"\bCircleShape\b",
    "MaterialTheme": r"\bMaterialTheme\.",
    "rememberCoroutineScope": r"\brememberCoroutineScope\s*\(",
    "rememberLauncherForActivityResult": r"\brememberLauncherForActivityResult\s*\(",
    "ActivityResultContracts": r"\bActivityResultContracts\.",
    "ExperimentalMaterial3Api": r"\bExperimentalMaterial3Api\b",
}

problems = []
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(dirpath, name)
        src = open(path, encoding="utf-8").read()
        if "compose" not in src and "androidx.compose" not in src:
            continue
        imports = set(re.findall(r"^import\s+([\w.]+)", src, re.MULTILINE))
        imported_names = {imp.rsplit(".", 1)[-1] for imp in imports}
        declared = set(re.findall(r"(?:class|interface|object|fun|val|var)\s+(\w+)", src))
        body = re.sub(r"^(?:import|package).*$", "", src, flags=re.MULTILINE)
        body = re.sub(r"//[^\n]*", "", body)
        for symbol, pattern in SYMBOLS.items():
            if not re.search(pattern, body):
                continue
            if symbol in imported_names or symbol in declared:
                continue
            if symbol == "getValue":
                if not re.search(r"\bval\s+\w+\s+by\s+", body):
                    continue
            if symbol == "setValue":
                if not re.search(r"\bvar\s+\w+\s+by\s+", body):
                    continue
            if symbol == "ContentDescription":
                continue
            if re.search(r"androidx\.[\w.]*\b" + symbol + r"\b", body):
                continue  # used fully qualified
            problems.append(f"{os.path.relpath(path, ROOT)}: {symbol} used without an import")

for p in sorted(set(problems)):
    print(p)
print(f"\n{len(set(problems))} missing Compose imports")
sys.exit(1 if problems else 0)
