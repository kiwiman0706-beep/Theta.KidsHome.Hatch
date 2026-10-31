#!/usr/bin/env python3
"""
GM（General MIDI）の うちがっき を 1 音 ずつ WAV に して、がっき の パッド に つかう。
  sudo apt install fluidsynth fluid-soundfont-gm ffmpeg
  python3 tools/render_gm_drums.py
音源：FluidR3_GM（MIT ライセンス、Frank Wen）。できた ogg は app/src/main/assets/drums/ に。
"""
import os, struct, subprocess, sys, tempfile, wave, array

SF2 = os.environ.get("SF2", "/usr/share/sounds/sf2/FluidR3_GM.sf2")
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "drums")
RATE = 22050
MAX_SEC = 0.45

# (キット, パッド番号) → (GM の ノート番号, 大きさ 0〜1)
PADS = {
    ("DRUMS", 0): (36, 1.0),   # Bass Drum 1
    ("DRUMS", 1): (38, 0.95),  # Acoustic Snare
    ("DRUMS", 2): (42, 0.7),   # Closed Hi-Hat
    ("DRUMS", 3): (39, 0.9),   # Hand Clap
    ("DRUMS", 4): (48, 0.9),   # Hi-Mid Tom
    ("DRUMS", 5): (45, 0.95),  # Low Tom
    ("DRUMS", 6): (49, 0.8),   # Crash Cymbal 1
    ("DRUMS", 7): (56, 0.8),   # Cowbell
    ("DRUMS", 8): (46, 0.7),   # Open Hi-Hat
    ("DRUMS", 9): (37, 0.85),  # Side Stick
    ("DRUMS", 10): (51, 0.7),  # Ride Cymbal 1
    ("DRUMS", 11): (50, 0.9),  # High Tom
    ("WORLD", 0): (63, 0.9),   # Open Hi Conga
    ("WORLD", 1): (64, 0.95),  # Low Conga
    ("WORLD", 2): (60, 0.9),   # Hi Bongo
    ("WORLD", 4): (56, 0.8),   # Cowbell
    ("WORLD", 5): (75, 0.85),  # Claves
    ("WORLD", 6): (73, 0.8),   # Short Guiro
    ("WORLD", 7): (70, 0.75),  # Maracas
    ("WORLD", 9): (76, 0.85),  # Hi Wood Block
    ("WORLD", 11): (81, 0.7),  # Open Triangle
}


def midi(note: int, vel: int = 112) -> bytes:
    ev = bytes([0x00, 0x99, note, vel, 0x60, 0x89, note, 0x00, 0x00, 0xFF, 0x2F, 0x00])
    trk = b"MTrk" + struct.pack(">I", len(ev)) + ev
    return b"MThd" + struct.pack(">IHHH", 6, 0, 1, 96) + trk


def render(note: int, gain: float, dst: str):
    with tempfile.TemporaryDirectory() as d:
        mid, wav = os.path.join(d, "n.mid"), os.path.join(d, "n.wav")
        open(mid, "wb").write(midi(note))
        subprocess.run(["fluidsynth", "-ni", "-g", "1.0", "-r", str(RATE), "-F", wav, SF2, mid], check=True, capture_output=True)
        w = wave.open(wav)
        ch, n = w.getnchannels(), w.getnframes()
        raw = array.array("h", w.readframes(n))
        mono = [(raw[i] + raw[i + 1]) / 2 if ch == 2 else raw[i] for i in range(0, len(raw), ch)]
        # 出だし の 無音 を きる
        start = next((i for i, v in enumerate(mono) if abs(v) > 300), 0)
        mono = mono[max(0, start - 20):][: int(RATE * MAX_SEC)]
        peak = max(1, max(abs(v) for v in mono))
        fade = int(RATE * 0.06)
        out = array.array("h")
        for i, v in enumerate(mono):
            f = min(1.0, (len(mono) - i) / fade)
            out.append(int(v / peak * 32000 * gain * f))
        tmp = os.path.join(d, "o.wav")
        o = wave.open(tmp, "wb"); o.setnchannels(1); o.setsampwidth(2); o.setframerate(RATE); o.writeframes(out.tobytes()); o.close()
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "5", dst], check=True)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for (kit, pad), (note, gain) in PADS.items():
        dst = os.path.join(OUT, f"{kit}_{pad}.ogg")
        render(note, gain, dst)
        print(dst, os.path.getsize(dst))
