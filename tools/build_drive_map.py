#!/usr/bin/env python3
"""
ドライブ マップ（🚗 いま どこ？）の データ を 作る：近畿 の 高速道路 の 路線図 と IC・JCT・SA・PA、県 の 形。

  python3 tools/build_drive_map.py ne_10m_admin_1_states_provinces.geojson [overpass.json]

- 高速道路：OpenStreetMap（© OpenStreetMap contributors、ODbL）を Overpass API で とる。
  overpass.json を わたせば それ を 読む（ネット に つながらない とき）。
- 県 の 形：Natural Earth（パブリックドメイン）。
  curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson
- できる もの：app/src/main/assets/drive/kinki.json
  {"bbox":[s,w,n,e], "routes":[{"name","ref","city","lines":[[lat,lon,lat,lon,…],…]}],
   "points":[{"name","kind":"ic|jct|sa|pa","lat","lon","routes":[番号,…]}], "prefs":[{"code","polys":[[lat,lon,…],…]}]}
- 模式図 なので 線 は 間引く（だいたい 150m）。上り・下り が 2 本 に なる ところ も ある が 気に しない。
GitHub Actions の「drive-map」で 作りなおせる（.github/workflows/drive-map.yml）。
"""
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request

BBOX = (33.40, 134.20, 35.85, 136.95)  # 南・西・北・東（近畿 2 府 5 県 と すこし まわり）
KINKI = {24, 25, 26, 27, 28, 29, 30}
NEIGHBORS = {18, 21, 23, 31, 33, 36, 37}  # 福井・岐阜・愛知・鳥取・岡山・徳島・香川（うすく 出す）
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "drive", "kinki.json")
ROAD_NAME = re.compile(r"自動車道|高速|有料道路|道路|バイパス|ライン|ウェイ")


def query():
    s, w, n, e = BBOX
    b = f"({s},{w},{n},{e})"
    return f"""[out:json][timeout:600];
(
  way["highway"="motorway"]{b};
  way["highway"="trunk"]["motorroad"="yes"]{b};
);
out tags geom;
node["highway"="motorway_junction"]{b};
out body;
(
  node["highway"~"^(services|rest_area)$"]{b};
  way["highway"~"^(services|rest_area)$"]{b};
);
out center;
"""


def fetch():
    data = urllib.parse.urlencode({"data": query()}).encode()
    last = None
    for url in ENDPOINTS * 2:
        try:
            print("overpass:", url, flush=True)
            req = urllib.request.Request(url, data=data, headers={"User-Agent": "ThetaKidsHome-drive-map/1.0"})
            with urllib.request.urlopen(req, timeout=900) as r:
                return json.load(r)
        except Exception as ex:  # 混んで いたら つぎ へ
            last = ex
            print("  だめ:", ex, flush=True)
            time.sleep(20)
    raise SystemExit(f"Overpass に つながりません：{last}")


def dp(pts, eps):
    """Douglas-Peucker（pts は (x, y)）"""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        (x1, y1), (x2, y2) = pts[a], pts[b]
        dx, dy = x2 - x1, y2 - y1
        n = math.hypot(dx, dy) or 1e-12
        best, idx = 0.0, -1
        for i in range(a + 1, b):
            d = abs(dy * pts[i][0] - dx * pts[i][1] + x2 * y1 - y2 * x1) / n
            if d > best:
                best, idx = d, i
        if best > eps and idx > 0:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(pts, keep) if k]


K = math.cos(math.radians(34.7))


def xy(lat, lon):
    """だいたい km（近畿 の まんなか で）"""
    return lon * 111.32 * K, lat * 110.57


def seg_dist(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / l2))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def route_key(tags):
    name = (tags.get("name") or "").strip()
    ref = (tags.get("ref") or "").strip()
    if tags.get("highway") == "trunk" and not ROAD_NAME.search(name):
        return None
    if not name:
        return None
    # 「名神高速道路;新名神…」の よう な もの は さいしょ だけ
    return name.split(";")[0].strip(), ref.split(";")[0].strip()


def kind_of(name, hw):
    if re.search(r"JCT|ジャンクション", name):
        return "jct"
    if re.search(r"SA$|サービスエリア", name):
        return "sa"
    if re.search(r"PA$|パーキングエリア", name):
        return "pa"
    if hw == "services":
        return "sa"
    if hw == "rest_area":
        return "pa"
    return "ic"


def build_routes(elements):
    routes = {}
    for el in elements:
        if el.get("type") != "way" or "geometry" not in el:
            continue
        tags = el.get("tags", {})
        if tags.get("highway") not in ("motorway", "trunk"):
            continue
        key = route_key(tags)
        if not key:
            continue
        r = routes.setdefault(key[0], {"name": key[0], "ref": key[1], "ways": []})
        if not r["ref"] and key[1]:
            r["ref"] = key[1]
        r["ways"].append([(g["lat"], g["lon"]) for g in el["geometry"]])
    out = []
    for r in routes.values():
        lines, km = [], 0.0
        for w in join_ways(r["ways"]):
            pts = [xy(a, b) for a, b in w]
            km += sum(math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]) for i in range(1, len(pts)))
            idx = {id(p): i for i, p in enumerate(pts)}
            s = dp(pts, 0.15)
            keep = [w[idx[id(p)]] for p in s]
            if len(keep) >= 2:
                lines.append([v for a, b in keep for v in (round(a, 4), round(b, 4))])
        if km < 2 or not lines:  # みじかい 切れはし は すてる
            continue
        out.append({"name": r["name"], "ref": r["ref"], "city": r["name"].startswith(("阪神高速", "阪神高速道路")), "km": round(km / 2, 1), "lines": lines})
    out.sort(key=lambda r: (r["city"], -r["km"]))
    return out


def join_ways(ways):
    """はし が つながる way を つなげて 長い 線 に（間引き が よく きく）"""
    ways = [list(w) for w in ways if len(w) >= 2]
    by_start = {}
    for i, w in enumerate(ways):
        by_start.setdefault(w[0], []).append(i)
    used = [False] * len(ways)
    out = []
    for i, w in enumerate(ways):
        if used[i]:
            continue
        used[i] = True
        line = list(w)
        while True:
            nxt = next((j for j in by_start.get(line[-1], []) if not used[j]), None)
            if nxt is None:
                break
            used[nxt] = True
            line += ways[nxt][1:]
        out.append(line)
    return out


def build_points(elements, routes):
    raw = []
    for el in elements:
        tags = el.get("tags", {})
        hw = tags.get("highway")
        if hw not in ("motorway_junction", "services", "rest_area"):
            continue
        name = (tags.get("name") or "").strip()
        if not name:
            continue
        if "lat" in el:
            lat, lon = el["lat"], el["lon"]
        elif "center" in el:
            lat, lon = el["center"]["lat"], el["center"]["lon"]
        else:
            continue
        raw.append((name, kind_of(name, hw), lat, lon))
    # おなじ 名前 で 近い もの（出口 が いくつも ある IC・上り下り の SA）は 1 つ に
    groups = []
    for name, kind, lat, lon in raw:
        for g in groups:
            if g["name"] == name and math.hypot(*(a - b for a, b in zip(xy(lat, lon), xy(g["lat"], g["lon"])))) < 4:
                g["n"] += 1
                g["lat"] += (lat - g["lat"]) / g["n"]
                g["lon"] += (lon - g["lon"]) / g["n"]
                break
        else:
            groups.append({"name": name, "kind": kind, "lat": lat, "lon": lon, "n": 1})
    grid = {}  # 2km の ます → その ます に かかる 区間
    for i, r in enumerate(routes):
        for line in r["lines"]:
            pts = [xy(line[k], line[k + 1]) for k in range(0, len(line), 2)]
            for k in range(1, len(pts)):
                a, c = pts[k - 1], pts[k]
                for gx in range(int(min(a[0], c[0]) // 2) - 1, int(max(a[0], c[0]) // 2) + 2):
                    for gy in range(int(min(a[1], c[1]) // 2) - 1, int(max(a[1], c[1]) // 2) + 2):
                        grid.setdefault((gx, gy), []).append((i, a, c))
    out = []
    for g in groups:
        p = xy(g["lat"], g["lon"])
        near = sorted({i for i, a, b in grid.get((int(p[0] // 2), int(p[1] // 2)), []) if seg_dist(p, a, b) < 0.8})
        if not near:
            continue  # 高速 に のって いない もの（一般道 の 出口 など）
        out.append({"name": g["name"], "kind": g["kind"], "lat": round(g["lat"], 5), "lon": round(g["lon"], 5), "routes": near})
    return out


def build_prefs(ne_path):
    src = json.load(open(ne_path, encoding="utf-8"))
    out = []
    for f in src["features"]:
        p = f["properties"]
        if p.get("adm0_a3") != "JPN":
            continue
        code = int(p["iso_3166_2"].split("-")[1])
        if code not in KINKI | NEIGHBORS:
            continue
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        rings = []
        for poly in polys:
            ring = poly[0]
            lats = [c[1] for c in ring]; lons = [c[0] for c in ring]
            if max(lats) < BBOX[0] - 0.5 or min(lats) > BBOX[2] + 0.5 or max(lons) < BBOX[1] - 0.5 or min(lons) > BBOX[3] + 0.5:
                continue
            pts = [xy(c[1], c[0]) for c in ring]
            if len(pts) < 8:
                continue
            idx = {id(q): i for i, q in enumerate(pts)}
            m = len(pts) // 2
            s = dp(pts[: m + 1], 0.6)[:-1] + dp(pts[m:], 0.6)
            keep = [ring[idx[id(q)]] for q in s]
            if len(keep) >= 4:
                rings.append([v for c in keep for v in (round(c[1], 3), round(c[0], 3))])
        if rings:
            out.append({"code": code, "kinki": code in KINKI, "polys": rings})
    return out


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    prefs = build_prefs(sys.argv[1])
    osm = json.load(open(sys.argv[2], encoding="utf-8")) if len(sys.argv) > 2 else fetch()
    els = osm.get("elements", [])
    routes = build_routes(els)
    points = build_points(els, routes)
    for r in routes:
        del r["km"]
    data = {
        "bbox": list(BBOX), "attribution": "高速道路：© OpenStreetMap contributors（ODbL）／県 の 形：Natural Earth",
        "made": time.strftime("%Y-%m-%d"), "routes": routes, "points": points, "prefs": prefs,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    kinds = {}
    for p in points:
        kinds[p["kind"]] = kinds.get(p["kind"], 0) + 1
    print(f"路線 {len(routes)}・{kinds}・県 {len(prefs)}・{os.path.getsize(OUT) // 1024}KB → {OUT}")


if __name__ == "__main__":
    main()
