#!/usr/bin/env python3
"""
都道府県パズル の 地図 を 作る。
  curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson
  python3 tools/build_japan_map.py ne_10m_admin_1_states_provinces.geojson
Natural Earth（パブリックドメイン）。遠い 島 は はぶき、沖縄 は 左上 の わく に うつす。
できる もの：app/src/main/assets/japan/map.json
  {"w":1000,"h":…,"inset":[x,y,w,h],"prefs":[{"code":1,"polys":[[x,y,x,y,…],…],"cx":…,"cy":…}, …]}
"""
import json, math, os, sys

src = json.load(open(sys.argv[1], encoding="utf-8"))
feats = [f for f in src["features"] if f["properties"].get("adm0_a3") == "JPN"]
K = math.cos(math.radians(36))
OKI_SHIFT = (4.0, 15.6)  # 沖縄本島 を 日本海 の 左上 へ


def area(r):
    return abs(sum(r[i][0] * r[i - 1][1] - r[i - 1][0] * r[i][1] for i in range(len(r)))) / 2


def keep(code, ring):
    lons = [p[0] for p in ring]; lats = [p[1] for p in ring]
    lo, la = sum(lons) / len(lons), sum(lats) / len(lats)
    if code == 13 and la < 34.9: return False          # 伊豆・小笠原
    if code == 46 and la < 29.9: return False          # 奄美
    if code == 47 and not (126.5 < lo < 128.5 and la > 26): return False  # 沖縄本島 と まわり だけ
    return True


def dp(pts, eps):
    if len(pts) < 3: return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    n = math.hypot(dx, dy) or 1e-9
    best, idx = 0, 0
    for i in range(1, len(pts) - 1):
        d = abs(dy * pts[i][0] - dx * pts[i][1] + x2 * y1 - y2 * x1) / n
        if d > best: best, idx = d, i
    if best > eps:
        return dp(pts[: idx + 1], eps)[:-1] + dp(pts[idx:], eps)
    return [pts[0], pts[-1]]


prefs = []
for f in feats:
    code = int(f["properties"]["iso_3166_2"].split("-")[1])
    g = f["geometry"]
    polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
    rings = [p[0] for p in polys if keep(code, p[0])]
    big = max(area(r) for r in rings)
    rings = [r for r in rings if area(r) > big * 0.004 or area(r) > 0.004]
    out = []
    for r in rings:
        if code == 47: r = [(x + OKI_SHIFT[0], y + OKI_SHIFT[1]) for x, y in r]
        pts = [(x * K, -y) for x, y in r]
        # とじた 輪 は 2 つ に わけて 間引く
        m = len(pts) // 2
        s = dp(pts[: m + 1], 0.006)[:-1] + dp(pts[m:], 0.006)
        if len(s) >= 4: out.append(s)
    prefs.append({"code": code, "rings": out})

xs = [x for p in prefs for r in p["rings"] for x, _ in r]; ys = [y for p in prefs for r in p["rings"] for _, y in r]
x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
pad = 0.02 * (x1 - x0)
S = 1000 / (x1 - x0 + 2 * pad)
def tr(x, y): return round((x - x0 + pad) * S, 1), round((y - y0 + pad) * S, 1)

res = []
for p in sorted(prefs, key=lambda p: p["code"]):
    polys = [[c for x, y in r for c in tr(x, y)] for r in p["rings"]]
    # まんなか：いちばん 大きい 島 の 重心
    r = max(p["rings"], key=area)
    cx = sum(x for x, _ in r) / len(r); cy = sum(y for _, y in r) / len(r)
    res.append({"code": p["code"], "polys": polys, "cx": tr(cx, cy)[0], "cy": tr(cx, cy)[1]})

oki = [v for p in res if p["code"] == 47 for poly in p["polys"] for v in poly]
ox, oy = oki[0::2], oki[1::2]
inset = [round(min(ox) - 25, 1), round(min(oy) - 25, 1), round(max(ox) - min(ox) + 50, 1), round(max(oy) - min(oy) + 50, 1)]
h = round((y1 - y0 + 2 * pad) * S, 1)
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "japan", "map.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump({"w": 1000, "h": h, "inset": inset, "prefs": res}, open(dst, "w"), separators=(",", ":"))
print("h", h, "points", sum(len(poly) // 2 for p in res for poly in p["polys"]), os.path.getsize(dst) // 1024, "KB", "inset", inset)
