#!/usr/bin/env python3
"""
Deterministic Android TV D-pad scroll benchmark for MuxGrid (`Live`, `Recordings`, `Guide`).

Drives scripted D-pad sweeps via `adb shell` against a running MuxGrid TV install (typically
an AOT-compiled `noDebug` `.demo` build connected to `scripts/mock_tvh_server.py` or a test
server) and summarizes `dumpsys gfxinfo <pkg> framestats` stage timings + GPU RenderTarget usage.

Usage:
    python3 scripts/bench_tv_scroll.py --serial 192.168.1.138:5555 --pkg io.github.corebonts.muxgrid.demo
    python3 scripts/bench_tv_scroll.py -s 192.168.1.138:5555 --screens guide_vert,guide_horiz
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys

ALL_SCREENS = ("live", "recordings", "guide_vert", "guide_horiz")

# Keycodes: 19=DPAD_UP, 20=DPAD_DOWN, 21=DPAD_LEFT, 22=DPAD_RIGHT, 23=DPAD_CENTER
SUMMARY_PREFIXES = (
    "Total frames rendered:",
    "Janky frames:",
    "50th percentile:",
    "90th percentile:",
    "95th percentile:",
    "99th percentile:",
    "Number Slow UI thread:",
    "Number Slow bitmap uploads:",
    "Number Slow issue draw commands:",
    "Number Frame deadline missed:",
    "50th gpu percentile:",
    "90th gpu percentile:",
    "95th gpu percentile:",
    "RenderTarget:",
)


def run_adb(serial: str, cmd: str) -> str:
    res = subprocess.run(
        ["adb", "-s", serial, "shell", cmd],
        capture_output=True,
        text=True,
        check=True,
    )
    return res.stdout


def parse_framestats(raw: str) -> list[dict[str, float]]:
    lines = raw.splitlines()
    in_data = False
    frames: list[dict[str, float]] = []
    for line in lines:
        if line.startswith("---PROFILEDATA---"):
            in_data = not in_data
            continue
        if not in_data or line.startswith("Flags,"):
            continue
        parts = [p for p in line.strip().split(",") if p != ""]
        if len(parts) < 14:
            continue
        vals = [int(x) for x in parts]
        vsync = vals[2]
        anim_start = vals[6]
        trav_start = vals[7]
        sync_queued = vals[9]
        sync_start = vals[10]
        issue_start = vals[11]
        swap = vals[12]
        completed = vals[13]
        dequeue_dur = vals[14] if len(vals) > 14 else 0
        if completed <= vsync or trav_start <= 0 or completed <= 0:
            continue
        frames.append(
            {
                "vsync_to_done": (completed - vsync) / 1e6,
                "anim_to_trav": (trav_start - anim_start) / 1e6,
                "trav_to_syncQ": (sync_queued - trav_start) / 1e6,
                "sync_to_issue": (issue_start - sync_start) / 1e6,
                "issue_to_swap": (swap - issue_start) / 1e6,
                "dequeue_dur": dequeue_dur / 1e6,
                "swap_to_done": (completed - swap) / 1e6,
                "active_cpu_rt": (
                    (trav_start - anim_start)
                    + (sync_queued - trav_start)
                    + (completed - sync_start)
                )
                / 1e6,
            }
        )
    return frames


def pct(sorted_vals: list[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    idx = min(len(sorted_vals) - 1, max(0, int(round((p / 100.0) * (len(sorted_vals) - 1)))))
    return sorted_vals[idx]


def stat_line(label: str, frames: list[dict[str, float]], key: str) -> str:
    vals = sorted(f[key] for f in frames)
    return (
        f"  {label:<28} p50={pct(vals, 50):5.1f}ms  p90={pct(vals, 90):5.1f}ms  "
        f"p95={pct(vals, 95):5.1f}ms  max={vals[-1]:6.1f}ms"
    )


def summarize(name: str, raw: str) -> None:
    print(f"=== {name} ===")
    seen_summary_lines: set[str] = set()
    for line in raw.splitlines():
        stripped = line.strip()
        if any(stripped.startswith(prefix) for prefix in SUMMARY_PREFIXES):
            if stripped not in seen_summary_lines:
                seen_summary_lines.add(stripped)
                print("  " + stripped)
    frames = parse_framestats(raw)
    if frames:
        over16 = sum(1 for f in frames if f["vsync_to_done"] > 16.67)
        over16_act = sum(1 for f in frames if f["active_cpu_rt"] > 16.67)
        print(
            f"  Framestats valid frames: {len(frames)} | "
            f"vsync->done>16.7ms: {over16} ({100 * over16 / len(frames):.1f}%) | "
            f"Active(Anim+UI+RT)>16.7ms: {over16_act} ({100 * over16_act / len(frames):.1f}%)"
        )
        for label, key in (
            ("Vsync -> Done", "vsync_to_done"),
            ("Active (Anim+UI+RT)", "active_cpu_rt"),
            ("Compose/Anim (Anim->Trav)", "anim_to_trav"),
            ("UI Trav+Draw (Trav->SyncQ)", "trav_to_syncQ"),
            ("Sync/Upload (Sync->Issue)", "sync_to_issue"),
            ("IssueDraw (Issue->Swap)", "issue_to_swap"),
            ("  DequeueBuffer (in Issue)", "dequeue_dur"),
            ("Swap -> Done", "swap_to_done"),
        ):
            print(stat_line(label, frames, key))


def goto_tab(serial: str, down_count: int, expected_label: str) -> None:
    downs = ("input keyevent " + " ".join(["20"] * down_count)) if down_count > 0 else "true"
    out = run_adb(
        serial,
        f"""
        input keyevent 21 21 21 21
        sleep 0.2
        input keyevent 19 19 19 19
        sleep 0.2
        {downs}
        input keyevent 23
        sleep 0.8
        input keyevent 22
        sleep 0.4
        uiautomator dump /data/local/tmp/bench_ui.xml >/dev/null
        cat /data/local/tmp/bench_ui.xml
        rm -f /data/local/tmp/bench_ui.xml
        """,
    )
    labels = re.findall(r'text="(Live|Guide|Recordings|Settings)"', out)
    print(f"[nav -> {expected_label}] visible={labels[:4]}")


def bench_vertical(serial: str, pkg: str, title: str, steps: int) -> None:
    raw = run_adb(
        serial,
        f"""
        dumpsys gfxinfo {pkg} reset >/dev/null
        for i in $(seq 1 {steps}); do input keyevent 20; done
        for i in $(seq 1 {steps}); do input keyevent 19; done
        sleep 0.8
        dumpsys gfxinfo {pkg} framestats
        """,
    )
    summarize(title, raw)


def bench_recordings_shelf(serial: str, pkg: str, horiz_steps: int) -> None:
    raw = run_adb(
        serial,
        f"""
        dumpsys gfxinfo {pkg} reset >/dev/null
        for i in $(seq 1 {horiz_steps}); do input keyevent 22; done
        input keyevent 20
        for i in $(seq 1 {horiz_steps}); do input keyevent 22; done
        for i in $(seq 1 {horiz_steps}); do input keyevent 21; done
        input keyevent 19
        for i in $(seq 1 {horiz_steps}); do input keyevent 21; done
        sleep 0.8
        dumpsys gfxinfo {pkg} framestats
        """,
    )
    summarize("Recordings shelf scroll", raw)


def bench_guide_horiz(serial: str, pkg: str, steps: int) -> None:
    raw = run_adb(
        serial,
        f"""
        dumpsys gfxinfo {pkg} reset >/dev/null
        for i in $(seq 1 {steps}); do input keyevent 22; done
        for i in $(seq 1 {steps}); do input keyevent 21; done
        sleep 0.8
        dumpsys gfxinfo {pkg} framestats
        """,
    )
    summarize(f"EPG horizontal scroll ({steps}R/{steps}L)", raw)


def main() -> int:
    parser = argparse.ArgumentParser(description="Benchmark Android TV D-pad scrolling via gfxinfo framestats")
    parser.add_argument("-s", "--serial", required=True, help="ADB target serial (e.g. 192.168.1.138:5555)")
    parser.add_argument("--pkg", default="io.github.corebonts.muxgrid.demo", help="Target package name")
    parser.add_argument(
        "--screens",
        default=",".join(ALL_SCREENS),
        help=f"Comma-separated subset of: {', '.join(ALL_SCREENS)}",
    )
    parser.add_argument("--vert-steps", type=int, default=20, help="Down/Up D-pad steps for Live & EPG vertical")
    parser.add_argument("--horiz-steps", type=int, default=24, help="Right/Left D-pad steps for EPG horizontal")
    parser.add_argument("--shelf-steps", type=int, default=10, help="Right/Left D-pad steps per Recordings shelf")
    args = parser.parse_args()

    screens = [s.strip() for s in args.screens.split(",") if s.strip()]
    invalid = [s for s in screens if s not in ALL_SCREENS]
    if invalid:
        print(f"Unknown screens: {invalid}. Valid options: {', '.join(ALL_SCREENS)}", file=sys.stderr)
        return 2

    if "live" in screens:
        goto_tab(args.serial, 0, "Live")
        bench_vertical(args.serial, args.pkg, f"Live vertical scroll ({args.vert_steps}D/{args.vert_steps}U)", args.vert_steps)

    if "recordings" in screens:
        goto_tab(args.serial, 2, "Recordings")
        bench_recordings_shelf(args.serial, args.pkg, args.shelf_steps)

    in_guide = False
    if "guide_vert" in screens:
        goto_tab(args.serial, 1, "Guide")
        in_guide = True
        bench_vertical(args.serial, args.pkg, f"EPG vertical scroll ({args.vert_steps}D/{args.vert_steps}U)", args.vert_steps)

    if "guide_horiz" in screens:
        if not in_guide:
            goto_tab(args.serial, 1, "Guide")
        bench_guide_horiz(args.serial, args.pkg, args.horiz_steps)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
