"""Course 6: Strategy - positional themes illustrated with engine-selected positions from Zorix games."""
import json, os, random
import chess, chess.engine
import cb
from cb import *

CACHE = os.path.join(os.path.dirname(__file__), "../cache/strategy.json")

def is_passed(b, sq, color):
    f, r = chess.square_file(sq), chess.square_rank(sq)
    for ff in (f - 1, f, f + 1):
        if not 0 <= ff < 8:
            continue
        rng = range(r + 1, 8) if color == chess.WHITE else range(0, r)
        for rr in rng:
            p = b.piece_at(chess.square(ff, rr))
            if p and p.piece_type == chess.PAWN and p.color != color:
                return False
    return True

def open_file(b, f):
    return not any(b.piece_type_at(chess.square(f, r)) == chess.PAWN for r in range(8))

def material(b, color):
    v = {chess.PAWN: 1, chess.KNIGHT: 3, chess.BISHOP: 3, chess.ROOK: 5, chess.QUEEN: 9}
    return sum(v[p.piece_type] * (1 if p.color == color else -1) for p in b.piece_map().values() if p.piece_type != chess.KING)

def isolated(b, sq):
    f = chess.square_file(sq); c = b.color_at(sq)
    return not any(b.piece_type_at(chess.square(ff, r)) == chess.PAWN and b.color_at(chess.square(ff, r)) == c
                   for ff in (f - 1, f + 1) if 0 <= ff < 8 for r in range(8))

def detect(b, m):
    """Themes illustrated by move m in position b."""
    t = set()
    us = b.turn
    p = b.piece_at(m.from_square)
    to = m.to_square
    tf, tr = chess.square_file(to), chess.square_rank(to)
    rel = tr if us == chess.WHITE else 7 - tr
    after = b.copy(); after.push(m)
    if p.piece_type == chess.ROOK and chess.square_file(m.from_square) != tf and open_file(b, tf) and not b.is_capture(m):
        t.add("openFile")
    if p.piece_type == chess.ROOK and rel == 6 and chess.square_rank(m.from_square) != tr:
        t.add("seventhRank")
    if p.piece_type == chess.KNIGHT and rel in (3, 4, 5) and 2 <= tf <= 5:
        guarded = any(after.piece_type_at(s) == chess.PAWN and after.color_at(s) == us for s in after.attackers(us, to))
        attackable = False
        for ff in (tf - 1, tf + 1):
            if not 0 <= ff < 8:
                continue
            for r in (range(tr + 1, 8) if us == chess.WHITE else range(0, tr)):
                q = after.piece_at(chess.square(ff, r))
                if q and q.piece_type == chess.PAWN and q.color != us:
                    attackable = True
        if guarded and not attackable:
            t.add("outpost")
    if p.piece_type == chess.PAWN and not b.is_capture(m):
        if is_passed(b, m.from_square, us) and rel >= 4:
            t.add("passedPawn")
        elif not is_passed(b, m.from_square, us) and is_passed(after, to, us):
            t.add("createPasser")
    if b.is_capture(m) and material(b, us) >= 3 and b.piece_at(to) and b.piece_at(to).piece_type == p.piece_type:
        t.add("tradeWhenAhead")
    if p.piece_type in (chess.KNIGHT, chess.QUEEN) and to in (chess.D4, chess.E4, chess.D5, chess.E5) and not b.is_capture(m):
        t.add("centralize")
    if b.is_capture(m) and b.piece_type_at(to) == chess.PAWN and isolated(b, to):
        t.add("isolatedPawn")
    queens = len(b.pieces(chess.QUEEN, True)) + len(b.pieces(chess.QUEEN, False))
    if p.piece_type == chess.KING and queens == 0 and len(b.piece_map()) <= 14:
        center_before = chess.square_distance(m.from_square, chess.E4) + chess.square_distance(m.from_square, chess.D5)
        center_after = chess.square_distance(to, chess.E4) + chess.square_distance(to, chess.D5)
        if center_after < center_before:
            t.add("activeKing")
    if p.piece_type == chess.ROOK:
        same = [s for s in b.pieces(chess.ROOK, us) if s != m.from_square and chess.square_file(s) == tf]
        if same and chess.square_file(m.from_square) != tf:
            t.add("doubleRooks")
    return t

def collect(engine, per_theme=6, max_games=400, seed=5):
    rng = random.Random(seed)
    found = {}
    if os.path.exists(CACHE):
        found = json.load(open(CACHE))
    themes = ["openFile", "seventhRank", "outpost", "passedPawn", "createPasser", "tradeWhenAhead", "centralize", "isolatedPawn", "activeKing", "doubleRooks"]
    if all(len(found.get(t, [])) >= per_theme for t in themes):
        return found
    from openings import OPENINGS
    for g in range(max_games):
        b = chess.Board()
        line = rng.choice(OPENINGS)[5].split()
        for s in line:
            b.push_san(s)
        engine.configure({"UCI_LimitStrength": True, "UCI_Elo": rng.choice([2200, 2400, 2600])})
        while not b.is_game_over() and b.ply() < 130:
            b.push(engine.play(b, chess.engine.Limit(nodes=60000)).move)
            if b.ply() < 14 or b.ply() % 3 or b.is_game_over():
                continue
            engine.configure({"UCI_LimitStrength": False})
            infos = engine.analyse(b, chess.engine.Limit(depth=14), multipv=2)
            engine.configure({"UCI_LimitStrength": True})
            if len(infos) < 2 or "pv" not in infos[0]:
                continue
            s0 = infos[0]["score"].pov(b.turn); s1 = infos[1]["score"].pov(b.turn)
            if s0.is_mate() or s1.is_mate() or abs(s0.score()) > 400:
                continue
            if s0.score() - s1.score() < 40:
                continue  # the best move should clearly stand out
            m = infos[0]["pv"][0]
            for t in detect(b, m):
                lst = found.setdefault(t, [])
                if len(lst) < per_theme and b.fen() not in lst:
                    lst.append(b.fen())
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        json.dump(found, open(CACHE, "w"), indent=0)
        if all(len(found.get(t, [])) >= per_theme for t in themes):
            break
    engine.configure({"UCI_LimitStrength": False})
    return found

LESSONS = [
    ("openFile", T("Rooks on open files", "رخ روی ستون باز", "قەڵا لەسەر ستوونی کراوە"),
     T("Rooks need open lines. A file with no pawns is an 'open file': a rook placed there attacks deep into the enemy camp, often reaching the 7th rank. Put your rooks on open (or half-open) files before your opponent does.",
       "رخ‌ها به خط باز نیاز دارند. ستونی که هیچ سربازی ندارد «ستون باز» است: رخ روی آن تا عمق اردوی حریف حمله می‌کند و اغلب به ردیف هفتم می‌رسد. رخ‌هایت را زودتر از حریف روی ستون‌های باز (یا نیمه‌باز) بگذار.",
       "قەڵاکان پێویستیان بە هێڵی کراوەیە. ستوونێک کە هیچ سەربازێکی تێدا نییە «ستوونی کراوە»یە: قەڵا لەوێ هێرش دەکاتە قووڵایی ئۆردووگای ڕکابەر و زۆرجار دەگاتە ڕیزی حەوتەم. قەڵاکانت پێش ڕکابەر بخە سەر ستوونە کراوە (یان نیوە-کراوە)کان.")),
    ("seventhRank", T("The seventh rank", "ردیف هفتم", "ڕیزی حەوتەم"),
     T("A rook on the 7th rank attacks the pawns that are still on their starting squares and traps the enemy king on the back rank. Two rooks on the 7th ('pigs on the seventh') are often decisive.",
       "رخ در ردیف هفتم به سربازهایی که هنوز در خانه‌ی شروع‌اند حمله می‌کند و شاه حریف را در ردیف آخر محبوس می‌کند. دو رخ در ردیف هفتم اغلب سرنوشت‌ساز است.",
       "قەڵا لە ڕیزی حەوتەمدا هێرش دەکاتە سەر ئەو سەربازانەی هێشتا لە خانەی دەستپێکدان و شای ڕکابەر لە ڕیزی دواوەدا دەگرێت. دوو قەڵا لە ڕیزی حەوتەمدا زۆرجار یەکلاکەرەوەیە.")),
    ("outpost", T("Knight outposts", "پایگاه اسب", "بنکەی ئەسپ"),
     T("An outpost is a square in the enemy half that your pawn protects and no enemy pawn can ever attack. A knight on an outpost is a monster: it cannot be chased away and it controls key squares.",
       "پایگاه خانه‌ای در نیمه‌ی حریف است که سرباز تو از آن محافظت می‌کند و هیچ سرباز حریفی هرگز نمی‌تواند به آن حمله کند. اسب روی پایگاه یک هیولاست: فراری داده نمی‌شود و خانه‌های کلیدی را کنترل می‌کند.",
       "بنکە خانەیەکە لە نیوەی ڕکابەردا کە سەربازەکەت دەیپارێزێت و هیچ سەربازێکی ڕکابەر هەرگیز ناتوانێت هێرشی بکاتە سەر. ئەسپ لەسەر بنکە دێوێکە: ڕاو نانرێت و خانە گرنگەکان کۆنترۆڵ دەکات.")),
    ("passedPawn", T("Passed pawns must be pushed", "سرباز رونده باید جلو برود", "سەربازی ڕاکەر دەبێت پاڵ بنرێت"),
     T("A passed pawn has no enemy pawns in front of it or on the neighbouring files. It ties the opponent down and becomes stronger with every step. 'Passed pawns must be pushed' — especially when the pieces support them.",
       "سرباز رونده سربازی است که هیچ سرباز حریفی جلویش یا در ستون‌های کناری‌اش نیست. حریف را درگیر می‌کند و با هر قدم قوی‌تر می‌شود. «سرباز رونده باید جلو برود» — به‌خصوص وقتی مهره‌ها از آن پشتیبانی می‌کنند.",
       "سەربازی ڕاکەر هیچ سەربازێکی ڕکابەری لە بەردەم یان لە ستوونە دراوسێکانیدا نییە. ڕکابەر سەرقاڵ دەکات و بە هەر هەنگاوێک بەهێزتر دەبێت. «سەربازی ڕاکەر دەبێت پاڵ بنرێت» — بەتایبەت کاتێک مۆرەکان پشتگیری دەکەن.")),
    ("createPasser", T("Creating a passed pawn", "ساختن سرباز رونده", "دروستکردنی سەربازی ڕاکەر"),
     T("With a pawn majority on one wing (for example three pawns against two), advance the pawn that has no opponent in front of it first. The majority turns into a passed pawn.",
       "با اکثریت سربازی در یک جناح (مثلاً سه سرباز در برابر دو)، اول سربازی را جلو ببر که حریفی روبه‌رویش نیست. اکثریت به سرباز رونده تبدیل می‌شود.",
       "بە زۆرینەی سەربازی لە لایەکدا (بۆ نموونە سێ سەرباز بەرامبەر دوو)، سەرەتا ئەو سەربازە پێش بخە کە ڕکابەری لە بەردەمدا نییە. زۆرینەکە دەبێتە سەربازی ڕاکەر.")),
    ("tradeWhenAhead", T("Trade pieces when you are ahead", "وقتی جلو هستی مهره معاوضه کن", "کاتێک لە پێشیت مۆرە ئاڵوگۆڕ بکە"),
     T("When you are ahead in material, trade pieces (not pawns). Every trade makes your extra material count for more and reduces the opponent's counter-chances.",
       "وقتی از نظر مهره جلو هستی، مهره‌ها را معاوضه کن (نه سربازها). هر معاوضه مهره‌ی اضافه‌ی تو را ارزشمندتر می‌کند و فرصت‌های ضدحمله‌ی حریف را کم می‌کند.",
       "کاتێک لە مۆرەدا لە پێشیت، مۆرەکان ئاڵوگۆڕ بکە (نەک سەربازەکان). هەر ئاڵوگۆڕێک مۆرە زیادەکەت بەنرختر دەکات و دەرفەتەکانی دژەهێرشی ڕکابەر کەم دەکاتەوە.")),
    ("centralize", T("Centralize your pieces", "مهره‌ها را در مرکز مستقر کن", "مۆرەکانت لە ناوەنددا جێگیر بکە"),
     T("A knight on the rim is dim: in the center a knight controls eight squares, on the edge only four. Queens and knights love central squares where they reach both wings quickly.",
       "اسب کنار صفحه کم‌فروغ است: در مرکز هشت خانه را کنترل می‌کند و در لبه فقط چهار خانه. وزیر و اسب عاشق خانه‌های مرکزی‌اند که از آن‌جا سریع به هر دو جناح می‌رسند.",
       "ئەسپ لە لێوارەوە کز دەبێت: لە ناوەنددا هەشت خانە کۆنترۆڵ دەکات و لە لێواردا تەنها چوار. وەزیر و ئەسپ خانە ناوەندییەکانیان خۆشدەوێت کە لەوێوە خێرا دەگەنە هەردوو لا.")),
    ("isolatedPawn", T("Attacking weak pawns", "حمله به سربازهای ضعیف", "هێرش بۆ سەر سەربازە لاوازەکان"),
     T("A pawn with no friendly pawns on the neighbouring files is 'isolated': no pawn can ever defend it, so pieces must. Attack it, blockade the square in front of it, and win it.",
       "سربازی که در ستون‌های کناری‌اش سرباز خودی ندارد «منزوی» است: هیچ سربازی نمی‌تواند از آن دفاع کند، پس مهره‌ها باید این کار را بکنند. به آن حمله کن، خانه‌ی جلویش را ببند و آن را ببر.",
       "سەربازێک کە لە ستوونە دراوسێکانیدا سەربازی خۆیی نییە «گۆشەگیر»ە: هیچ سەربازێک ناتوانێت بەرگری لێ بکات، بۆیە مۆرەکان دەبێت ئەوە بکەن. هێرشی بکە سەر، خانەی بەردەمی بگرە و بیبەرەوە.")),
    ("activeKing", T("The active king in the endgame", "شاه فعال در آخر بازی", "شای چالاک لە کۆتایی یاریدا"),
     T("When the queens are gone, the king is no longer a target but a strong piece. March it to the center and towards the pawns — in the endgame the king is worth about three pawns.",
       "وقتی وزیرها از صفحه رفته‌اند، شاه دیگر هدف نیست بلکه مهره‌ای قوی است. آن را به مرکز و به سمت سربازها ببر — در آخر بازی ارزش شاه حدود سه سرباز است.",
       "کاتێک وەزیرەکان نەماون، شا چیتر ئامانج نییە بەڵکو مۆرەیەکی بەهێزە. بیبە بۆ ناوەند و بەرەو سەربازەکان — لە کۆتایی یاریدا بەهای شا نزیکەی سێ سەربازە.")),
    ("doubleRooks", T("Doubling rooks", "دوبله کردن رخ‌ها", "دووبارەکردنی قەڵاکان"),
     T("Two rooks on the same file support each other and double the pressure. Doubling on an open file often wins control of it for good.",
       "دو رخ روی یک ستون از هم پشتیبانی می‌کنند و فشار را دو برابر می‌کنند. دوبله کردن روی ستون باز اغلب کنترل آن را برای همیشه به دست می‌آورد.",
       "دوو قەڵا لەسەر یەک ستوون پشتگیری یەکتر دەکەن و فشارەکە دوو هێندە دەکەن. دووبارەکردن لەسەر ستوونێکی کراوە زۆرجار کۆنترۆڵی بۆ هەمیشە بەدەست دەهێنێت.")),
]

FIND = T("Find the strongest positional move.", "قوی‌ترین حرکت راهبردی را پیدا کن.", "بەهێزترین جوڵەی ستراتیژی بدۆزەرەوە.")

def build():
    found = collect(cb.ENGINE)
    lessons = []
    for key, title, text in LESSONS:
        fens = found.get(key, [])
        if len(fens) < 2:
            raise ContentError(f"not enough strategy positions for {key}")
        half = (len(fens) + 1) // 2
        lessons.append(lesson(f"strategy.{key}.1", title, text, 3,
                              [theory(text, fen=fens[0])] + [best(f, FIND, margin=30) for f in fens[:half]]))
        lessons.append(lesson(f"strategy.{key}.2", T(title["en"] + " — practice", title["fa"] + " — تمرین", title["ckb"] + " — ڕاهێنان"),
                              T("Apply the idea in new positions from real games.", "ایده را در وضعیت‌های تازه از بازی‌های واقعی به کار ببر.", "بیرۆکەکە لە دۆخی نوێی یارییە ڕاستەقینەکاندا بەکاربهێنە."),
                              3, [best(f, FIND, margin=30) for f in fens[half:]] or [best(fens[0], FIND, margin=30)]))
    return course("strategy", T("Strategy", "استراتژی", "ستراتیژی"),
        T("How strong players think when there is no tactic: open files, outposts, passed pawns, weak pawns and the active king.",
          "بازیکنان قوی وقتی تاکتیکی در کار نیست چطور فکر می‌کنند: ستون باز، پایگاه، سرباز رونده، سرباز ضعیف و شاه فعال.",
          "یاریزانە بەهێزەکان کاتێک تاکتیک نییە چۆن بیر دەکەنەوە: ستوونی کراوە، بنکە، سەربازی ڕاکەر، سەربازی لاواز و شای چالاک."),
        [chapter("positional", T("Positional play", "بازی پوزیسیونی", "یاریی پۆزیسیۆنی"), lessons)])
