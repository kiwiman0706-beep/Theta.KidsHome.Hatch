#!/usr/bin/env python3
"""
もじ れんしゅう の「よみ・ことば・かきじゅん」データ を 作る。
  python3 tools/build_kanji_info.py <jamdict.db> <kanjivg の kanji フォルダ>
  - jamdict.db：pip download jamdict-data で とれる（KANJIDIC2・JMdict、EDRDG、CC BY-SA 4.0）
  - KanjiVG：https://github.com/KanjiVG/kanjivg/releases の *-main.zip（CC BY-SA 3.0）
できる もの：app/src/main/assets/moji/kanji_info.json
  { "山": {"s": 3, "on": ["サン"], "kun": ["やま"], "w": [["火山","かざん"],…], "p": ["M…", …]}, "あ": {"p": […]}, … }
"""
import json, os, re, sqlite3, sys

db, kvg = sys.argv[1], sys.argv[2]
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "moji")
grades = json.load(open(os.path.join(root, "kyoiku_kanji.json"), encoding="utf-8"))["grades"]
grade = {ch: i + 1 for i, g in enumerate(grades) for ch in g}

HIRA = "".join(chr(c) for c in range(0x3041, 0x3097))
KATA = "".join(chr(c) for c in range(0x30A1, 0x30FB)) + "ー"
EXTRA = "０１２３４５６７８９0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"


def is_kana(ch): return ch in HIRA or ch in KATA


def paths(ch):
    f = os.path.join(kvg, "%05x.svg" % ord(ch))
    if not os.path.exists(f): return None
    svg = open(f, encoding="utf-8").read()
    ps = re.findall(r'<path id="kvg:[0-9a-f]+-s(\d+)"[^>]*?\sd="([^"]+)"', svg)
    ps.sort(key=lambda t: int(t[0]))
    return [re.sub(r"\s+", " ", d.strip()) for _, d in ps]


c = sqlite3.connect(db)


def readings(ch):
    rows = c.execute("""select r.r_type, r.value from reading r join rm_group g on r.gid = g.ID
                        join character k on g.cid = k.ID where k.literal = ? and r.r_type in ('ja_on','ja_kun')""", (ch,)).fetchall()
    on = [v for t, v in rows if t == "ja_on"][:3]
    kun = []
    for t, v in rows:
        if t != "ja_kun" or v.startswith("-") or v.endswith("-"): continue
        v = v.replace(".", "(", 1) + (")" if "." in v else "")
        if v not in kun: kun.append(v)
    return on, kun[:4]


def strokes(ch):
    r = c.execute("select stroke_count from character where literal = ?", (ch,)).fetchone()
    return int(r[0]) if r and r[0] else None


PRI = ("ichi1", "news1", "spec1", "spec2", "ichi2", "news2")


def words(ch, g):
    rows = c.execute("""select k.ID, k.idseq, k.text from Kanji k where k.text like ? and length(k.text) between 1 and 3""", ("%" + ch + "%",)).fetchall()
    cand = []
    for kid, seq, text in rows:
        if any(not (x == ch or is_kana(x) or grade.get(x, 9) <= g) for x in text): continue
        pri = [p for (p,) in c.execute("select text from KJP where kid = ?", (kid,))]
        score = sum(3 if p in PRI[:3] else 1 for p in pri if p in PRI)
        if score == 0: continue
        kana = c.execute("""select a.text from Kana a left join KNP p on p.kid = a.ID where a.idseq = ?
                            group by a.ID order by count(p.text) desc, a.ID limit 1""", (seq,)).fetchone()
        if not kana: continue
        cand.append((-score, len(text), text, kana[0]))
    out, seen = [], set()
    for _, _, t, k in sorted(cand):
        if t in seen: continue
        seen.add(t); out.append([t, k])
        if len(out) >= 5: break
    return out


info = {}
for ch, g in grade.items():
    on, kun = readings(ch)
    e = {"s": strokes(ch), "on": on, "kun": kun, "w": words(ch, g)}
    p = paths(ch)
    if p: e["p"] = p; e["s"] = len(p)
    info[ch] = e
for ch in HIRA + KATA + EXTRA:
    p = paths(ch)
    if p: info[ch] = {"s": len(p), "p": p}

json.dump(info, open(os.path.join(root, "kanji_info.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(len(info), "chars", os.path.getsize(os.path.join(root, "kanji_info.json")) // 1024, "KB")
print(info["山"]); print(info["生"]["on"], info["生"]["kun"], info["生"]["w"])
