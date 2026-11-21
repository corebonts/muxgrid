#!/usr/bin/env python3
"""
Standalone mock Tvheadend server (HTSP :9982 + HTTP :9981) for MuxGrid demos and
clean, generic README screenshots on real Android & Android TV devices.

Usage:
    python3 scripts/mock_tvh_server.py [--htsp-port 9982] [--http-port 9981]

Then forward ports to a connected device over ADB:
    adb reverse tcp:9982 tcp:9982
    adb reverse tcp:9981 tcp:9981

Connect MuxGrid to host `127.0.0.1` (HTSP 9982, HTTP 9981, any username/password).
"""

from __future__ import annotations

import argparse
import hashlib
import io
import math
import os
import shutil
import socket
import struct
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    Image = None  # type: ignore[assignment]

# ---------------------------------------------------------------------------
# HTSP binary wire codec (matches core/htsp HtsMsgCodec.kt)
# ---------------------------------------------------------------------------

TYPE_MAP = 1
TYPE_S64 = 2
TYPE_STR = 3
TYPE_BIN = 4
TYPE_LIST = 5
TYPE_BOOL = 7


def _encode_s64(val: int) -> bytes:
    if val == 0:
        return b""
    if val < 0:
        return struct.pack("<q", val)
    raw = struct.pack("<Q", val)
    return raw.rstrip(b"\x00")


def _decode_s64(data: bytes) -> int:
    if not data:
        return 0
    if len(data) == 8 and (data[7] & 0x80):
        return struct.unpack("<q", data)[0]
    padded = data + b"\x00" * (8 - len(data))
    return struct.unpack("<Q", padded)[0]


def encode_map_body(d: dict[str, Any]) -> bytes:
    out = bytearray()
    for name, val in d.items():
        if val is None:
            continue
        _write_field(out, name, val)
    return bytes(out)


def _write_field(out: bytearray, name: str, val: Any) -> None:
    name_b = name.encode("utf-8") if name else b""
    if isinstance(val, bool):
        ftype = TYPE_BOOL
        payload = b"\x01" if val else b""
    elif isinstance(val, int):
        ftype = TYPE_S64
        payload = _encode_s64(val)
    elif isinstance(val, str):
        ftype = TYPE_STR
        payload = val.encode("utf-8")
    elif isinstance(val, (bytes, bytearray)):
        ftype = TYPE_BIN
        payload = bytes(val)
    elif isinstance(val, dict):
        ftype = TYPE_MAP
        payload = encode_map_body(val)
    elif isinstance(val, (list, tuple)):
        ftype = TYPE_LIST
        list_buf = bytearray()
        for item in val:
            _write_field(list_buf, "", item)
        payload = bytes(list_buf)
    else:
        raise TypeError(f"Unsupported HTSP value type: {type(val)}")

    out.append(ftype)
    out.append(len(name_b))
    out.extend(struct.pack(">I", len(payload)))
    out.extend(name_b)
    out.extend(payload)


def encode_frame(d: dict[str, Any]) -> bytes:
    body = encode_map_body(d)
    return struct.pack(">I", len(body)) + body


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError("Connection closed")
        buf.extend(chunk)
    return bytes(buf)


def decode_map_body(data: bytes) -> dict[str, Any]:
    res: dict[str, Any] = {}
    pos = 0
    end = len(data)
    while pos < end:
        ftype = data[pos]
        name_len = data[pos + 1]
        data_len = struct.unpack(">I", data[pos + 2 : pos + 6])[0]
        pos += 6
        name = data[pos : pos + name_len].decode("utf-8") if name_len else ""
        pos += name_len
        payload = data[pos : pos + data_len]
        pos += data_len
        res[name] = _decode_val(ftype, payload)
    return res


def _decode_list_body(data: bytes) -> list[Any]:
    items: list[Any] = []
    pos = 0
    end = len(data)
    while pos < end:
        ftype = data[pos]
        name_len = data[pos + 1]
        data_len = struct.unpack(">I", data[pos + 2 : pos + 6])[0]
        pos += 6 + name_len
        payload = data[pos : pos + data_len]
        pos += data_len
        items.append(_decode_val(ftype, payload))
    return items


def _decode_val(ftype: int, payload: bytes) -> Any:
    if ftype == TYPE_MAP:
        return decode_map_body(payload)
    if ftype == TYPE_S64:
        return _decode_s64(payload)
    if ftype == TYPE_STR:
        return payload.decode("utf-8", errors="replace")
    if ftype == TYPE_BIN:
        return payload
    if ftype == TYPE_LIST:
        return _decode_list_body(payload)
    if ftype == TYPE_BOOL:
        return len(payload) >= 1 and payload[0] != 0
    return payload


def read_frame(sock: socket.socket) -> dict[str, Any]:
    header = _recv_exact(sock, 4)
    length = struct.unpack(">I", header)[0]
    payload = _recv_exact(sock, length)
    return decode_map_body(payload)


# ---------------------------------------------------------------------------
# Generic English demo dataset (anchored dynamically to current wall clock)
# ---------------------------------------------------------------------------

TAGS = [
    {"tagId": 1, "tagName": "News", "tagIndex": 1},
    {"tagId": 2, "tagName": "Entertainment", "tagIndex": 2},
    {"tagId": 3, "tagName": "Movies", "tagIndex": 3},
    {"tagId": 4, "tagName": "Sports", "tagIndex": 4},
    {"tagId": 5, "tagName": "Documentary", "tagIndex": 5},
    {"tagId": 6, "tagName": "Radio", "tagIndex": 6},
]

CHANNELS = [
    # (id, number, name, short_badge, accent_rgb, tag_ids, is_radio)
    (101, 1, "Channel One HD", "CH1", (245, 158, 11), [2, 1], False),
    (102, 2, "Channel Two HD", "CH2", (59, 130, 246), [2], False),
    (103, 3, "Horizon News HD", "HZN", (239, 68, 68), [1], False),
    (104, 4, "Alpine Nature 4K", "ALP", (16, 185, 129), [5], False),
    (105, 5, "Arena Sports 1", "AS1", (249, 115, 22), [4], False),
    (106, 6, "Arena Sports 2", "AS2", (234, 88, 12), [4], False),
    (107, 7, "Nordic Cinema HD", "NCM", (139, 92, 246), [3], False),
    (108, 8, "Classic Film", "CLF", (168, 85, 247), [3], False),
    (109, 9, "Science & Space", "SCI", (6, 182, 212), [5], False),
    (110, 10, "Metro Arts HD", "ART", (236, 72, 153), [2, 5], False),
    (111, 11, "World News 24", "W24", (220, 38, 38), [1], False),
    (201, 101, "Classic Jazz FM", "JAZ", (217, 119, 6), [6], True),
    (202, 102, "Symphony Radio", "SYM", (99, 102, 241), [6], True),
    (203, 103, "Indie Pulse FM", "IND", (20, 184, 166), [6], True),
]

SHOW_TEMPLATES: dict[int, list[tuple[str, str, str, int]]] = {
    # channel_id -> list of (title, subtitle, description, duration_min)
    101: [
        ("Morning Briefing", "Daily Headlines", "Top international and regional stories with live studio correspondents.", 45),
        ("Midday Live", "Current Affairs & Weather", "In-depth discussion on technology, culture, and European weather.", 60),
        ("Coastal Express", "Series 4 · Episode 6", "A scenic railway journey tracing the rugged northern coastline.", 55),
        ("Evening News Hour", "Live Broadcast", "Comprehensive coverage of the day's global developments and markets.", 60),
        ("The Glass Harbor", "Drama Series", "When a routine port inspection uncovers a decades-old ledger, an investigator reopens the case.", 65),
        ("Late Night Review", "Arts & Cinema", "Critics discuss this week's festival premieres and streaming releases.", 45),
    ],
    102: [
        ("Kitchen Craft", "Seasonal Baking", "Artisan sourdough and winter pastries made with simple ingredients.", 45),
        ("Cityscapes", "Modern Architecture", "How timber skyscrapers and green corridors are reshaping urban skylines.", 50),
        ("Quiz Masters", "Quarter-Final", "Four teams compete across history, science, and world geography.", 45),
        ("Acoustic Sessions", "Live from Studio B", "Intimate unplugged performances from independent folk and jazz ensembles.", 60),
        ("Northern Noir", "Episode 3: Fog Line", "Detectives track a missing freighter through the archipelago.", 60),
        ("Midnight Comedy Hour", "Stand-Up Showcase", "Highlights from the annual international comedy festival.", 50),
    ],
    103: [
        ("Global Desk", "Live Coverage", "Continuous international reporting from London, Brussels, and Tokyo.", 60),
        ("Markets Today", "Business & Economy", "Opening bell analysis, energy prices, and central bank updates.", 45),
        ("Tech & Infrastructure", "Weekly Report", "Inside next-generation open-source networks and renewable microgrids.", 45),
        ("Horizon Evening Report", "Flagship Bulletin", "Breaking stories, interviews, and live panel debate.", 60),
        ("World Press Review", "Media Analysis", "Editors examine tomorrow's front pages from around the globe.", 45),
    ],
    104: [
        ("Peaks of the Dolomites", "4K HDR Documentary", "Following ibex herds and golden eagles across high alpine ridges in winter.", 60),
        ("Deep Fjord", "Ocean Wildlife", "Submersible cameras explore cold-water coral reefs beneath northern fjords.", 55),
        ("Forests After Dusk", "Episode 2: Owls & Lynx", "Thermal optics reveal the hidden nocturnal life of ancient pine forests.", 50),
        ("Glacier Time-Lapse", "Earth Sciences", "Decades of satellite and field photography documenting shifting ice sheets.", 60),
        ("Migratory Skies", "Autumn Flyway", "Tracking cranes and storks across thousands of kilometers of open terrain.", 55),
    ],
    105: [
        ("European Cycling Tour", "Stage 5 · Mountain Finish", "Live coverage of the decisive hors-catégorie climb to the summit.", 90),
        ("Champions League Magazine", "Matchday Preview", "Tactical breakdowns, training ground access, and manager press conferences.", 45),
        ("Live Football: North vs. Metro", "Premier Cup · 2nd Half", "High-stakes knockout clash under the floodlights with pitchside commentary.", 105),
        ("Post-Match Analysis", "Live Studio", "Key moments, expected goals, and player interviews from tonight's fixture.", 45),
    ],
    106: [
        ("Grand Prix Qualifying", "Circuit Highlights", "Full qualifying recap and telemetry comparison from the front row.", 60),
        ("World Athletics Indoor", "Track & Field Finals", "Live 1500m, high jump, and relay finals from the central arena.", 75),
        ("Alpine Ski World Cup", "Men's Downhill Replay", "High-speed runs down the classic winter course.", 60),
        ("Tennis Masters Daily", "Court Side", "Best rallies and quarter-final highlights from the indoor tour.", 50),
    ],
    107: [
        ("Winter Light", "2023 · Drama", "A quiet portrait of a lighthouse keeper preparing for the final automated season.", 105),
        ("The Cartographer's Map", "2022 · Mystery", "An archivist discovers an uncharted island hidden in 19th-century naval records.", 110),
        ("Silent Orbit", "2024 · Sci-Fi", "Two orbital mechanics must manually dock a drifting relay station before solar storm arrival.", 115),
        ("Short Film Anthology", "European Selection", "Award-winning animated and live-action shorts from emerging directors.", 55),
    ],
    108: [
        ("The Third Train", "1964 · Noir Thriller", "A restored monochrome classic set aboard an overnight transcontinental express.", 95),
        ("Harbor of Shadows", "1958 · Drama", "Dockworkers navigate loyalty and ambition during a bitter winter strike.", 100),
        ("Cinema Restored", "Archival Featurette", "How 35mm nitrate prints are scanned and preserved frame by frame.", 45),
        ("Double Horizon", "1971 · Adventure", "Two rival pilots attempt a non-stop polar crossing in vintage twin-engine aircraft.", 105),
    ],
    109: [
        ("James Webb: First Light", "Astrophysics", "How infrared mirrors peered back to the earliest galaxies in the cosmos.", 55),
        ("Fusion Frontier", "Engineering", "Inside experimental stellarators and superconducting magnet assemblies.", 60),
        ("Signal & Noise", "Computing History", "From mechanical relays to modern packet-switched fiber backbones.", 50),
        ("Mars Rovers: Sol 1000", "Planetary Science", "Autonomous navigation and core sampling across Jezero Crater.", 55),
    ],
    110: [
        ("Philharmonic Live", "Mahler Symphony No. 5", "Recorded live in the main concert hall with full surround mix.", 80),
        ("Design Objects", "Industrial Classics", "The story behind iconic lamps, turntables, and Swiss railway clocks.", 45),
        ("Stage & Curtain", "Theater Magazine", "Backstage rehearsals and lighting design for the autumn repertory.", 50),
        ("World Cinema Essays", "Cinematography", "Exploring natural light and wide-angle framing in modern film.", 55),
    ],
    111: [
        ("World News Top of the Hour", "Live Bulletin", "Fast-paced global headlines and international weather.", 30),
        ("European Parliament Live", "Debate & Votes", "Key committee sessions and policy briefings from Strasbourg.", 60),
        ("Climate & Energy Watch", "Weekly Digest", "Grid storage breakthroughs and offshore wind deployments.", 45),
        ("Asia-Pacific Today", "Regional Desk", "Overnight developments from Seoul, Singapore, and Sydney.", 45),
    ],
    201: [
        ("Blue Note Afternoons", "Vinyl & Modern Jazz", "Curated modal jazz, hard bop, and contemporary European trios.", 120),
        ("Late Night Standards", "Quartet Sessions", "Warm acoustic ballads and classic studio recordings.", 120),
    ],
    202: [
        ("Chamber Music Matinee", "String Quartets", "Works by Haydn, Debussy, and Bartók performed live.", 120),
        ("Evening Concert Hall", "Orchestral Broadcast", "Full symphony performances from European concert halls.", 120),
    ],
    203: [
        ("Daytime Indie Rotation", "New Releases", "Independent electronic, post-rock, and shoegaze tracks.", 120),
        ("Night Drive Synth", "Ambient & Downtempo", "Uninterrupted late-night atmospheric instrumentals.", 120),
    ],
}


def build_dataset(now_sec: int) -> dict[str, Any]:
    """Generate EPG events and DVR entries anchored cleanly around now_sec."""
    # Round anchor to nearest 15 minutes in the past
    base_hour = (now_sec // 3600) * 3600 - 4 * 3600

    events: list[dict[str, Any]] = []
    channel_now_next: dict[int, tuple[int | None, int | None]] = {}
    next_event_id = 10_000

    # Recording-now link for Alpine Nature 4K (ch 104)
    recording_now_dvr_id = 5001
    recording_now_event: dict[str, Any] | None = None
    scheduled_dvr_id_1 = 5002
    scheduled_dvr_id_2 = 5003
    scheduled_events: list[tuple[int, dict[str, Any]]] = []

    for ch_idx, (ch_id, _num, _name, _badge, _rgb, _tags, _is_radio) in enumerate(CHANNELS):
        templates = SHOW_TEMPLATES[ch_id]
        # Stagger channel start offsets slightly so the TV EPG grid looks realistic
        t = base_hour + (ch_idx % 4) * 900
        end_horizon = now_sec + 36 * 3600
        ch_events: list[dict[str, Any]] = []
        tpl_idx = ch_idx % len(templates)

        while t < end_horizon:
            title, subtitle, desc, dur_min = templates[tpl_idx % len(templates)]
            stop = t + dur_min * 60
            ev = {
                "eventId": next_event_id,
                "channelId": ch_id,
                "start": t,
                "stop": stop,
                "title": title,
                "subtitle": subtitle,
                "summary": subtitle,
                "description": desc,
            }
            ch_events.append(ev)
            next_event_id += 1
            tpl_idx += 1
            t = stop

        # Link nextEventId and locate current/next event
        cur_id = None
        nxt_id = None
        for i, ev in enumerate(ch_events):
            if i + 1 < len(ch_events):
                ev["nextEventId"] = ch_events[i + 1]["eventId"]
            if ev["start"] <= now_sec < ev["stop"]:
                cur_id = ev["eventId"]
                nxt_id = ev.get("nextEventId")
                if ch_id == 104:
                    ev["dvrId"] = recording_now_dvr_id
                    recording_now_event = ev
            elif ch_id == 107 and ev["start"] > now_sec + 3600 and len(scheduled_events) == 0:
                ev["dvrId"] = scheduled_dvr_id_1
                scheduled_events.append((scheduled_dvr_id_1, ev))
            elif ch_id == 109 and ev["start"] > now_sec + 7200 and len(scheduled_events) == 1:
                ev["dvrId"] = scheduled_dvr_id_2
                scheduled_events.append((scheduled_dvr_id_2, ev))

        channel_now_next[ch_id] = (cur_id, nxt_id)
        events.extend(ch_events)

    # Build DVR entries: 1 recording now, 5 completed (incl. 2-ep series), 2 scheduled
    dvr_entries: list[dict[str, Any]] = []

    if recording_now_event is not None:
        dvr_entries.append({
            "id": recording_now_dvr_id,
            "channel": 104,
            "start": recording_now_event["start"],
            "stop": recording_now_event["stop"],
            "title": recording_now_event["title"],
            "subtitle": recording_now_event["subtitle"],
            "description": recording_now_event["description"],
            "state": "recording",
            "dataSize": 1_840_000_000,
            "playposition": 0,
            "playcount": 0,
        })

    # Completed recordings (yesterday & 2 days ago)
    dvr_entries.extend([
        {
            "id": 5010,
            "channel": 101,
            "start": now_sec - 20 * 3600,
            "stop": now_sec - 19 * 3600,
            "title": "The Glass Harbor",
            "subtitle": "Episode 2: Night Manifest",
            "description": "An anonymous tip leads investigators to an abandoned dry dock.",
            "state": "completed",
            "dataSize": 3_420_000_000,
            "playposition": 1480,  # ~41% watched (in-progress resume bar)
            "playcount": 0,
        },
        {
            "id": 5011,
            "channel": 101,
            "start": now_sec - 44 * 3600,
            "stop": now_sec - 43 * 3600,
            "title": "The Glass Harbor",
            "subtitle": "Episode 1: The Ledger",
            "description": "A routine port inspection uncovers a decades-old shipping ledger.",
            "state": "completed",
            "dataSize": 3_310_000_000,
            "playposition": 0,
            "playcount": 1,  # Watched
        },
        {
            "id": 5012,
            "channel": 107,
            "start": now_sec - 28 * 3600,
            "stop": now_sec - 26 * 3600 - 900,
            "title": "Silent Orbit",
            "subtitle": "2024 · Sci-Fi Feature",
            "description": "Two orbital mechanics manually dock a drifting relay station before a solar storm.",
            "state": "completed",
            "dataSize": 6_180_000_000,
            "playposition": 2820,
            "playcount": 0,
        },
        {
            "id": 5013,
            "channel": 105,
            "start": now_sec - 52 * 3600,
            "stop": now_sec - 50 * 3600 - 1800,
            "title": "European Cycling Tour",
            "subtitle": "Stage 4 · Alpine Pass",
            "description": "Full broadcast of the mountain stage with summit finish.",
            "state": "completed",
            "dataSize": 4_950_000_000,
            "playposition": 0,
            "playcount": 0,
        },
        {
            "id": 5014,
            "channel": 109,
            "start": now_sec - 72 * 3600,
            "stop": now_sec - 71 * 3600,
            "title": "James Webb: First Light",
            "subtitle": "Astrophysics Special",
            "description": "How infrared mirrors peered back to the earliest galaxies.",
            "state": "completed",
            "dataSize": 2_890_000_000,
            "playposition": 0,
            "playcount": 1,
        },
    ])

    for dvr_id, ev in scheduled_events:
        dvr_entries.append({
            "id": dvr_id,
            "channel": ev["channelId"],
            "start": ev["start"],
            "stop": ev["stop"],
            "title": ev["title"],
            "subtitle": ev["subtitle"],
            "description": ev["description"],
            "state": "scheduled",
            "dataSize": 0,
            "playposition": 0,
            "playcount": 0,
        })

    channels_out: list[dict[str, Any]] = []
    for ch_id, num, name, _badge, _rgb, tag_ids, is_radio in CHANNELS:
        cur_id, nxt_id = channel_now_next[ch_id]
        ch_msg: dict[str, Any] = {
            "channelId": ch_id,
            "channelNumber": num,
            "channelName": name,
            "channelIcon": f"imagecache/{ch_id}",
            "tags": tag_ids,
            "services": [{"type": "Radio", "content": 2} if is_radio else {"type": "HDTV", "content": 1}],
        }
        if cur_id is not None:
            ch_msg["eventId"] = cur_id
        if nxt_id is not None:
            ch_msg["nextEventId"] = nxt_id
        channels_out.append(ch_msg)

    # Populate tagMembers
    tags_out: list[dict[str, Any]] = []
    for tag in TAGS:
        tid = tag["tagId"]
        members = [c[0] for c in CHANNELS if tid in c[5]]
        tags_out.append({**tag, "tagMembers": members})

    return {
        "tags": tags_out,
        "channels": channels_out,
        "events": events,
        "dvr": dvr_entries,
    }


# ---------------------------------------------------------------------------
# Picon PNG generator (crisp Broadcast-Monitor badges via Pillow)
# ---------------------------------------------------------------------------


def _load_font(size: int) -> Any:
    if Image is None:
        return None
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    ):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def generate_picons() -> dict[int, bytes]:
    picons: dict[int, bytes] = {}
    if Image is None:
        return picons

    font_big = _load_font(56)
    font_small = _load_font(20)

    for ch_id, _num, name, badge, rgb, _tags, is_radio in CHANNELS:
        w, h = 220, 132
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)

        r, g, b = rgb
        # Dark slate rounded card with subtle accent bar
        bg = (24, 27, 34, 255)
        border = (r, g, b, 210)
        draw.rounded_rectangle([4, 4, w - 5, h - 5], radius=22, fill=bg, outline=border, width=4)
        # Top accent stripe
        draw.rounded_rectangle([18, 14, w - 19, 22], radius=4, fill=(r, g, b, 255))

        # Centered badge letters
        bbox = draw.textbbox((0, 0), badge, font=font_big)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text(((w - tw) / 2, 34), badge, fill=(244, 246, 250, 255), font=font_big)

        # Sub-label ("RADIO" or short suffix)
        sub = "FM RADIO" if is_radio else ("4K UHD" if "4K" in name else "HDTV")
        sb = draw.textbbox((0, 0), sub, font=font_small)
        sw = sb[2] - sb[0]
        draw.text(((w - sw) / 2, h - 32), sub, fill=(r, g, b, 255), font=font_small)

        buf = io.BytesIO()
        img.save(buf, format="PNG")
        picons[ch_id] = buf.getvalue()

    return picons


# ---------------------------------------------------------------------------
# Scenic broadcast MPEG-TS clip generator (for real libmpv player screenshots)
# ---------------------------------------------------------------------------


def generate_scenic_ts(tmp_dir: str) -> str | None:
    """Create a sleek 10-second 1280x720 scenic twilight landscape MPEG-TS clip."""
    if not shutil.which("ffmpeg") or Image is None:
        return None

    frame_path = os.path.join(tmp_dir, "broadcast_frame.png")
    ts_path = os.path.join(tmp_dir, "broadcast_loop.ts")

    w, h = 1280, 720
    img = Image.new("RGB", (w, h))
    px = img.load()
    # Atmospheric alpine twilight gradient (sky -> warm amber horizon -> deep lake)
    for y in range(h):
        t = y / (h - 1)
        if t < 0.62:
            u = t / 0.62
            r = int(12 + 52 * (u ** 2.2) + 85 * (u ** 5.0))
            g = int(20 + 48 * (u ** 1.8) + 42 * (u ** 4.5))
            b = int(42 + 65 * (u ** 1.3) - 18 * (u ** 4.0))
        else:
            u = (t - 0.62) / 0.38
            r = int(32 * (1 - u) + 10 * u)
            g = int(44 * (1 - u) + 16 * u)
            b = int(68 * (1 - u) + 26 * u)
        for x in range(w):
            px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))

    draw = ImageDraw.Draw(img, "RGBA")

    # Subtle glowing horizon sun / alpine glow
    for radius in range(220, 10, -10):
        alpha = int(10 * (1 - radius / 220))
        draw.ellipse(
            [w * 0.62 - radius, h * 0.56 - radius * 0.55, w * 0.62 + radius, h * 0.56 + radius * 0.55],
            fill=(255, 185, 95, alpha),
        )

    # Distant misty mountain ridge
    far_ridge = [(0, int(h * 0.62))]
    for x in range(0, w + 1, 16):
        y = int(
            h * 0.54
            - 75 * math.sin(x * 0.0042 + 0.6)
            - 38 * math.sin(x * 0.011 + 1.8)
            - 18 * math.cos(x * 0.023)
        )
        far_ridge.append((x, y))
    far_ridge.append((w, int(h * 0.65)))
    draw.polygon(far_ridge, fill=(28, 42, 66, 235))

    # Midground dramatic peaks
    mid_ridge = [(0, int(h * 0.66))]
    for x in range(0, w + 1, 12):
        y = int(
            h * 0.60
            - 95 * abs(math.sin(x * 0.0031 + 2.2))
            - 28 * math.sin(x * 0.014)
        )
        mid_ridge.append((x, y))
    mid_ridge.append((w, int(h * 0.68)))
    draw.polygon(mid_ridge, fill=(18, 28, 45, 245))

    # Foreground dark pine ridge
    near_ridge = [(0, h)]
    for x in range(0, w + 1, 10):
        y = int(h * 0.68 + 32 * math.sin(x * 0.0045 + 0.4) - 12 * abs(math.sin(x * 0.05)))
        near_ridge.append((x, y))
    near_ridge.append((w, h))
    draw.polygon(near_ridge, fill=(11, 16, 26, 255))

    # Subtle water reflection shimmer
    for i in range(18):
        ry = int(h * 0.67 + i * 11)
        rw = max(20, 140 - i * 6)
        draw.line(
            [(int(w * 0.62 - rw), ry), (int(w * 0.62 + rw), ry)],
            fill=(245, 175, 95, max(8, 38 - i * 2)),
            width=2,
        )

    # Subtle crisp TV channel bug in top-right corner ("ALPINE 4K")
    bug_font = _load_font(20)
    draw.rounded_rectangle([w - 178, 30, w - 34, 68], radius=8, fill=(15, 18, 24, 150))
    draw.text((w - 162, 38), "ALPINE 4K", fill=(240, 244, 248, 210), font=bug_font)

    img.save(frame_path)

    # Encode 10-second smooth H.264 + stereo AAC MPEG-TS with subtle zoompan motion
    cmd = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-loop",
        "1",
        "-i",
        frame_path,
        "-f",
        "lavfi",
        "-i",
        "sine=frequency=220:sample_rate=48000:duration=10",
        "-t",
        "10",
        "-vf",
        "scale=1280:720,format=yuv420p",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-tune",
        "stillimage",
        "-r",
        "25",
        "-g",
        "25",
        "-b:v",
        "1800k",
        "-c:a",
        "aac",
        "-ac",
        "2",
        "-b:a",
        "128k",
        "-af",
        "volume=0.02",
        "-f",
        "mpegts",
        ts_path,
    ]
    subprocess.run(cmd, check=True)
    return ts_path


# ---------------------------------------------------------------------------
# HTSP TCP Server (:9982)
# ---------------------------------------------------------------------------


class MockHtspServer:
    def __init__(self, host: str, port: int, picons: dict[int, bytes]) -> None:
        self.host = host
        self.port = port
        self.picons = picons
        self._stop = threading.Event()
        self._sock: socket.socket | None = None

    def start(self) -> threading.Thread:
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind((self.host, self.port))
        self._sock.listen(16)
        self._sock.settimeout(1.0)
        t = threading.Thread(target=self._accept_loop, name="htsp-accept", daemon=True)
        t.start()
        return t

    def stop(self) -> None:
        self._stop.set()
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass

    def _accept_loop(self) -> None:
        assert self._sock is not None
        while not self._stop.is_set():
            try:
                conn, addr = self._sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(
                target=self._handle_client,
                args=(conn, addr),
                name=f"htsp-{addr[0]}:{addr[1]}",
                daemon=True,
            ).start()

    def _handle_client(self, conn: socket.socket, addr: tuple[str, int]) -> None:
        print(f"[HTSP] Client connected: {addr[0]}:{addr[1]}", flush=True)
        open_files: dict[int, bytes] = {}
        next_handle = 1
        challenge = hashlib.sha256(b"muxgrid-demo-challenge").digest()
        try:
            while not self._stop.is_set():
                req = read_frame(conn)
                method = req.get("method", "")
                seq = req.get("seq")
                reply: dict[str, Any] = {}
                if seq is not None:
                    reply["seq"] = seq

                if method == "hello":
                    reply.update({
                        "htspversion": 44,
                        "servername": "MuxGrid Demo Server",
                        "serverversion": "4.3-demo",
                        "challenge": challenge,
                    })
                    conn.sendall(encode_frame(reply))

                elif method == "authenticate":
                    conn.sendall(encode_frame(reply))

                elif method == "getSysTime":
                    reply.update({
                        "time": int(time.time()),
                        "gmtoffset": 3600,
                        "timezone": "UTC+1",
                    })
                    conn.sendall(encode_frame(reply))

                elif method == "enableAsyncMetadata":
                    conn.sendall(encode_frame(reply))
                    dataset = build_dataset(int(time.time()))
                    for tag in dataset["tags"]:
                        conn.sendall(encode_frame({"method": "tagAdd", **tag}))
                    for ch in dataset["channels"]:
                        conn.sendall(encode_frame({"method": "channelAdd", **ch}))
                    for ev in dataset["events"]:
                        conn.sendall(encode_frame({"method": "eventAdd", **ev}))
                    for dvr in dataset["dvr"]:
                        conn.sendall(encode_frame({"method": "dvrEntryAdd", **dvr}))
                    conn.sendall(encode_frame({"method": "initialSyncCompleted"}))
                    print(
                        f"[HTSP] Synced {len(dataset['channels'])} channels, "
                        f"{len(dataset['events'])} events, {len(dataset['dvr'])} DVR entries",
                        flush=True,
                    )

                elif method == "getTicket":
                    ch_id = req.get("channelId")
                    dvr_id = req.get("dvrId")
                    path = f"/stream/channelid/{ch_id}" if ch_id else f"/dvrfile/{dvr_id or 5010}"
                    reply.update({"path": path, "ticket": "demo-ticket"})
                    conn.sendall(encode_frame(reply))

                elif method == "fileOpen":
                    fpath = str(req.get("file", "")).lstrip("/")
                    data = self._lookup_vfs(fpath)
                    if data is None:
                        reply["error"] = "File not found"
                    else:
                        hid = next_handle
                        next_handle += 1
                        open_files[hid] = data
                        reply.update({"id": hid, "size": len(data), "mtime": int(time.time())})
                    conn.sendall(encode_frame(reply))

                elif method == "fileRead":
                    hid = int(req.get("id", 0))
                    size = int(req.get("size", 65536))
                    offset = int(req.get("offset", 0))
                    blob = open_files.get(hid, b"")
                    reply["data"] = blob[offset : offset + size]
                    conn.sendall(encode_frame(reply))

                elif method == "fileClose":
                    hid = int(req.get("id", 0))
                    open_files.pop(hid, None)
                    conn.sendall(encode_frame(reply))

                elif method in ("addDvrEntry", "updateDvrEntry", "cancelDvrEntry", "stopDvrEntry", "deleteDvrEntry"):
                    reply["success"] = 1
                    if method == "addDvrEntry":
                        reply["id"] = 5099
                    conn.sendall(encode_frame(reply))

                else:
                    conn.sendall(encode_frame(reply))
        except (EOFError, ConnectionResetError, BrokenPipeError, OSError):
            pass
        finally:
            try:
                conn.close()
            except OSError:
                pass
            print(f"[HTSP] Client disconnected: {addr[0]}:{addr[1]}", flush=True)

    def _lookup_vfs(self, path: str) -> bytes | None:
        if path.startswith("imagecache/"):
            try:
                cid = int(path.split("/", 1)[1])
                return self.picons.get(cid)
            except ValueError:
                return None
        return None


# ---------------------------------------------------------------------------
# HTTP Server (:9981) — picons + looping MPEG-TS stream
# ---------------------------------------------------------------------------


def make_http_handler(picons: dict[int, bytes], ts_bytes: bytes | None) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt: str, *args: Any) -> None:
            print(f"[HTTP] {self.address_string()} - {fmt % args}", flush=True)

        def do_GET(self) -> None:
            clean_path = self.path.split("?", 1)[0]
            if clean_path.startswith("/imagecache/"):
                try:
                    cid = int(clean_path.split("/")[2])
                except (IndexError, ValueError):
                    self.send_error(404)
                    return
                png = picons.get(cid)
                if not png:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(png)))
                self.end_headers()
                self.wfile.write(png)
                return

            if clean_path.startswith("/stream/") or clean_path.startswith("/dvrfile/"):
                if not ts_bytes:
                    self.send_error(503, "No MPEG-TS test clip available")
                    return
                self.send_response(200)
                self.send_header("Content-Type", "video/mp2t")
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.end_headers()
                chunk_size = 188 * 128
                try:
                    while True:
                        for off in range(0, len(ts_bytes), chunk_size):
                            self.wfile.write(ts_bytes[off : off + chunk_size])
                            self.wfile.flush()
                            time.sleep(0.03)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    return

            self.send_error(404)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="Mock Tvheadend server for MuxGrid demos/screenshots")
    parser.add_argument("--host", default="0.0.0.0", help="Bind address (default: 0.0.0.0)")
    parser.add_argument("--htsp-port", type=int, default=9982, help="HTSP port (default: 9982)")
    parser.add_argument("--http-port", type=int, default=9981, help="HTTP port (default: 9981)")
    args = parser.parse_args()

    picons = generate_picons()
    print(f"[INIT] Generated {len(picons)} PNG picons", flush=True)

    tmp_dir = tempfile.mkdtemp(prefix="muxgrid-mock-tvh-")
    ts_bytes: bytes | None = None
    try:
        ts_path = generate_scenic_ts(tmp_dir)
        if ts_path and os.path.exists(ts_path):
            with open(ts_path, "rb") as f:
                ts_bytes = f.read()
            print(f"[INIT] Generated scenic MPEG-TS stream ({len(ts_bytes) // 1024} KiB)", flush=True)
        else:
            print("[INIT] Warning: ffmpeg/Pillow unavailable; live stream disabled", flush=True)

        htsp = MockHtspServer(args.host, args.htsp_port, picons)
        htsp.start()

        httpd = ThreadingHTTPServer((args.host, args.http_port), make_http_handler(picons, ts_bytes))
        print(
            f"[READY] Mock Tvheadend listening on HTSP {args.host}:{args.htsp_port}, "
            f"HTTP {args.host}:{args.http_port}",
            flush=True,
        )
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            httpd.shutdown()
            htsp.stop()
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
