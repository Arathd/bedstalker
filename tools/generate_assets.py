#!/usr/bin/env python3
"""
Genera los assets procedurales de Bed Stalker:
  - geo/entity/stalker.geo.json             (modelo GeckoLib / Bedrock geometry)
  - animations/entity/stalker.animation.json
  - textures/entity/stalker.png + stalker_glowmask.png
  - sounds/entity/stalker/*.ogg             (sintetizados; reemplazables por tus propios .ogg)

Requiere: python3, numpy, scipy, pillow y ffmpeg (con libvorbis) en el PATH.
Uso:  python3 tools/generate_assets.py
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image
from scipy import signal

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/bedstalker")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


# ============================================================ MODELO
# Unidades Bedrock: 16 = 1 bloque. Altura total = 45 (~2.8 bloques).
def cube(origin, size, uv):
    return {"origin": origin, "size": size, "uv": uv}


def build_geo():
    bones = [
        {"name": "root", "pivot": [0, 23, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 21, 0],
         "cubes": [cube([-3, 21, -1.5], [6, 16, 3], [16, 16])]},
        {"name": "head", "parent": "body", "pivot": [0, 37, 0],
         "cubes": [cube([-3, 37, -3], [6, 8, 6], [0, 0])]},
        {"name": "left_arm", "parent": "body", "pivot": [4.5, 36, 0],
         "cubes": [cube([3, 16, -1.5], [3, 20, 3], [40, 16])]},
        {"name": "right_arm", "parent": "body", "pivot": [-4.5, 36, 0],
         "cubes": [cube([-6, 16, -1.5], [3, 20, 3], [40, 40])]},
        {"name": "left_leg", "parent": "root", "pivot": [1.5, 21, 0],
         "cubes": [cube([0, 0, -1.5], [3, 21, 3], [0, 16])]},
        {"name": "right_leg", "parent": "root", "pivot": [-1.5, 21, 0],
         "cubes": [cube([-3, 0, -1.5], [3, 21, 3], [0, 40])]},
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.stalker",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


# ============================================================ ANIMACIONES
def kf(*pairs):
    """kf((0, [x,y,z]), (0.5, [..])) -> dict de keyframes."""
    return {("%.2f" % t): v for t, v in pairs}


# Pose final "tumbado bajo la cama": tumbado boca abajo (rot X 90) y encogido (escala 0.5)
# alrededor del pivote central (0,23,0); bajado 21.5 unidades para quedar a ras de suelo.
LYING_ROT = [90, 0, 0]
LYING_POS = [0, -21.5, 0]
LYING_SCALE = [0.5, 0.5, 0.5]


def build_animations():
    A = {}

    A["animation.stalker.idle"] = {
        "loop": True, "animation_length": 4.0,
        "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (2, [1.5, 0, 0.8]), (4, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (1.5, [0, 0, 7]), (3, [3, 0, -4]), (4, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 3]), (2, [2, 0, 5]), (4, [0, 0, 3]))},
            "right_arm": {"rotation": kf((0, [0, 0, -3]), (2, [2, 0, -5]), (4, [0, 0, -3]))},
        },
    }

    # Caminar lento y sigiloso (modo acecho)
    A["animation.stalker.walk"] = {
        "loop": True, "animation_length": 1.6,
        "bones": {
            "body": {"rotation": kf((0, [6, 0, 2]), (0.8, [6, 0, -2]), (1.6, [6, 0, 2]))},
            "head": {"rotation": kf((0, [-4, 0, 4]), (0.8, [-4, 0, -4]), (1.6, [-4, 0, 4]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [-22, 0, 0]), (0.8, [0, 0, 0]), (1.2, [22, 0, 0]), (1.6, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [22, 0, 0]), (0.8, [0, 0, 0]), (1.2, [-22, 0, 0]), (1.6, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 3]), (0.4, [14, 0, 3]), (0.8, [0, 0, 3]), (1.2, [-14, 0, 3]), (1.6, [0, 0, 3]))},
            "right_arm": {"rotation": kf((0, [0, 0, -3]), (0.4, [-14, 0, -3]), (0.8, [0, 0, -3]), (1.2, [14, 0, -3]), (1.6, [0, 0, -3]))},
        },
    }

    # Carrera agresiva
    A["animation.stalker.run"] = {
        "loop": True, "animation_length": 0.6,
        "bones": {
            "body": {"rotation": kf((0, [18, 0, 3]), (0.3, [18, 0, -3]), (0.6, [18, 0, 3]))},
            "head": {"rotation": kf((0, [-12, 0, 0]), (0.3, [-14, 0, 0]), (0.6, [-12, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-55, 0, 0]), (0.3, [55, 0, 0]), (0.6, [-55, 0, 0]))},
            "right_leg": {"rotation": kf((0, [55, 0, 0]), (0.3, [-55, 0, 0]), (0.6, [55, 0, 0]))},
            "left_arm": {"rotation": kf((0, [50, 0, 4]), (0.3, [-50, 0, 4]), (0.6, [50, 0, 4]))},
            "right_arm": {"rotation": kf((0, [-50, 0, -4]), (0.3, [50, 0, -4]), (0.6, [-50, 0, -4]))},
        },
    }

    # Ataque (se dispara con triggerAnim): brazos arriba y golpe hacia abajo
    A["animation.stalker.attack"] = {
        "animation_length": 0.7,
        "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.2, [-8, 0, 0]), (0.4, [20, 0, 0]), (0.7, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 3]), (0.2, [-125, 0, 3]), (0.4, [-25, 0, 3]), (0.7, [0, 0, 3]))},
            "right_arm": {"rotation": kf((0, [0, 0, -3]), (0.2, [-125, 0, -3]), (0.4, [-25, 0, -3]), (0.7, [0, 0, -3]))},
        },
    }

    # Agacharse y meterse bajo la cama (1.5 s = ENTER_TICKS 30)
    A["animation.stalker.enter_bed"] = {
        "loop": "hold_on_last_frame", "animation_length": 1.5,
        "bones": {
            "root": {
                "rotation": kf((0, [0, 0, 0]), (0.4, [22, 0, 0]), (0.8, [55, 0, 0]), (1.5, LYING_ROT)),
                "position": kf((0, [0, 0, 0]), (0.4, [0, -7, 0]), (0.8, [0, -14, 0]), (1.5, LYING_POS)),
                "scale": kf((0, [1, 1, 1]), (0.4, [1, 1, 1]), (1.5, LYING_SCALE)),
            },
            "head": {"rotation": kf((0, [0, 0, 0]), (0.4, [10, 0, 0]), (1.5, [-45, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 3]), (0.4, [-40, 0, 3]), (1.5, [-150, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, -3]), (0.4, [-40, 0, -3]), (1.5, [-150, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.5, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.5, [0, 0, 0]))},
        },
    }

    # Quieto bajo la cama (respira apenas)
    A["animation.stalker.hidden"] = {
        "loop": True, "animation_length": 3.0,
        "bones": {
            "root": {
                "rotation": kf((0, LYING_ROT), (3, LYING_ROT)),
                "position": kf((0, LYING_POS), (3, LYING_POS)),
                "scale": kf((0, LYING_SCALE), (1.5, [0.51, 0.5, 0.5]), (3, LYING_SCALE)),
            },
            "head": {"rotation": kf((0, [-45, 0, 0]), (3, [-45, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-150, 0, 0]), (3, [-150, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-150, 0, 0]), (3, [-150, 0, 0]))},
        },
    }

    # Salir de debajo de la cama (1.0 s = EXIT_TICKS 20)
    A["animation.stalker.exit_bed"] = {
        "animation_length": 1.0,
        "bones": {
            "root": {
                "rotation": kf((0, LYING_ROT), (0.5, [50, 0, 0]), (0.8, [15, 0, 0]), (1.0, [0, 0, 0])),
                "position": kf((0, LYING_POS), (0.5, [0, -12, 0]), (0.8, [0, -4, 0]), (1.0, [0, 0, 0])),
                "scale": kf((0, LYING_SCALE), (0.5, [0.8, 0.8, 0.8]), (0.7, [1, 1, 1]), (1.0, [1, 1, 1])),
            },
            "head": {"rotation": kf((0, [-45, 0, 0]), (1.0, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-150, 0, 0]), (0.6, [-60, 0, 3]), (1.0, [0, 0, 3]))},
            "right_arm": {"rotation": kf((0, [-150, 0, 0]), (0.6, [-60, 0, -3]), (1.0, [0, 0, -3]))},
        },
    }

    return {"format_version": "1.8.0", "animations": A}


# ============================================================ TEXTURA
def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def build_texture():
    rng = np.random.default_rng(1337)
    img = np.zeros((64, 64, 4), dtype=np.uint8)
    glow = np.zeros((64, 64, 4), dtype=np.uint8)

    DARK = np.array([26, 25, 30])
    PALE = np.array([198, 192, 180])

    def fill(rect, color, jitter=6):
        x, y, w, h = rect
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                n = rng.integers(-jitter, jitter + 1)
                c = np.clip(np.array(color) + n, 0, 255)
                img[yy, xx] = [c[0], c[1], c[2], 255]

    def paint_box(u, v, w, h, d, base, tip=None, tip_rows=0):
        faces = box_faces(u, v, w, h, d)
        for name, rect in faces.items():
            fill(rect, base)
        if tip is not None and tip_rows:
            for name in ("east", "front", "west", "back"):
                x, y, fw, fh = faces[name]
                fill((x, y + fh - tip_rows, fw, tip_rows), tip, 5)
            fill(faces["bottom"], tip, 5)

    # Cabeza (6x8x6) palida
    paint_box(0, 0, 6, 8, 6, PALE)
    fx, fy, fw, fh = box_faces(0, 0, 6, 8, 6)["front"]
    # cuencas oscuras
    for (cx0, cx1) in ((fx, fx + 1), (fx + 4, fx + 5)):
        for yy in range(fy + 2, fy + 5):
            for xx in range(cx0, cx1 + 1):
                img[yy, xx] = [8, 6, 8, 255]
    # ojos rojos brillantes (con glowmask)
    for xx in (fx, fx + 1, fx + 4, fx + 5):
        img[fy + 3, xx] = [225, 25, 25, 255]
        glow[fy + 3, xx] = [255, 40, 40, 255]
    # nariz
    img[fy + 5, fx + 2] = [120, 112, 104, 255]
    img[fy + 5, fx + 3] = [120, 112, 104, 255]
    # boca: rendija negra con dientes
    for xx in range(fx + 1, fx + 5):
        img[fy + 6, xx] = [10, 6, 8, 255]
        img[fy + 7, xx] = [10, 6, 8, 255] if xx % 2 == 0 else [214, 208, 196, 255]

    # Torso (6x16x3) oscuro con costillas
    paint_box(16, 16, 6, 16, 3, DARK)
    tx, ty, tw, th = box_faces(16, 16, 6, 16, 3)["front"]
    for row in range(2, th - 2, 3):
        for xx in range(tx + 1, tx + tw - 1):
            img[ty + row, xx] = [78, 74, 82, 255]
    for yy in range(ty, ty + th):
        img[yy, tx + tw // 2 - 1] = [14, 13, 16, 255]

    # Brazos (3x20x3) oscuros con manos palidas
    paint_box(40, 16, 3, 20, 3, DARK, PALE, 6)
    paint_box(40, 40, 3, 20, 3, DARK, PALE, 6)
    # Piernas (3x21x3) oscuras con pies palidos
    paint_box(0, 16, 3, 21, 3, DARK, PALE, 3)
    paint_box(0, 40, 3, 21, 3, DARK, PALE, 3)

    out = os.path.join(ASSETS, "textures/entity")
    os.makedirs(out, exist_ok=True)
    Image.fromarray(img, "RGBA").save(os.path.join(out, "stalker.png"))
    Image.fromarray(glow, "RGBA").save(os.path.join(out, "stalker_glowmask.png"))


# ============================================================ SONIDOS
SR = 44100
RNG = np.random.default_rng(42)


def t_axis(dur):
    return np.linspace(0, dur, int(SR * dur), endpoint=False)


def noise(dur):
    return RNG.standard_normal(int(SR * dur))


def bandpass(x, lo, hi, order=3):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, fc, order=3):
    sos = signal.butter(order, fc, btype="low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, fc, order=3):
    sos = signal.butter(order, fc, btype="high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def reverb(x, wet=0.25, length=0.5):
    n = int(SR * length)
    ir = RNG.standard_normal(n) * np.exp(-np.linspace(0, 6, n))
    r = signal.fftconvolve(x, ir)[: len(x) + n]
    r /= (np.max(np.abs(r)) + 1e-9)
    out = np.zeros(len(r))
    out[: len(x)] += x
    out += wet * r * np.max(np.abs(x))
    return out


def sweep_phase(f0, f1, t, curve=1.0):
    """Fase de un barrido de frecuencia (f0 -> f1) a lo largo de t."""
    dur = t[-1] + (t[1] - t[0])
    frac = (t / dur) ** curve
    freq = f0 + (f1 - f0) * frac
    return 2 * np.pi * np.cumsum(freq) / SR


def finish(x, peak=0.9, fade=0.01):
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    n = int(SR * fade)
    if len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return x


def snd_step(variant):
    t = t_axis(0.55)
    f0 = 95 - variant * 6
    body = np.sin(sweep_phase(f0, 38, t, 0.6)) * np.exp(-t * 9)
    thud = lowpass(noise(0.55), 420) * np.exp(-t * 16) * 0.8
    creak = bandpass(noise(0.55), 600, 1400) * np.exp(-t * 30) * 0.15
    return finish(reverb(body + thud + creak, 0.18, 0.35), 0.95)


def snd_breath(variant):
    dur = 2.2
    t = t_axis(dur)
    n = noise(dur)
    air = bandpass(n, 250 + variant * 60, 2200)
    inhale = np.exp(-((t - 0.5) / 0.28) ** 2)
    exhale = np.exp(-((t - 1.4) / 0.35) ** 2) * 1.25
    env = inhale * 0.75 + exhale
    rasp = 0.65 + 0.35 * np.sin(2 * np.pi * (22 + variant * 3) * t)
    low = lowpass(n, 160) * env * 0.5
    x = air * env * rasp + low
    return finish(reverb(x, 0.3, 0.7), 0.55)


def snd_whisper(variant):
    dur = 2.4
    t = t_axis(dur)
    n = noise(dur)
    sib = bandpass(n, 1800, 6500)
    mid = bandpass(n, 700, 1800)
    env = np.zeros_like(t)
    rng = np.random.default_rng(100 + variant)
    pos = 0.15
    while pos < dur - 0.4:
        ln = rng.uniform(0.12, 0.32)
        env += np.exp(-((t - (pos + ln / 2)) / (ln / 2.2)) ** 2) * rng.uniform(0.5, 1.0)
        pos += ln + rng.uniform(0.04, 0.2)
    x = (sib * 0.8 + mid * 0.6) * env * (0.7 + 0.3 * np.sin(2 * np.pi * 5.5 * t))
    return finish(reverb(x, 0.4, 0.9), 0.45)


def snd_scream():
    dur = 1.4
    t = t_axis(dur)
    glide = 230 + 280 * np.sin(np.pi * np.clip(t / 0.9, 0, 1)) - 60 * (t / dur)
    vib = 1 + 0.03 * np.sin(2 * np.pi * 7 * t)
    phase = 2 * np.pi * np.cumsum(glide * vib) / SR
    x = sum(np.sin(h * phase) / h for h in range(1, 11))
    x += 0.5 * np.sin(phase * 0.5)
    x += bandpass(noise(dur), 900, 4500) * 0.6
    env = np.clip(t / 0.04, 0, 1) * np.exp(-np.maximum(t - 0.5, 0) * 2.2)
    x = np.tanh(x * 2.2) * env
    sub = np.sin(2 * np.pi * 58 * t) * np.exp(-t * 3) * 0.6
    return finish(reverb(x + sub, 0.3, 0.8), 0.95)


def snd_bang(variant):
    t = t_axis(0.5)
    thump = np.sin(sweep_phase(80 - variant * 8, 38, t, 0.5)) * np.exp(-t * 11)
    knock = (np.sin(2 * np.pi * (170 + variant * 25) * t) * 0.5 + np.sin(2 * np.pi * 310 * t) * 0.3) * np.exp(-t * 28)
    crack = lowpass(noise(0.5), 1400) * np.exp(-t * 35) * 0.7
    return finish(reverb(thump + knock + crack, 0.2, 0.4), 0.95)


def snd_smash():
    dur = 2.2
    t = t_axis(dur)
    boom = np.sin(sweep_phase(62, 24, t, 0.4)) * np.exp(-t * 2.6)
    crack = bandpass(noise(dur), 150, 3200) * np.exp(-t * 7)
    dis = (np.sin(2 * np.pi * 139 * t) + np.sin(2 * np.pi * 147 * t) + 0.6 * np.sin(2 * np.pi * 208 * t)) * np.exp(-t * 1.6) * 0.18
    groan = np.sin(sweep_phase(95, 48, t, 0.7) + 0.6 * np.sin(2 * np.pi * 5 * t)) * np.exp(-t * 1.3) * 0.35
    x = np.tanh((boom * 1.4 + crack * 0.8 + dis + groan) * 1.5)
    return finish(reverb(x, 0.35, 1.2), 0.98)


def snd_hide():
    dur = 1.2
    t = t_axis(dur)
    cloth = bandpass(noise(dur), 700, 3800)
    env = np.exp(-((t - 0.35) / 0.28) ** 2)
    crackle = (RNG.random(len(t)) > 0.985) * RNG.standard_normal(len(t)) * 0.8
    thump = np.sin(sweep_phase(70, 36, t, 0.5)) * np.exp(-np.maximum(t - 0.85, 0) * 14) * (t > 0.85)
    x = cloth * env * 0.9 + lowpass(crackle * env, 3000) + thump * 1.1
    return finish(reverb(x, 0.2, 0.5), 0.85)


def snd_emerge():
    dur = 1.6
    t = t_axis(dur)
    glide = 52 + 38 * np.clip(t / 1.1, 0, 1)
    phase = 2 * np.pi * np.cumsum(glide * (1 + 0.04 * np.sin(2 * np.pi * 6 * t))) / SR
    x = sum(np.sin(h * phase) / h for h in range(1, 8))
    x += lowpass(noise(dur), 500) * 0.5
    env = np.clip(t / 0.5, 0, 1) * np.exp(-np.maximum(t - 1.0, 0) * 3.5)
    x = np.tanh(x * 1.6) * env
    thump = np.sin(sweep_phase(80, 34, t, 0.5)) * np.exp(-np.maximum(t - 1.05, 0) * 12) * (t > 1.05)
    return finish(reverb(x + thump * 0.9, 0.3, 0.7), 0.95)


def snd_hurt(variant):
    dur = 0.55
    t = t_axis(dur)
    glide = 170 - 70 * (t / dur) + variant * 25
    phase = 2 * np.pi * np.cumsum(glide) / SR
    x = sum(np.sin(h * phase) / h for h in range(1, 9))
    x += bandpass(noise(dur), 400, 3000) * 0.7
    x = np.tanh(x * 2.0) * np.exp(-t * 4.5) * np.clip(t / 0.01, 0, 1)
    return finish(reverb(x, 0.2, 0.4), 0.9)


def snd_death():
    dur = 2.4
    t = t_axis(dur)
    glide = 150 * np.exp(-t * 1.3) + 28
    phase = 2 * np.pi * np.cumsum(glide * (1 + 0.05 * np.sin(2 * np.pi * 6 * t))) / SR
    x = sum(np.sin(h * phase) / h for h in range(1, 10))
    x += bandpass(noise(dur), 300, 2500) * 0.5 * np.exp(-t * 2.0)
    x = np.tanh(x * 1.8) * np.exp(-t * 1.1) * np.clip(t / 0.02, 0, 1)
    return finish(reverb(x, 0.35, 1.0), 0.95)


def write_ogg(name, data):
    out_dir = os.path.join(ASSETS, "sounds/entity/stalker")
    os.makedirs(out_dir, exist_ok=True)
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-ar", str(SR),
         "-c:a", "libvorbis", "-q:a", "4", os.path.join(out_dir, name + ".ogg")],
        check=True)
    os.remove(wav_path)


def build_sounds():
    for i in range(3):
        write_ogg("step%d" % (i + 1), snd_step(i))
    for i in range(2):
        write_ogg("breath%d" % (i + 1), snd_breath(i))
        write_ogg("whisper%d" % (i + 1), snd_whisper(i))
        write_ogg("bang%d" % (i + 1), snd_bang(i))
        write_ogg("hurt%d" % (i + 1), snd_hurt(i))
    write_ogg("scream", snd_scream())
    write_ogg("smash", snd_smash())
    write_ogg("hide", snd_hide())
    write_ogg("emerge", snd_emerge())
    write_ogg("death", snd_death())

    def entry(names, subtitle, volume=1.0, attenuation=None):
        sounds = []
        for n in names:
            s = {"name": "bedstalker:entity/stalker/" + n, "volume": volume}
            if attenuation:
                s["attenuation_distance"] = attenuation
            sounds.append(s)
        return {"category": "hostile", "subtitle": subtitle, "sounds": sounds}

    sounds_json = {
        "entity.stalker.step": entry(["step1", "step2", "step3"], "subtitles.bedstalker.step", 1.0, 24),
        "entity.stalker.breath": entry(["breath1", "breath2"], "subtitles.bedstalker.breath", 1.0, 16),
        "entity.stalker.whisper": entry(["whisper1", "whisper2"], "subtitles.bedstalker.whisper", 1.0, 16),
        "entity.stalker.scream": entry(["scream"], "subtitles.bedstalker.scream", 1.0, 48),
        "entity.stalker.bang": entry(["bang1", "bang2"], "subtitles.bedstalker.bang", 1.0, 32),
        "entity.stalker.smash": entry(["smash"], "subtitles.bedstalker.smash", 1.0, 48),
        "entity.stalker.hide": entry(["hide"], "subtitles.bedstalker.hide", 1.0, 24),
        "entity.stalker.emerge": entry(["emerge"], "subtitles.bedstalker.emerge", 1.0, 32),
        "entity.stalker.hurt": entry(["hurt1", "hurt2"], "subtitles.bedstalker.hurt", 1.0, 24),
        "entity.stalker.death": entry(["death"], "subtitles.bedstalker.death", 1.0, 32),
    }
    write_json(os.path.join(ASSETS, "sounds.json"), sounds_json)


def main():
    write_json(os.path.join(ASSETS, "geo/entity/stalker.geo.json"), build_geo())
    write_json(os.path.join(ASSETS, "animations/entity/stalker.animation.json"), build_animations())
    build_texture()
    build_sounds()
    print("Assets generados en", ASSETS)


if __name__ == "__main__":
    main()
