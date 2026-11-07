#!/usr/bin/env python3
"""
キッズホーム の SD を パソコン で 下ごしらえ する（Windows・Mac）。

SD の KidsHome フォルダ の 動画・音声 の 長さ と サムネイル を 先 に 作って、
KidsHome/.kidshome/ に おく。キッズホーム（📁 フォルダ を えらぶ 方式）は これ を 読む だけ なので、はじめて でも すぐ 出る。

  python make_kidshome_cache.py E:\\KidsHome          （Windows：SD が E: の とき）
  python3 make_kidshome_cache.py /Volumes/SD/KidsHome （Mac）

- ffmpeg と ffprobe が いる（Windows：winget install Gyan.FFmpeg、Mac：brew install ffmpeg）
- ファイル を 足したら もう いちど 走らせる。新しい もの・かわった もの だけ 作る（大きさ で 見分ける）
- できる もの：.kidshome/index.json（相対パス → 大きさ・長さ ms・サムネイル名）、.kidshome/thumbs/*.jpg、.kidshome/.nomedia
- 動画 は 3 びょう め（みじかい とき は さいしょ）の 1 コマ、音声 は 埋め込み ジャケット（なければ なし）。よこ 384px
"""
import concurrent.futures as cf
import hashlib
import json
import os
import shutil
import subprocess
import sys

VIDEO = {".mp4", ".m4v", ".mkv", ".webm", ".3gp", ".mov", ".ts"}
AUDIO = {".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav", ".m4b"}
WIDTH = 384
NO_WINDOW = 0x08000000 if os.name == "nt" else 0  # Windows で 黒い 窓 を 出さない


def run(args):
    return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace", creationflags=NO_WINDOW)


def duration_ms(path):
    r = run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path])
    try:
        return int(float(r.stdout.strip()) * 1000)
    except ValueError:
        return 0


def thumb(path, out, is_video, dur_ms):
    tmp = out + ".tmp.jpg"
    if is_video:
        ss = "3" if dur_ms > 6000 else "0"
        r = run(["ffmpeg", "-v", "error", "-y", "-ss", ss, "-i", path, "-frames:v", "1", "-vf", f"scale={WIDTH}:-2", "-q:v", "4", tmp])
    else:  # 埋め込み ジャケット
        r = run(["ffmpeg", "-v", "error", "-y", "-i", path, "-an", "-map", "0:v:0", "-frames:v", "1", "-vf", f"scale={WIDTH}:-2", "-q:v", "4", tmp])
    if r.returncode == 0 and os.path.exists(tmp) and os.path.getsize(tmp) > 0:
        os.replace(tmp, out)
        return True
    if os.path.exists(tmp):
        os.remove(tmp)
    return False


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    root = os.path.abspath(sys.argv[1])
    if not os.path.isdir(root):
        sys.exit(f"フォルダ が ありません：{root}")
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            sys.exit(f"{tool} が 見つかりません（Windows：winget install Gyan.FFmpeg、Mac：brew install ffmpeg）")
    cache = os.path.join(root, ".kidshome")
    thumbs = os.path.join(cache, "thumbs")
    os.makedirs(thumbs, exist_ok=True)
    open(os.path.join(cache, ".nomedia"), "a").close()  # Android の ギャラリー に 出さない
    index_path = os.path.join(cache, "index.json")
    old = {}
    if os.path.exists(index_path):
        try:
            old = json.load(open(index_path, encoding="utf-8")).get("files", {})
        except (ValueError, OSError):
            old = {}

    jobs = []
    for d, dirs, files in os.walk(root):
        dirs[:] = sorted(x for x in dirs if not x.startswith("."))
        for f in sorted(files):
            ext = os.path.splitext(f)[1].lower()
            if f.startswith(".") or (ext not in VIDEO and ext not in AUDIO):
                continue
            full = os.path.join(d, f)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            jobs.append((rel, full, ext in VIDEO, os.path.getsize(full)))

    files, todo = {}, []
    for rel, full, is_video, size in jobs:
        o = old.get(rel)
        name = hashlib.sha1(rel.encode("utf-8")).hexdigest()[:16] + ".jpg"
        if o and o.get("size") == size and (not o.get("thumb") or os.path.exists(os.path.join(thumbs, o["thumb"]))):
            files[rel] = o
        else:
            todo.append((rel, full, is_video, size, name))

    print(f"ぜんぶ {len(jobs)}・あたらしく 作る {len(todo)}")

    def work(job):
        rel, full, is_video, size, name = job
        ms = duration_ms(full)
        ok = thumb(full, os.path.join(thumbs, name), is_video, ms)
        return rel, {"size": size, "dur": ms, "thumb": name if ok else ""}

    done = 0
    with cf.ThreadPoolExecutor(max_workers=max(2, (os.cpu_count() or 4) // 2)) as ex:
        for rel, entry in ex.map(work, todo):
            files[rel] = entry
            done += 1
            if done % 20 == 0 or done == len(todo):
                print(f"  {done}/{len(todo)}  {rel}")

    # もう ない ファイル の サムネイル は けす
    keep = {e.get("thumb") for e in files.values()}
    for t in os.listdir(thumbs):
        if t not in keep:
            os.remove(os.path.join(thumbs, t))
    json.dump({"v": 1, "files": files}, open(index_path, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(f"できました：{index_path}")


if __name__ == "__main__":
    main()
