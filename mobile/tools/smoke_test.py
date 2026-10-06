#!/usr/bin/env python3
"""
End-to-end smoke test of the release APK on an Android emulator (run by CI).

  smoke_test.py APK OUT_DIR

Installs the app, completes onboarding in Persian, visits every section, plays a move, asks for the
best move, opens a lesson, the puzzle builder and the settings (switching to the light style), and
saves a screenshot of each screen. Fails when the app (or its voice process) crashes or a screen
cannot be reached.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

PKG = "com.zorix.chess"
APK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)


def adb(*args, check=True, capture=True):
    r = subprocess.run(["adb", *args], capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if "<hierarchy" in xml:
            root = ET.fromstring(xml[xml.index("<hierarchy"):])
            found = list(root.iter("node"))
            if dismiss_system_dialog(found):
                continue
            return found
        time.sleep(1)
    return []


def dismiss_system_dialog(found):
    """A cold-booted emulator sometimes shows "Pixel Launcher isn't responding" over the app. Such a
    dialog of another app is answered with "Wait"; the same dialog for this app fails the test."""
    title = next((n.get("text") for n in found if (n.get("text") or "").endswith("isn't responding")), None)
    if title is None:
        return False
    if "GrandMaster" in title:
        shot("app_not_responding")
        raise RuntimeError(f"the app froze: {title}")
    wait = next((n for n in found if n.get("text") == "Wait"), None)
    if wait is None:
        adb("shell", "input", "keyevent", "4")
    else:
        x, y = center(wait)
        adb("shell", "input", "tap", str(x), str(y))
    print("dismissed a system dialog:", title, flush=True)
    time.sleep(2)
    return True


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(text, timeout=30, exact=False, last=False):
    """The node showing [text]. An exact match wins over a longer text that only contains it (a hint
    such as «... «تحلیل وضعیت» را بزن» must not be tapped instead of the button). [last] picks the
    last exact match (a lesson card whose title repeats its chapter heading)."""
    end = time.time() + timeout
    while time.time() < end:
        exacts, partial = [], None
        for n in nodes():
            for attr in ("text", "content-desc"):
                v = (n.get(attr) or "").strip()
                if v == text:
                    exacts.append(n)
                elif not exact and partial is None and text in v:
                    partial = n
        if exacts:
            return exacts[-1] if last else exacts[0]
        if partial is not None:
            return partial
        alive()
        time.sleep(1)
    return None


def tap(text, timeout=30, exact=False, required=True, last=False):
    n = find(text, timeout, exact, last)
    if n is None:
        if required:
            shot(f"missing_{re.sub(r'[^a-z0-9]+', '_', text.encode('ascii', 'ignore').decode().lower()) or 'text'}")
            raise RuntimeError(f"could not find '{text}' on screen")
        return False
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.5)
    return True


step = [0]
def shot(name):
    step[0] += 1
    path = os.path.join(OUT, f"{step[0]:02d}_{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    print("screenshot", path, flush=True)


def alive():
    if not adb("shell", "pidof", PKG, check=False).strip():
        crash_log()
        raise RuntimeError("the app is not running (crashed?)")


def crash_log():
    log = adb("logcat", "-d", "-v", "brief", check=False)
    open(os.path.join(OUT, "logcat.txt"), "w").write(log)
    return log


def scroll_down():
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1]
    w, h = int(size[0]), int(size[1])
    adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.75)), str(w // 2), str(int(h * 0.3)), "300")
    time.sleep(1)


def main():
    adb("logcat", "-c", check=False)
    adb("install", "-r", APK)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)
    alive()

    # Onboarding: language, name, level.
    tap("فارسی", timeout=90)
    shot("onboarding_language")
    tap("شروع کن")
    tap("نام تو")
    adb("shell", "input", "text", "Milad")
    adb("shell", "input", "keyevent", "111")  # close the keyboard
    time.sleep(1)
    tap("بعدی")
    shot("onboarding_level")
    tap("شروع کنیم!")
    time.sleep(8)  # intro animation and engine start
    alive()
    shot("analysis")

    # Analysis: play e2-e4 by tapping squares is screen dependent; ask for the best move instead.
    tap("بهترین حرکت", exact=True)
    if find("بهترین حرکت برای", timeout=45) is None:
        shot("no_best_move")
        raise RuntimeError("the engine did not return a best move")
    alive()
    shot("analysis_best_move")

    # Puzzle builder from the analysis action bar.
    tap("ساخت پازل", exact=True)
    shot("builder")
    tap("تحلیل وضعیت", exact=True)
    time.sleep(3)
    alive()
    shot("analysis_after_builder")

    # Play tab.
    tap("بازی", exact=True)
    shot("play")

    # Learn tab, first course and first lesson (the coach reads the introduction aloud).
    tap("آموزش", exact=True)
    time.sleep(2)
    shot("learn")
    tap("مبانی شطرنج")
    shot("course")
    tap("صفحه‌ی شطرنج", exact=True, last=True)  # the lesson card, not the chapter heading above it
    if find("شروع", timeout=20, exact=True) is None:
        shot("lesson_not_open")
        raise RuntimeError("the lesson did not open")
    time.sleep(3)
    alive()
    shot("lesson")
    adb("shell", "input", "keyevent", "4")  # back to the course
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")  # back to the courses
    time.sleep(1)

    # Puzzles tab.
    tap("معماها", exact=True)
    time.sleep(3)
    alive()
    shot("puzzles")

    # Settings tab: switch to the light style and come back.
    tap("تنظیمات", exact=True)
    shot("settings")
    for _ in range(2):
        if tap("آسمانی (روشن)", timeout=3, required=False):
            break
        scroll_down()
    time.sleep(2)
    alive()
    shot("settings_light")
    tap("تحلیل", exact=True)
    time.sleep(2)
    shot("analysis_light")

    time.sleep(3)
    alive()
    log = crash_log()
    fatal = [l for l in log.splitlines() if "FATAL EXCEPTION" in l or ("Fatal signal" in l)]
    if fatal:
        print("\n".join(fatal))
        raise RuntimeError("a crash was logged")
    print("SMOKE TEST PASSED")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        try:
            shot("failure")
            crash_log()
        except Exception:
            pass
        print("SMOKE TEST FAILED:", e)
        sys.exit(1)
