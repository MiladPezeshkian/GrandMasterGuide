# main.py
# GrandMaster Guide — Final, PyInstaller-ready GUI
# - App name: "GrandMaster Guide"
# - Footer: "Created by Milad pezeshkian  All right reserverd"
# - Uses resource_path() to locate bundled stockfish.exe and pieces/
# - No top-level prints (suitable for building with --noconsole / --windowed)
#
# Packaging example (Windows, recommended test flow):
#   1) Test build (onedir):
#       pyinstaller --onedir --windowed --add-data "pieces;pieces" --add-binary "stockfish.exe;." --icon "app.ico" main.py
#   2) When OK, make single-file (optional):
#       pyinstaller --onefile --windowed --add-data "pieces;pieces" --add-binary "stockfish.exe;." --icon "app.ico" main.py

import sys
import os
import shutil
import threading
import time
import math
import urllib.request
import subprocess
from datetime import datetime

import pygame
import chess
import chess.engine
import chess.pgn

# ---------- APP METADATA ----------
APP_NAME = "GrandMaster Guide"
FOOTER_TEXT = "Created by Milad pezeshkian  All right reserverd"

# ---------- CONFIG ----------
SQ_SIZE = 88
BOARD_SIZE = SQ_SIZE * 8
PANEL_WIDTH = 340
WINDOW_SIZE = (BOARD_SIZE + PANEL_WIDTH, BOARD_SIZE)
FPS = 60

PIECE_URL = "https://chessboardjs.com/img/chesspieces/wikipedia/{}.png"

DEFAULT_MOVETIME = 2.0
MIN_MOVETIME = 0.5
MAX_MOVETIME = 30.0
HASH_MB = 256

# Colors
LIGHT_COLOR = (240, 217, 181)
DARK_COLOR  = (181, 136, 99)
HIGHLIGHT_COLOR = (60, 160, 255)
SUGGEST_COLOR = (30, 200, 80)
LASTMOVE_COLOR = (255, 235, 130, 160)
PANEL_BG = (36, 36, 36)
BOX_BG = (28, 28, 28)
BTN_BG = (70, 70, 76)
BTN_HOVER = (100, 100, 106)
TEXT_COLOR = (230, 230, 230)
NOTE_COLOR = (190,190,190)
ERR_COLOR = (220, 80, 80)

# ---------- resource helpers ----------
def resource_path(relpath: str) -> str:
    """ Resolve a local resource path in dev and after PyInstaller freeze. """
    if getattr(sys, "frozen", False):
        base = getattr(sys, "_MEIPASS", os.path.dirname(sys.executable))
    else:
        base = os.path.abspath(os.path.dirname(__file__) if "__file__" in globals() else ".")
    return os.path.join(base, relpath)

def find_stockfish() -> str:
    """ Search for stockfish executable in bundled resources, near exe, or PATH. """
    tries = []
    tries.append(resource_path("stockfish.exe"))
    tries.append(resource_path("stockfish"))
    # exe dir (onedir case)
    try:
        exe_dir = os.path.dirname(sys.executable)
        tries.append(os.path.join(exe_dir, "stockfish.exe"))
        tries.append(os.path.join(exe_dir, "stockfish"))
    except Exception:
        pass
    tries.append(os.path.abspath("./stockfish.exe"))
    tries.append(os.path.abspath("./stockfish"))

    for p in tries:
        if p and os.path.isfile(p):
            return os.path.abspath(p)
    which = shutil.which("stockfish")
    if which:
        return which
    return None

# ---------- image helpers ----------
def download_piece_images_if_missing(dest_folder="pieces"):
    """ Download piece images only in development mode. If frozen, use bundled pieces. """
    if getattr(sys, "frozen", False):
        path = resource_path(dest_folder)
        return path if os.path.isdir(path) else None

    os.makedirs(dest_folder, exist_ok=True)
    names = ["wK","wQ","wR","wB","wN","wP","bK","bQ","bR","bB","bN","bP"]
    ok = True
    for n in names:
        path = os.path.join(dest_folder, f"{n}.png")
        if not os.path.exists(path) or os.path.getsize(path) < 200:
            try:
                urllib.request.urlretrieve(PIECE_URL.format(n), path)
            except Exception:
                ok = False
    return dest_folder if ok else None

def load_piece_surfaces(folder):
    if not folder:
        return None
    if not os.path.isabs(folder):
        folder = resource_path(folder)
    if not os.path.isdir(folder):
        return None
    try:
        mapping = {}
        for fname in os.listdir(folder):
            if not fname.lower().endswith(".png"):
                continue
            name = os.path.splitext(fname)[0]
            if len(name) != 2:
                continue
            color = name[0].lower(); piece = name[1]
            key = piece.upper() if color == 'w' else piece.lower()
            surf = pygame.image.load(os.path.join(folder, fname)).convert_alpha()
            surf = pygame.transform.smoothscale(surf, (SQ_SIZE, SQ_SIZE))
            mapping[key] = surf
        needed = set(["K","Q","R","B","N","P","k","q","r","b","n","p"])
        if not needed.issubset(set(mapping.keys())):
            return None
        return mapping
    except Exception:
        return None

# ---------- drawing helpers ----------
def square_to_pixel(sq, orientation_white_bottom):
    f = chess.square_file(sq); r = chess.square_rank(sq)
    if orientation_white_bottom:
        x = f * SQ_SIZE; y = (7 - r) * SQ_SIZE
    else:
        x = (7 - f) * SQ_SIZE; y = r * SQ_SIZE
    return int(x), int(y)

def pixel_to_square(mx, my, orientation_white_bottom):
    if mx < 0 or my < 0 or mx >= BOARD_SIZE or my >= BOARD_SIZE:
        return None
    file_px = mx // SQ_SIZE; rank_px = my // SQ_SIZE
    if orientation_white_bottom:
        file = int(file_px); rank = int(7 - rank_px)
    else:
        file = int(7 - file_px); rank = int(rank_px)
    return chess.square(file, rank)

def draw_arrow(surface, start, end, color=SUGGEST_COLOR, width=4):
    pygame.draw.line(surface, color, start, end, width)
    sx, sy = start; ex, ey = end
    angle = math.atan2(ey - sy, ex - sx)
    head = max(10, width*3)
    left = (ex - head * math.cos(angle - math.pi/6), ey - head * math.sin(angle - math.pi/6))
    right = (ex - head * math.cos(angle + math.pi/6), ey - head * math.sin(angle + math.pi/6))
    pygame.draw.polygon(surface, color, [end, left, right])

# ---------- UI widgets ----------
class Button:
    def __init__(self, x, y, w, h, label, font, action=None):
        self.rect = pygame.Rect(x, y, w, h)
        self.label = label
        self.font = font
        self.action = action
        self.hover = False
    def draw(self, surf):
        color = BTN_HOVER if self.hover else BTN_BG
        pygame.draw.rect(surf, color, self.rect, border_radius=8)
        pygame.draw.rect(surf, (0,0,0), self.rect, 2, border_radius=8)
        txt = self.font.render(self.label, True, TEXT_COLOR)
        surf.blit(txt, (self.rect.x + (self.rect.w - txt.get_width())//2, self.rect.y + (self.rect.h - txt.get_height())//2))
    def contains(self, pos):
        return self.rect.collidepoint(pos)

class Slider:
    def __init__(self, x, y, w, h, minv, maxv, val, font):
        self.rect = pygame.Rect(x, y, w, h)
        self.minv = minv; self.maxv = maxv
        self.value = float(val)
        self.font = font
        self.handle_radius = max(8, h//2 + 2)
        self.dragging = False
    def handle_event(self, ev):
        if ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1:
            if self.rect.collidepoint(ev.pos):
                self.dragging = True
                self.set_from_mouse(ev.pos[0]); return True
            hx, hy = self.handle_pos()
            if (ev.pos[0]-hx)**2 + (ev.pos[1]-hy)**2 <= (self.handle_radius*1.5)**2:
                self.dragging = True; return True
        if ev.type == pygame.MOUSEMOTION and self.dragging:
            self.set_from_mouse(ev.pos[0]); return True
        if ev.type == pygame.MOUSEBUTTONUP and ev.button == 1 and self.dragging:
            self.dragging = False; return True
        return False
    def set_from_mouse(self, mx):
        t = (mx - self.rect.x) / float(max(1, self.rect.w))
        t = max(0.0, min(1.0, t))
        self.value = self.minv + t*(self.maxv - self.minv)
    def handle_pos(self):
        t = (self.value - self.minv) / float(self.maxv - self.minv)
        x = int(self.rect.x + t*self.rect.w); y = int(self.rect.y + self.rect.h//2)
        return x, y
    def draw(self, surf, fill=(160,160,160), back=(60,60,60), handle=(240,240,240)):
        pygame.draw.rect(surf, back, self.rect, border_radius=6)
        hx, hy = self.handle_pos()
        filled = pygame.Rect(self.rect.x, self.rect.y, max(1, hx - self.rect.x), self.rect.h)
        pygame.draw.rect(surf, fill, filled, border_radius=6)
        pygame.draw.circle(surf, handle, (hx, hy), self.handle_radius)
        pygame.draw.circle(surf, (40,40,40), (hx, hy), self.handle_radius, 2)
        val_txt = self.font.render(f"{self.value:.1f}s", True, TEXT_COLOR)
        surf.blit(val_txt, (self.rect.x + (self.rect.w - val_txt.get_width())//2, self.rect.y - val_txt.get_height() - 6))

# ---------- App ----------
class ChessApp:
    def __init__(self, engine_path):
        pygame.init()
        self.screen = pygame.display.set_mode(WINDOW_SIZE)
        pygame.display.set_caption(APP_NAME)
        self.clock = pygame.time.Clock()

        # fonts
        self.font_big = pygame.font.SysFont("dejavusans", 20, bold=True)
        self.font_med = pygame.font.SysFont("dejavusans", 16)
        self.font_small = pygame.font.SysFont("dejavusans", 14)
        self.font_footer = pygame.font.SysFont("dejavusans", 12)

        # board & game state
        self.board = chess.Board()
        self.selected = None
        self.last_move = None
        self.redo_stack = []

        # engine
        self.engine_path = engine_path
        self.engine = None
        self._start_engine()
        self.engine_thread = None
        self.engine_thinking = False
        self.engine_start = None
        self.cancel_request = False
        self.last_think_duration = 0.0

        # suggestion (internal only)
        self.suggestion_move = None
        self.suggestion_san = None
        self.suggestion_from_to = None
        self.suggestion_score = None

        # notification
        self.notification = None

        # orientation
        self.orientation_white_bottom = True

        # layout: minimal right panel
        self.panel_x = BOARD_SIZE
        self.margin = 16
        self.inner_w = PANEL_WIDTH - self.margin*2
        y = 12

        self.title_pos = (self.panel_x + self.margin, y)
        y += 36

        # slider + +/- buttons (top)
        self.slider = Slider(self.panel_x + self.margin, y + 6, self.inner_w - 120, 16, MIN_MOVETIME, MAX_MOVETIME, DEFAULT_MOVETIME, self.font_small)
        self.btn_minus = Button(self.panel_x + self.margin + self.inner_w - 120 + 6, y, 40, 30, "-", self.font_med, lambda: self.change_movetime(-0.5))
        self.btn_plus  = Button(self.panel_x + self.margin + self.inner_w - 56, y, 40, 30, "+", self.font_med, lambda: self.change_movetime(0.5))
        self.movetime = DEFAULT_MOVETIME
        self.slider.value = self.movetime
        y += 56

        # progress bar (compact)
        self.progress_rect = pygame.Rect(self.panel_x + self.margin, y-20, self.inner_w, 10)
        # text offset below progress (to avoid overlap)
        self.think_text_offset = 12
        y += 12 + 12  # progress height + spacing

        # buttons stack: user requested; move them 5 pixels down (tiny nudge)
        btn_h = 54
        gap = 12
        btn_x = self.panel_x + self.margin
        btn_w = self.inner_w

        # initial 5px nudge
        y += 5

        self.btn_suggest_white = Button(btn_x, y, btn_w, btn_h, "Suggest for White", self.font_med, lambda: self.request_suggestion_for(chess.WHITE))
        y += btn_h + gap
        self.btn_suggest_black = Button(btn_x, y, btn_w, btn_h, "Suggest for Black", self.font_med, lambda: self.request_suggestion_for(chess.BLACK))
        y += btn_h + gap
        self.btn_apply = Button(btn_x, y, btn_w, btn_h, "Apply Suggestion (manual)", self.font_med, self.apply_suggestion)
        y += btn_h + gap
        self.btn_undo = Button(btn_x, y, btn_w, btn_h, "Undo", self.font_med, lambda: self.undo_plies(1))
        y += btn_h + gap
        self.btn_redo = Button(btn_x, y, btn_w, btn_h, "Redo", self.font_med, self.redo_plies)
        y += btn_h + gap
        # Toggle perspective (show current view)
        self.btn_toggle_persp = Button(btn_x, y, btn_w, btn_h, "View: White", self.font_med, self.toggle_perspective)
        y += btn_h + gap

        # collect for hover/click
        self.buttons = [
            self.btn_minus, self.btn_plus,
            self.btn_suggest_white, self.btn_suggest_black, self.btn_apply,
            self.btn_undo, self.btn_redo, self.btn_toggle_persp
        ]

        # load piece images if possible
        folder = resource_path("pieces") if getattr(sys, "frozen", False) else download_piece_images_if_missing("pieces")
        self.pieces = load_piece_surfaces(folder)
        self.use_images = bool(self.pieces)

        self.running = True

    def _start_engine(self):
        try:
            if not self.engine_path:
                self.engine = None
                return

            # On Windows, CREATE_NO_WINDOW prevents the child process (stockfish) from opening a console window.
            # Use getattr(subprocess, "CREATE_NO_WINDOW", 0) so it also runs safely on non-Windows (where that flag doesn't exist).
            creation = getattr(subprocess, "CREATE_NO_WINDOW", 0)

            # Pass creationflags into popen_uci so subprocess.Popen gets it.
            self.engine = chess.engine.SimpleEngine.popen_uci(self.engine_path, creationflags=creation)

            try:
                threads = max(1, (os.cpu_count() or 2) - 1)
                self.engine.configure({"Threads": threads, "Hash": HASH_MB})
            except Exception:
                pass
        except Exception:
            # if engine failed to start, set to None but keep GUI functional
            self.engine = None

    # notifications
    def notify(self, text, color=NOTE_COLOR, ttl=3.0):
        self.notification = (text, color, time.time() + ttl)

    def draw_notification(self, surf, x, y, width):
        if not self.notification: return
        txt, color, expiry = self.notification
        if time.time() > expiry:
            self.notification = None; return
        r = pygame.Rect(x, y, width, 36)
        pygame.draw.rect(surf, BOX_BG, r, border_radius=8)
        pygame.draw.rect(surf, (0,0,0), r, 2, border_radius=8)
        tt = self.font_small.render(txt, True, color)
        surf.blit(tt, (r.x + 8, r.y + (r.h - tt.get_height())//2))

    # perspective toggle
    def toggle_perspective(self):
        self.orientation_white_bottom = not self.orientation_white_bottom
        if self.orientation_white_bottom:
            self.btn_toggle_persp.label = "View: White"
            self.notify("View: White bottom", ttl=1.0)
        else:
            self.btn_toggle_persp.label = "View: Black"
            self.notify("View: Black bottom", ttl=1.0)

    # suggestion workflow (engine computes moves; panel does not show SAN/score)
    def request_suggestion_for(self, for_color, override_time=None):
        if self.board.turn != for_color:
            color_name = "White" if for_color == chess.WHITE else "Black"
            self.notify(f"Error: It's not {color_name}'s turn!", color=ERR_COLOR, ttl=3.0)
            return
        if self.engine is None:
            self.notify("No engine available", color=ERR_COLOR, ttl=3.0); return
        if self.engine_thinking:
            self.notify("Engine already thinking", ttl=2.0); return
        self.suggestion_move = None; self.suggestion_san = None; self.suggestion_from_to = None; self.suggestion_score = None
        self.cancel_request = False
        tlimit = override_time if override_time is not None else self.movetime
        self.engine_thread = threading.Thread(target=self._engine_worker_suggest, args=(for_color, tlimit), daemon=True)
        self.engine_thread.start()

    def _engine_worker_suggest(self, for_color, tlimit):
        try:
            self.engine_thinking = True
            self.engine_start = time.time()
            board_copy = self.board.copy()
            board_copy.turn = for_color
            limit = chess.engine.Limit(time=tlimit)
            res = self.engine.play(board_copy, limit)
            elapsed = time.time() - self.engine_start
            self.last_think_duration = elapsed
            if self.cancel_request:
                self.engine_thinking = False
                return
            if res and res.move:
                self.suggestion_move = res.move
                try:
                    self.suggestion_san = board_copy.san(res.move)
                except Exception:
                    self.suggestion_san = str(res.move)
                self.suggestion_from_to = (res.move.from_square, res.move.to_square)
                try:
                    info = self.engine.analyse(board_copy, chess.engine.Limit(depth=1))
                    if "score" in info:
                        sc = info["score"]
                        if sc.is_mate():
                            self.suggestion_score = f"# {sc.mate()}"
                        else:
                            self.suggestion_score = str(sc.pov(for_color).score())
                except Exception:
                    self.suggestion_score = None
            else:
                self.suggestion_move = None
        except Exception:
            self.notify("Engine error", color=ERR_COLOR, ttl=3.0)
        finally:
            self.engine_thinking = False

    def apply_suggestion(self):
        if not self.suggestion_move:
            self.notify("No suggestion to apply", color=ERR_COLOR, ttl=2.5); return
        if self.suggestion_move not in self.board.legal_moves:
            self.notify("Suggestion not legal now", color=ERR_COLOR, ttl=3.0); return
        self.redo_stack.clear()
        self.board.push(self.suggestion_move)
        self.last_move = (self.suggestion_move.from_square, self.suggestion_move.to_square)
        self.notify("Suggestion applied", ttl=1.6)

    def cancel_thinking(self):
        if not self.engine_thinking:
            self.notify("No active thinking job", ttl=1.2); return
        self.cancel_request = True
        self.notify("Cancel requested", ttl=1.6)

    # undo / redo
    def undo_plies(self, count=1):
        if self.engine_thinking:
            self.notify("Cannot undo while engine thinking", color=ERR_COLOR, ttl=3.0); return
        for _ in range(count):
            if not self.board.move_stack:
                break
            mv = self.board.pop()
            self.redo_stack.append(mv)
        self.suggestion_move = None; self.suggestion_san = None; self.suggestion_from_to = None; self.suggestion_score = None
        if self.board.move_stack:
            last = self.board.move_stack[-1]; self.last_move = (last.from_square, last.to_square)
        else:
            self.last_move = None

    def redo_plies(self):
        if self.engine_thinking:
            self.notify("Cannot redo while engine thinking", color=ERR_COLOR, ttl=3.0); return
        if not self.redo_stack:
            self.notify("Nothing to redo", ttl=2.0); return
        mv = self.redo_stack.pop()
        if mv in self.board.legal_moves:
            self.board.push(mv); self.last_move = (mv.from_square, mv.to_square)
        else:
            self.notify("Redo not legal now", color=ERR_COLOR, ttl=2.8)

    # board interaction (two-click move)
    def handle_board_click(self, pos):
        sq = pixel_to_square(pos[0], pos[1], self.orientation_white_bottom)
        if sq is None:
            return
        piece = self.board.piece_at(sq)
        if self.selected is None:
            if piece:
                self.selected = sq
        else:
            mv = chess.Move(self.selected, sq)
            p = self.board.piece_at(self.selected)
            if p and p.piece_type == chess.PAWN:
                tr = chess.square_rank(mv.to_square)
                if (p.color == chess.WHITE and tr == 7) or (p.color == chess.BLACK and tr == 0):
                    mv = chess.Move(mv.from_square, mv.to_square, promotion=chess.QUEEN)
            if mv in self.board.legal_moves:
                self.redo_stack.clear()
                self.board.push(mv)
                self.last_move = (mv.from_square, mv.to_square)
                self.suggestion_move = None; self.suggestion_san = None; self.suggestion_from_to = None; self.suggestion_score = None
            else:
                self.notify("Illegal move attempted", color=ERR_COLOR, ttl=2.2)
            if self.board.piece_at(sq):
                self.selected = sq
            else:
                self.selected = None

    def change_movetime(self, delta):
        self.movetime = max(MIN_MOVETIME, min(MAX_MOVETIME, round(self.movetime + delta, 1)))
        self.slider.value = self.movetime

    # drawing
    def draw_board(self):
        for r in range(8):
            for f in range(8):
                c = LIGHT_COLOR if (r + f) % 2 == 0 else DARK_COLOR
                pygame.draw.rect(self.screen, c, pygame.Rect(f*SQ_SIZE, r*SQ_SIZE, SQ_SIZE, SQ_SIZE))
        if self.last_move:
            for sq in self.last_move:
                x, y = square_to_pixel(sq, self.orientation_white_bottom)
                surf = pygame.Surface((SQ_SIZE, SQ_SIZE), pygame.SRCALPHA)
                surf.fill(LASTMOVE_COLOR)
                self.screen.blit(surf, (x, y))
        if self.suggestion_from_to:
            fr, to = self.suggestion_from_to
            sx, sy = square_to_pixel(fr, self.orientation_white_bottom)
            tx, ty = square_to_pixel(to, self.orientation_white_bottom)
            t = time.time(); pulse = 0.6 + 0.4 * math.sin(t * 5.0)
            pygame.draw.rect(self.screen, SUGGEST_COLOR, pygame.Rect(sx, sy, SQ_SIZE, SQ_SIZE), 3 + int(2*pulse))
            overlay = pygame.Surface((SQ_SIZE, SQ_SIZE), pygame.SRCALPHA)
            overlay.fill((SUGGEST_COLOR[0], SUGGEST_COLOR[1], SUGGEST_COLOR[2], 100))
            self.screen.blit(overlay, (tx, ty))
            draw_arrow(self.screen, (sx + SQ_SIZE//2, sy + SQ_SIZE//2), (tx + SQ_SIZE//2, ty + SQ_SIZE//2), SUGGEST_COLOR, 3)
        if self.selected is not None:
            sx, sy = square_to_pixel(self.selected, self.orientation_white_bottom)
            pygame.draw.rect(self.screen, HIGHLIGHT_COLOR, pygame.Rect(sx, sy, SQ_SIZE, SQ_SIZE), 4)
            for mv in self.board.legal_moves:
                if mv.from_square == self.selected:
                    dx, dy = square_to_pixel(mv.to_square, self.orientation_white_bottom)
                    pygame.draw.circle(self.screen, (20,200,80), (dx + SQ_SIZE//2, dy + SQ_SIZE//2), max(4, SQ_SIZE//12))
        for sq in chess.SQUARES:
            p = self.board.piece_at(sq)
            if not p: continue
            x, y = square_to_pixel(sq, self.orientation_white_bottom)
            if self.use_images and p.symbol() in self.pieces:
                self.screen.blit(self.pieces[p.symbol()], (x, y))
            else:
                self._draw_simple_piece(x, y, p)

    def _draw_simple_piece(self, x, y, piece):
        rect = pygame.Rect(x, y, SQ_SIZE, SQ_SIZE)
        fill = (245,245,245) if piece.color == chess.WHITE else (25,25,25)
        outline = (20,20,20) if piece.color == chess.WHITE else (245,245,245)
        cx = rect.x + rect.w//2; cy = rect.y + rect.h//2
        if piece.piece_type == chess.PAWN:
            pygame.draw.circle(self.screen, fill, (cx, cy - 8), rect.w//7)
            pygame.draw.circle(self.screen, outline, (cx, cy - 8), rect.w//7, 2)
        else:
            pygame.draw.circle(self.screen, fill, (cx, cy - 6), rect.w//6)
            pygame.draw.circle(self.screen, outline, (cx, cy - 6), rect.w//6, 2)

    def draw_panel(self):
        panel_x = self.panel_x
        pygame.draw.rect(self.screen, PANEL_BG, pygame.Rect(panel_x, 0, PANEL_WIDTH, BOARD_SIZE))
        # title
        title = self.font_big.render("Menu", True, TEXT_COLOR)
        self.screen.blit(title, self.title_pos)
        # slider and +/- buttons
        self.slider.draw(self.screen)
        self.btn_minus.draw(self.screen); self.btn_plus.draw(self.screen)
        # progress bar
        pygame.draw.rect(self.screen, BOX_BG, self.progress_rect, border_radius=6)
        if self.engine_thinking and self.engine_start:
            elapsed = time.time() - self.engine_start
            denom = max(0.0001, self.movetime)
            t = min(1.0, elapsed / denom)
            fill = pygame.Rect(self.progress_rect.x, self.progress_rect.y, int(self.progress_rect.w * t), self.progress_rect.h)
            pygame.draw.rect(self.screen, SUGGEST_COLOR, fill, border_radius=6)
            info_txt = f"Thinking {elapsed:.2f}s / {self.movetime:.1f}s"
        else:
            info_txt = f"Last think: {self.last_think_duration:.2f}s"
        info = self.font_small.render(info_txt, True, TEXT_COLOR)
        self.screen.blit(info, (self.progress_rect.x + 4, self.progress_rect.y + self.progress_rect.h + self.think_text_offset))

        # draw stacked buttons
        for b in [self.btn_suggest_white, self.btn_suggest_black, self.btn_apply, self.btn_undo, self.btn_redo, self.btn_toggle_persp]:
            b.draw(self.screen)

        # footer (small centered line at bottom of panel)
        footer_pad = 12
        footer_y = BOARD_SIZE - footer_pad - 18
        footer_surf = self.font_footer.render(FOOTER_TEXT, True, NOTE_COLOR)
        fx = panel_x + (PANEL_WIDTH - footer_surf.get_width()) // 2
        self.screen.blit(footer_surf, (fx, footer_y))

    # main loop
    def run(self):
        while self.running:
            self.clock.tick(FPS)
            mx, my = pygame.mouse.get_pos()
            for b in self.buttons:
                b.hover = b.rect.collidepoint((mx, my))
            for ev in pygame.event.get():
                if ev.type == pygame.QUIT:
                    self.running = False; break
                if ev.type == pygame.KEYDOWN:
                    mods = pygame.key.get_mods()
                    if ev.key == pygame.K_z:
                        if mods & pygame.KMOD_CTRL: self.undo_plies(2)
                        else: self.undo_plies(1)
                    elif ev.key == pygame.K_y and (mods & pygame.KMOD_CTRL):
                        self.redo_plies()
                    elif ev.key == pygame.K_s and (mods & pygame.KMOD_CTRL):
                        self.save_pgn()
                    elif ev.key == pygame.K_c:
                        self.cancel_thinking()
                if ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1:
                    pos = ev.pos
                    if pos[0] >= BOARD_SIZE:
                        # panel clicks
                        if self.btn_plus.contains(pos): self.btn_plus.action()
                        elif self.btn_minus.contains(pos): self.btn_minus.action()
                        else:
                            handled = False
                            for b in [self.btn_suggest_white, self.btn_suggest_black, self.btn_apply, self.btn_undo, self.btn_redo, self.btn_toggle_persp]:
                                if b.contains(pos):
                                    if callable(b.action): b.action()
                                    handled = True; break
                            if not handled:
                                if self.slider.handle_event(ev):
                                    self.movetime = round(self.slider.value, 1)
                    else:
                        self.handle_board_click(pos)
                if ev.type in (pygame.MOUSEBUTTONUP, pygame.MOUSEMOTION):
                    if self.slider.handle_event(ev):
                        self.movetime = round(self.slider.value, 1)

            # sync slider
            self.slider.value = self.movetime

            # expire notifications
            if self.notification and time.time() > self.notification[2]:
                self.notification = None

            # draw
            self.screen.fill((10,10,10))
            self.draw_board()
            self.draw_panel()
            pygame.display.flip()

        # cleanup engine
        try:
            if self.engine: self.engine.quit()
        except Exception:
            pass
        pygame.quit()

    # optional: save PGN
    def save_pgn(self):
        try:
            g = chess.pgn.Game.from_board(self.board)
            g.headers["Event"] = "Session"
            g.headers["Date"] = datetime.utcnow().strftime("%Y.%m.%d")
            fn = f"game_{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}.pgn"
            with open(fn, "w", encoding="utf-8") as f:
                f.write(str(g))
            self.notify(f"Saved PGN: {fn}", ttl=2.6)
        except Exception:
            self.notify("Save failed", color=ERR_COLOR, ttl=3.0)

# ---------- ENTRY ----------
def main():
    engine_path = find_stockfish()
    # if no engine found, we still open GUI but show notification; app is usable for manual moves
    app = ChessApp(engine_path)
    app.run()

if __name__ == "__main__":
    main()
