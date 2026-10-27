import json, sys, re, collections
import pdfplumber
pg = pdfplumber.open(sys.argv[1]).pages[2]
ws = pg.extract_words(extra_attrs=["size"])
# column blocks start x
starts = [238, 438, 638, 836, 1036]
starts = [s * 72 / 110 for s in starts]  # from 110dpi render to pt
blocks = collections.defaultdict(list)
for w in ws:
    if w["top"] < 40 or w["top"] > 540: continue
    if w["x0"] < starts[0] - 5: continue
    b = max(i for i, s in enumerate(starts) if w["x0"] >= s - 5)
    blocks[b].append(w)
entries = []
for b in sorted(blocks):
    lines = collections.defaultdict(list)
    cur = None
    for w in sorted(blocks[b], key=lambda w: w["top"]):
        if cur is None or w["top"] - cur > 3.5:
            cur = w["top"]
        lines[cur].append(w)
    pending = []
    for top in sorted(lines):
        ws_ = sorted(lines[top], key=lambda w: w["x0"])
        toks = [w["text"] for w in ws_]
        if len(toks) >= 3 and toks[-1] in ("A", "B") and re.fullmatch("[1-6]", toks[-2]) and re.fullmatch("[1-6]", toks[-3]):
            rel, name = toks[-3:], " ".join(toks[:-3])
        else:
            rel, name = [], " ".join(toks)
        if name in ("Straße",) or "Bioabfall" in name: continue
        if b == 4 and top > 175: continue  # Öffnungszeiten box
        if b in (2, 3) and top > 425: continue  # ad box + footer
        codes = rel
        if any(c in ("Bioabfall", "Restabfall,", "Gelber", "Papier", "Sack") for c in codes):
            continue
        pending.append(name)
        if codes:
            if len(codes) != 3: print("WARN", codes, pending, file=sys.stderr)
            entries.append({"name": " ".join(p for p in pending if p).replace("- ", "-"), "bio": codes[0], "rest": codes[1], "gelb": codes[2]})
            pending = []
    if pending: print("LEFTOVER", b, pending, file=sys.stderr)
json.dump(entries, sys.stdout, ensure_ascii=False, indent=0)
print(len(entries), file=sys.stderr)
