"""Course 4: Endgames - pawn, rook, queen and minor-piece endgames, played out against Zorix."""
import chess, chess.engine
import cb
from cb import *
from course_tactics import fa_num, ck_num, EXAMPLE, demo_theory

WIN = T("Win: promote or checkmate — Zorix defends.", "ببر: ارتقا بده یا مات کن — Zorix دفاع می‌کند.", "بیبەرەوە: بەرزی بکەرەوە یان کش‌مات بکە — Zorix بەرگری دەکات.")
PROMO = T("Promote the pawn safely — Zorix defends.", "سرباز را با خیال راحت ارتقا بده — Zorix دفاع می‌کند.", "سەربازەکە بە سەلامەتی بەرز بکەرەوە — Zorix بەرگری دەکات.")
HOLD = T("Hold the draw for 20 moves — Zorix tries to win.", "۲۰ حرکت تساوی را نگه دار — Zorix برای بردن تلاش می‌کند.", "بۆ ٢٠ جوڵە یەکسانییەکە بپارێزە — Zorix هەوڵی بردنەوە دەدات.")
BEST = T("Find the only move that keeps the win (or the draw).", "تنها حرکتی را پیدا کن که برد (یا تساوی) را حفظ می‌کند.", "تاکە جوڵەیەک بدۆزەرەوە کە بردنەوە (یان یەکسانی) دەپارێزێت.")

def evaluate(fen, depth=26):
    b = chess.Board(fen)
    s = cb.ENGINE.analyse(b, chess.engine.Limit(depth=depth))["score"].pov(b.turn)
    if s.is_mate():
        return 10000 if s.mate() > 0 else -10000
    return s.score()

def winning_quiz(fen, question, yes, no, winning_means_yes=True, explain_yes=None, explain_no=None):
    """Answer derived from the engine: winning (for White) vs not."""
    b = chess.Board(fen)
    ev = evaluate(fen)
    white_ev = ev if b.turn == chess.WHITE else -ev
    wins = white_ev >= 400
    ans = 0 if wins == winning_means_yes else 1
    return quiz(question, [yes, no], ans, explain_yes if ans == 0 else explain_no, fen=fen)

def only_moves(fen, prompt, explain=None):
    """A 'best' step: accept only moves that keep the result (win stays win, draw stays draw)."""
    b = chess.Board(fen)
    infos = cb.ENGINE.analyse(b, chess.engine.Limit(depth=24), multipv=min(8, b.legal_moves.count()))
    def val(s):
        s = s.pov(b.turn)
        return 10000 - abs(s.mate()) if s.is_mate() and s.mate() > 0 else (-10000 if s.is_mate() else s.score())
    top = val(infos[0]["score"])
    if top >= 300:
        accept = [i["pv"][0].uci() for i in infos if val(i["score"]) >= 300]
    else:
        accept = [i["pv"][0].uci() for i in infos if val(i["score"]) >= top - 50]
    if len(accept) == b.legal_moves.count():
        raise ContentError(f"every move keeps the result in {fen}")
    return best(fen, prompt, explain, accept=accept)

def kpk_only_moves(count, seed_squares=None):
    """K+P vs K, White to move, where only one or two king moves win (the others draw)."""
    out = []
    tries = 0
    while len(out) < count and tries < 400:
        tries += 1
        fen = random_position([("K", None), ("P", [f"{f}{r}" for f in "bcdefg" for r in "2345"]), ("k", None)])
        b = chess.Board(fen)
        if b.is_check() or chess.square_distance(b.king(chess.WHITE), b.king(chess.BLACK)) < 2:
            continue
        infos = cb.ENGINE.analyse(b, chess.engine.Limit(depth=20), multipv=min(8, b.legal_moves.count()))
        def win(i):
            s = i["score"].pov(chess.WHITE)
            return s.is_mate() and s.mate() > 0 or (s.score() or 0) >= 400
        wins = [i for i in infos if win(i)]
        draws = [i for i in infos if not win(i)]
        if 1 <= len(wins) <= 2 and draws and all(b.piece_at(i["pv"][0].from_square).piece_type == chess.KING for i in wins):
            out.append(fen)
    if len(out) < count:
        raise ContentError("not enough KPK only-move positions")
    return out

def knight_vs_pawn(count):
    """White knight (and a far king) stops a black pawn on its 2nd/3rd rank: engine-confirmed draws."""
    out = []
    tries = 0
    while len(out) < count and tries < 500:
        tries += 1
        fen = random_position([("K", [f"{f}{r}" for f in "efgh" for r in "12345"]), ("N", None),
                               ("p", [f"{f}{r}" for f in "abcd" for r in "23"]), ("k", [f"{f}{r}" for f in "abcde" for r in "2345"])])
        b = chess.Board(fen)
        if b.is_check() or any(b.piece_at(s) and b.piece_at(s).piece_type == chess.PAWN and b.piece_at(s).color == chess.BLACK and see_gain(b, s, chess.WHITE) > 0 for s in chess.SQUARES):
            continue
        ev = evaluate(fen, depth=20)
        if abs(ev) <= 40:
            # the naive defence must lose: at least one white move loses the game
            infos = cb.ENGINE.analyse(b, chess.engine.Limit(depth=16), multipv=min(10, b.legal_moves.count()))
            if any(i["score"].pov(chess.WHITE).is_mate() or (i["score"].pov(chess.WHITE).score() or 0) < -400 for i in infos):
                out.append(fen)
    if len(out) < count:
        raise ContentError("no knight vs pawn draws")
    return out

def kpk_defense(count):
    """K+P vs K with Black (the defender) to move: a draw, but some king moves lose."""
    out = []
    tries = 0
    while len(out) < count and tries < 600:
        tries += 1
        fen = random_position([("K", None), ("P", [f"{f}{r}" for f in "bcdefg" for r in "2345"]), ("k", None)])
        b = chess.Board(fen); b.turn = chess.BLACK
        if not b.is_valid() or b.is_check() or chess.square_distance(b.king(chess.WHITE), b.king(chess.BLACK)) < 2:
            continue
        infos = cb.ENGINE.analyse(b, chess.engine.Limit(depth=20), multipv=min(8, b.legal_moves.count()))
        def lose(i):
            s = i["score"].pov(chess.BLACK)
            return s.is_mate() and s.mate() < 0 or (s.score() or 0) <= -400
        if not lose(infos[0]) and any(lose(i) for i in infos):
            out.append(b.fen())
    if len(out) < count:
        raise ContentError("no KPK defense positions")
    return out

def pawn_chapter():
    SQ_Q = T("Black to move: can the black king catch the pawn?", "نوبت سیاه: آیا شاه سیاه به سرباز می‌رسد؟", "نۆرەی ڕەش: ئایا شای ڕەش دەگاتە سەربازەکە؟")
    YES, NO = T("Yes", "بله", "بەڵێ"), T("No", "نه", "نەخێر")
    squares = ["8/8/8/1k6/8/8/6P1/7K b - - 0 1", "8/8/8/2k5/8/8/6P1/7K b - - 0 1", "k7/8/8/8/8/5P2/8/7K b - - 0 1",
               "8/8/8/8/k7/8/5P2/7K b - - 0 1", "8/1k6/8/8/8/5P2/8/K7 b - - 0 1", "8/8/3k4/8/8/7P/8/K7 b - - 0 1"]
    sq_quiz = [winning_quiz(f, SQ_Q, YES, NO, winning_means_yes=False) for f in squares]
    return chapter("pawn-endgames", T("Pawn endgames", "آخر بازی سرباز", "کۆتایی سەرباز"), [
        lesson("endgames.pawn.square", T("The rule of the square", "قانون مربع", "یاسای چوارگۆشە"),
            T("Can a king catch a passed pawn? Draw a square from the pawn to its promotion rank (as wide as it is tall). If the defending king can step into the square, it catches the pawn; if not, the pawn queens. Remember: from its starting rank a pawn may move two squares, so count from the square in front.",
              "آیا شاه به سرباز رونده می‌رسد؟ از سرباز تا ردیف ارتقا یک مربع بکش (عرضش به اندازه‌ی ارتفاعش). اگر شاه مدافع بتواند وارد مربع شود، سرباز را می‌گیرد؛ وگرنه سرباز وزیر می‌شود. یادت باشد سرباز از ردیف شروعش می‌تواند دو خانه برود، پس از خانه‌ی جلویی حساب کن.",
              "ئایا شا دەگاتە سەربازێکی ڕاکەر؟ لە سەربازەکەوە تا ڕیزی بەرزبوونەوە چوارگۆشەیەک بکێشە (پانییەکەی بەقەد بەرزییەکەی). ئەگەر شای بەرگریکار بتوانێت بچێتە ناو چوارگۆشەکە، سەربازەکە دەگرێت؛ ئەگەرنا سەربازەکە دەبێتە وەزیر. لەبیرت بێت سەرباز لە ڕیزی دەستپێکەوە دەتوانێت دوو خانە بڕوات، بۆیە لە خانەی پێشەوەی بژمێرە."),
            1, [theory(T("The square of the f3-pawn: f3–f8–a8–a3. The black king is inside it, so it can catch the pawn.", "مربع سرباز f3: از f3 تا f8 و a8 و a3. شاه سیاه داخل مربع است، پس به سرباز می‌رسد.", "چوارگۆشەی سەربازی f3: لە f3 تا f8 و a8 و a3. شای ڕەش لە ناویدایە، بۆیە دەگاتە سەربازەکە."),
                       fen="8/1k6/8/8/8/5P2/8/K7 b - - 0 1", marks=["f3", "f8", "a8", "a3", "c6", "d5", "e4", "c3", "a6", "d8"])] + sq_quiz[:4]),
        lesson("endgames.pawn.square2", T("Square rule practice", "تمرین قانون مربع", "ڕاهێنانی یاسای چوارگۆشە"),
            T("Race the pawn yourself. With White: promote before the king arrives.", "خودت با سرباز مسابقه بده. با سفید: قبل از رسیدن شاه ارتقا بده.", "خۆت پێشبڕکێ لەگەڵ سەربازەکە بکە. بە سپی: پێش گەیشتنی شا بەرزی بکەرەوە."),
            1, sq_quiz[4:] + [play("8/8/8/8/k7/8/5P2/7K w - - 0 1", "promote", 10, PROMO)]),
        lesson("endgames.pawn.opposition", T("The opposition", "اپوزیسیون", "ئۆپۆزیسیۆن"),
            T("When the two kings face each other with one square between them, the side that does NOT have to move 'has the opposition'. The other king must give way. In king and pawn endgames the opposition decides whether the pawn promotes.",
              "وقتی دو شاه با یک خانه فاصله روبه‌روی هم هستند، طرفی که نوبت حرکتش نیست «اپوزیسیون» را دارد. شاه دیگر مجبور است کنار برود. در آخربازی شاه و سرباز، اپوزیسیون تعیین می‌کند سرباز ارتقا پیدا می‌کند یا نه.",
              "کاتێک دوو شاکە بە یەک خانە دووری ڕووبەڕووی یەکن، ئەو لایەی نۆرەی جوڵەی نییە «ئۆپۆزیسیۆن»ی هەیە. شاکەی تر ناچارە ڕێ بدات. لە کۆتایی شا و سەربازدا ئۆپۆزیسیۆن یەکلای دەکاتەوە کە سەربازەکە بەرز دەبێتەوە یان نا."),
            2, [theory(T("White to move would have to give way; here Black is to move, so White has the opposition.", "اگر نوبت سفید بود باید کنار می‌رفت؛ این‌جا نوبت سیاه است، پس اپوزیسیون با سفید است.", "ئەگەر نۆرەی سپی بوایە دەبوو ڕێ بدات؛ لێرەدا نۆرەی ڕەشە، بۆیە ئۆپۆزیسیۆن لای سپییە."),
                       fen="4k3/8/4K3/4P3/8/8/8/8 b - - 0 1", marks=["e6", "e8"]),
                *[only_moves(f, BEST) for f in kpk_only_moves(3)],
                play(kpk_only_moves(1)[0], "promote", 25, PROMO)]),
        lesson("endgames.pawn.key-squares", T("Key squares", "خانه‌های کلیدی", "خانە سەرەکییەکان"),
            T("For a pawn that has not crossed the middle, the key squares are the three squares two ranks in front of it. If your king reaches one of them, the pawn promotes no matter what. Get your king in front of the pawn, not behind it.",
              "برای سربازی که از وسط صفحه رد نشده، خانه‌های کلیدی سه خانه‌ای هستند که دو ردیف جلوتر از آن قرار دارند. اگر شاهت به یکی از آن‌ها برسد، سرباز در هر حال ارتقا پیدا می‌کند. شاهت را جلوی سرباز ببر، نه پشتش.",
              "بۆ سەربازێک کە لە ناوەڕاست تێنەپەڕیوە، خانە سەرەکییەکان ئەو سێ خانەیەن کە دوو ڕیز لە پێشییەوەن. ئەگەر شاکەت بگاتە یەکێکیان، سەربازەکە بە هەر حاڵ بەرز دەبێتەوە. شاکەت ببە بەردەم سەربازەکە، نەک پشتی."),
            2, [theory(T("Key squares of the e3-pawn: d5, e5, f5.", "خانه‌های کلیدی سرباز e3: d5، e5 و f5.", "خانە سەرەکییەکانی سەربازی e3: d5، e5 و f5."), fen="8/8/8/8/8/4P3/8/4K3 w - - 0 1", marks=["d5", "e5", "f5"]),
                *[only_moves(f, BEST) for f in kpk_only_moves(2)],
                play(kpk_only_moves(1)[0], "promote", 25, PROMO)]),
        lesson("endgames.pawn.defend", T("Defending king and pawn", "دفاع در برابر شاه و سرباز", "بەرگری دژی شا و سەرباز"),
            T("As the defender, stand in front of the pawn and take the opposition whenever the attacking king comes close. If you are pushed back to the last rank, go straight back, not sideways.",
              "به‌عنوان مدافع جلوی سرباز بایست و هر وقت شاه مهاجم نزدیک شد اپوزیسیون را بگیر. اگر به ردیف آخر عقب رانده شدی، مستقیم عقب برو، نه به پهلو.",
              "وەک بەرگریکار لە بەردەم سەربازەکە بوەستە و هەر کاتێک شای هێرشبەر نزیک بووەوە ئۆپۆزیسیۆن بگرە. ئەگەر پاڵنرایتە دوایین ڕیز، ڕاستەوخۆ بگەڕێوە دواوە، نەک بۆ لاوە."),
            2, [play(f, "draw", 20, HOLD) for f in kpk_defense(3)]),
        lesson("endgames.pawn.rook-pawn", T("The rook pawn", "سرباز رخ", "سەربازی قەڵا"),
            T("A pawn on the a- or h-file is special: if the defending king reaches the corner in front of it, the game is a draw — even with the attacking king nearby — because the attacker has no room to go around.",
              "سرباز ستون a یا h خاص است: اگر شاه مدافع به گوشه‌ی جلوی آن برسد، بازی مساوی است — حتی اگر شاه مهاجم نزدیک باشد — چون مهاجم جایی برای دور زدن ندارد.",
              "سەربازێک لەسەر ستوونی a یان h تایبەتە: ئەگەر شای بەرگریکار بگاتە گۆشەی بەردەمی، یاری یەکسانە — تەنانەت ئەگەر شای هێرشبەر نزیک بێت — چونکە هێرشبەر شوێنی نییە بۆ خولانەوە."),
            2, [play("7k/8/8/6KP/8/8/8/8 b - - 0 1", "draw", 20, HOLD),
                play("8/8/8/8/8/k7/p7/1K6 w - - 0 1", "draw", 20, HOLD)]),
        lesson("endgames.pawn.outside", T("The outside passed pawn", "سرباز رونده‌ی دور", "سەربازی ڕاکەری دوور"),
            T("A passed pawn far from the other pawns is a decoy: the enemy king must run to stop it, and meanwhile your king eats the pawns on the other side.",
              "سرباز رونده‌ای که از بقیه‌ی سربازها دور است یک طعمه است: شاه حریف باید برای متوقف کردنش بدود و در این فاصله شاه تو سربازهای طرف دیگر را می‌خورد.",
              "سەربازێکی ڕاکەر کە لە سەربازەکانی تر دوورە داوە: شای ڕکابەر دەبێت ڕابکات بۆ وەستاندنی و لەو کاتەدا شاکەت سەربازەکانی ئەولای دەخوات."),
            3, [play("8/5pk1/6p1/P7/8/6P1/5PK1/8 w - - 0 1", "win", 40, WIN)]),
        lesson("endgames.pawn.breakthrough", T("The pawn breakthrough", "رخنه‌ی سربازی", "شکاندنی سەربازی"),
            T("Three pawns against three facing each other: one sacrifice opens the way for a new queen. Sacrifice in the middle, then recapture towards the free pawn.",
              "سه سرباز در برابر سه سرباز روبه‌روی هم: یک قربانی راه را برای وزیر جدید باز می‌کند. در وسط قربانی کن، بعد به سمت سرباز آزاد پس بگیر.",
              "سێ سەرباز بەرامبەر سێ سەرباز ڕووبەڕووی یەک: یەک قوربانی ڕێگا بۆ وەزیرێکی نوێ دەکاتەوە. لە ناوەڕاستدا قوربانی بکە، پاشان بەرەو سەربازە ئازادەکە بیگرەوە."),
            3, [only_moves("7k/ppp5/8/PPP5/8/8/8/7K w - - 0 1", T("Find the breakthrough!", "رخنه را پیدا کن!", "شکاندنەکە بدۆزەرەوە!")),
                play("7k/ppp5/8/PPP5/8/8/8/7K w - - 0 1", "promote", 10, PROMO)]),
    ])

def rook_chapter():
    return chapter("rook-endgames", T("Rook endgames", "آخر بازی رخ", "کۆتایی قەڵا"), [
        lesson("endgames.rook.cutoff", T("Cut off the king", "شاه را جدا کن", "شاکە ببڕە"),
            T("In rook endgames the rook is strongest when it cuts the enemy king off from the pawn along a file or rank. A king cut off by two files usually cannot come back in time.",
              "در آخربازی رخ، رخ وقتی قوی‌ترین است که شاه حریف را روی یک ستون یا ردیف از سرباز جدا کند. شاهی که دو ستون دور نگه داشته شود معمولاً به‌موقع برنمی‌گردد.",
              "لە کۆتایی قەڵادا قەڵا بەهێزترینە کاتێک شای ڕکابەر لە سەربازەکە دەبڕێت بە درێژایی ستوون یان ڕیزێک. شایەک کە دوو ستوون دوور خرابێت زۆرجار بە کات ناگەڕێتەوە."),
            3, [only_moves("8/8/8/8/2k5/8/4P3/R3K3 w - - 0 1", BEST),
                play("8/8/8/8/2k5/8/4P3/R3K3 w - - 0 1", "promote", 30, PROMO)]),
        lesson("endgames.rook.lucena", T("The Lucena position", "وضعیت لوسنا", "دۆخی لوسێنا"),
            T("The most important winning position: your king stands in front of its pawn on the 7th rank, shut in by the enemy rook. The winning method is 'building a bridge': bring your rook to the 4th rank, walk the king out, and block the checks with the rook.",
              "مهم‌ترین وضعیت برنده: شاهت جلوی سربازِ ردیف هفتم ایستاده و رخ حریف او را محبوس کرده است. روش برد «ساختن پل» است: رخت را به ردیف چهارم بیاور، شاه را بیرون ببر و کیش‌ها را با رخ سد کن.",
              "گرنگترین دۆخی براوە: شاکەت لە بەردەم سەربازی ڕیزی حەوتەمدا وەستاوە و قەڵای ڕکابەر گرتوویەتی. ڕێگای بردنەوە «دروستکردنی پرد»ە: قەڵاکەت بهێنە ڕیزی چوارەم، شاکە بەرە دەرەوە و کشەکان بە قەڵا بگرەوە."),
            4, [theory(T("Lucena: build a bridge with Rc4 (or Rd4) and step out with the king.", "لوسنا: با Rc4 (یا Rd4) پل بساز و شاه را بیرون ببر.", "لوسێنا: بە Rc4 (یان Rd4) پرد دروست بکە و شاکە بەرە دەرەوە."),
                       fen="1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1"),
                play("1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1", "promote", 20, PROMO)]),
        lesson("endgames.rook.philidor", T("The Philidor position", "وضعیت فیلیدور", "دۆخی فیلیدۆر"),
            T("The key drawing method: keep your rook on your third rank (the 6th for Black) so the enemy king cannot advance. When the pawn moves forward, go behind it and give checks from far away.",
              "روش کلیدی تساوی: رخت را روی ردیف سوم خودت (ردیف ششم برای سیاه) نگه دار تا شاه حریف نتواند جلو بیاید. وقتی سرباز جلو رفت، پشتش برو و از دور کیش بده.",
              "ڕێگای سەرەکیی یەکسانی: قەڵاکەت لەسەر ڕیزی سێیەمی خۆت (ڕیزی شەشەم بۆ ڕەش) بهێڵەوە تا شای ڕکابەر نەتوانێت پێش بکەوێت. کاتێک سەربازەکە پێشکەوت، بچۆ پشتی و لە دوورەوە کش بدە."),
            4, [play("4k3/7R/8/4PK2/8/8/8/r7 b - - 0 1", "draw", 20, HOLD)]),
        lesson("endgames.rook.behind", T("Rooks belong behind passed pawns", "رخ پشت سرباز رونده", "قەڵا لە پشتی سەربازی ڕاکەر"),
            T("Tarrasch's rule: rooks belong behind passed pawns — your own (to push them) and the opponent's (to stop them). From behind, the rook gains activity as the pawn advances.",
              "قانون تاراش: جای رخ پشت سربازهای رونده است — سرباز خودت (برای جلو بردنش) و سرباز حریف (برای متوقف کردنش). از پشت، هر چه سرباز جلوتر برود رخ فعال‌تر می‌شود.",
              "یاسای تاراش: شوێنی قەڵا لە پشتی سەربازە ڕاکەرەکانە — هی خۆت (بۆ پاڵنانی) و هی ڕکابەر (بۆ وەستاندنی). لە پشتەوە، تا سەربازەکە پێشتر بکەوێت قەڵا چالاکتر دەبێت."),
            3, [only_moves("6k1/5ppp/8/P7/8/8/5PPP/R5K1 w - - 0 1", BEST)]),
    ])

def queen_minor_chapter():
    DQ = T("Is this a win for White?", "آیا سفید می‌برد؟", "ئایا سپی دەیباتەوە؟")
    YES, NO = T("Yes", "بله", "بەڵێ"), T("No, it's a draw", "نه، مساوی است", "نەخێر، یەکسانە")
    return chapter("queen-minor", T("Queen and minor pieces", "وزیر و مهره‌های سبک", "وەزیر و مۆرە سووکەکان"), [
        lesson("endgames.queen.vs-pawn", T("Queen against pawn", "وزیر در برابر سرباز", "وەزیر بەرامبەر سەرباز"),
            T("A queen beats a pawn on the 7th rank: check and pin to force the king in front of the pawn, then bring your king one step closer. Repeat until the pawn falls.",
              "وزیر سرباز ردیف هفتم را شکست می‌دهد: با کیش و آچمز شاه را مجبور کن جلوی سرباز برود، بعد شاهت را یک قدم نزدیک‌تر بیاور. تکرار کن تا سرباز از دست برود.",
              "وەزیر سەربازی ڕیزی حەوتەم دەبەزێنێت: بە کش و بەستنەوە شاکە ناچار بکە بچێتە بەردەم سەربازەکە، پاشان شاکەت هەنگاوێک نزیک بخەرەوە. دووبارە بکەرەوە تا سەربازەکە دەکەوێت."),
            3, [play("8/8/8/8/8/8/2kp4/K6Q w - - 0 1", "win", 30, WIN)]),
        lesson("endgames.queen.exceptions", T("When the queen cannot win", "وقتی وزیر نمی‌برد", "کاتێک وەزیر نایباتەوە"),
            T("Against a rook pawn or a bishop pawn on the 7th rank the defender has a stalemate trick: when the king hides in the corner, taking the pawn is impossible without stalemate. These are draws if the attacking king is far away.",
              "در برابر سرباز رخ یا سرباز فیل در ردیف هفتم، مدافع ترفند پات دارد: وقتی شاه در گوشه پنهان می‌شود، گرفتن سرباز بدون پات ممکن نیست. اگر شاه مهاجم دور باشد این‌ها مساوی‌اند.",
              "دژی سەربازی قەڵا یان سەربازی فیل لە ڕیزی حەوتەمدا، بەرگریکار فێڵی پاتی هەیە: کاتێک شا لە گۆشەدا خۆی دەشارێتەوە، گرتنی سەربازەکە بەبێ پات ناکرێت. ئەگەر شای هێرشبەر دوور بێت ئەمانە یەکسانن."),
            3, [winning_quiz("8/8/8/8/8/8/1kp5/K6Q w - - 0 1", DQ, YES, NO) if False else
                winning_quiz("7K/8/8/8/8/8/p1k5/7Q w - - 0 1", DQ, YES, NO),
                winning_quiz("7K/8/8/8/8/8/2kp4/7Q w - - 0 1", DQ, YES, NO)]),
        lesson("endgames.minor.wrong-bishop", T("The wrong bishop", "فیل اشتباه", "فیلی هەڵە"),
            T("King, bishop and rook pawn cannot win if the bishop does not control the promotion square and the defending king reaches the corner. Remember this when you trade into an endgame!",
              "شاه و فیل و سرباز رخ نمی‌برند اگر فیل خانه‌ی ارتقا را کنترل نکند و شاه مدافع به گوشه برسد. وقتی به آخربازی می‌روی این را به یاد داشته باش!",
              "شا و فیل و سەربازی قەڵا ناتوانن ببەنەوە ئەگەر فیلەکە خانەی بەرزبوونەوە کۆنترۆڵ نەکات و شای بەرگریکار بگاتە گۆشەکە. کاتێک ئاڵوگۆڕ دەکەیت بەرەو کۆتایی ئەمە لەبیر بێت!"),
            3, [winning_quiz("7k/8/5K1P/8/8/8/8/3B4 w - - 0 1", DQ, YES, NO),
                # A known fortress: engines without tablebases show a small plus, but it cannot be won.
                play("7k/8/5K1P/8/8/8/8/3B4 b - - 0 1", "draw", 20, HOLD, check=False)]),
        lesson("endgames.minor.knight-pawn", T("Knight against pawn", "اسب در برابر سرباز", "ئەسپ بەرامبەر سەرباز"),
            T("A knight stops a pawn best from in front of it. Knights are clumsy against rook pawns: count the jumps carefully.",
              "اسب بهتر از همه از جلوی سرباز جلویش را می‌گیرد. اسب در برابر سرباز رخ دست‌وپا چلفتی است: پرش‌ها را با دقت بشمار.",
              "ئەسپ باشترین شێوە لە بەردەم سەربازەکەوە ڕێگری لێ دەکات. ئەسپ دژی سەربازی قەڵا ناڕێکە: بازەکان بە وردی بژمێرە."),
            3, [play(f, "draw", 20, HOLD) for f in knight_vs_pawn(2)]),
    ])

def puzzle_chapter(n_lessons=24):
    pool = [p for p in cb.DATA["puzzles"] if "endgame" in p["themes"] and p["id"] not in cb.USED]
    pool.sort(key=lambda p: p["rating"])
    per = 6
    n = min(n_lessons, len(pool) // per)
    lessons = []
    if n == 0:
        return None
    step = len(pool) / n
    for i in range(n):
        band = pool[int(i * step): int((i + 1) * step)]
        chosen = [band[int(j * len(band) / per)] for j in range(per)]
        for p in chosen:
            cb.USED.add(p["id"])
        avg = sum(p["rating"] for p in chosen) // per
        lessons.append(lesson(f"endgames.puzzles.{i + 1}",
            T(f"Endgame practice {i + 1}", f"تمرین آخر بازی {fa_num(i + 1)}", f"ڕاهێنانی کۆتایی یاری {ck_num(i + 1)}"),
            T(f"Real endgame positions around rating {avg}. Kings become active, pawns race — calculate precisely.",
              f"وضعیت‌های واقعی آخر بازی با سختی حدود {fa_num(avg)}. شاه‌ها فعال می‌شوند و سربازها مسابقه می‌دهند — دقیق حساب کن.",
              f"دۆخی ڕاستەقینەی کۆتایی یاری بە قورسی نزیکەی {ck_num(avg)}. شاکان چالاک دەبن و سەربازەکان پێشبڕکێ دەکەن — بە وردی حساب بکە."),
            min(5, 1 + (avg - 400) // 400), [puzzle(p, T("Find the best move.", "بهترین حرکت را پیدا کن.", "باشترین جوڵە بدۆزەرەوە.")) for p in chosen]))
    return chapter("endgame-practice", T("Endgame practice", "تمرین آخر بازی", "ڕاهێنانی کۆتایی یاری"), lessons)

def build(target_total=55):
    chs = [pawn_chapter(), rook_chapter(), queen_minor_chapter()]
    count = sum(len(c["lessons"]) for c in chs)
    pc = puzzle_chapter(max(8, target_total - count))
    if pc:
        chs.append(pc)
    return course("endgames", T("Endgames", "آخر بازی", "کۆتایی یاری"),
        T("King and pawn, rook, queen and minor-piece endgames — learn the key positions and play them out against Zorix.",
          "آخربازی‌های شاه و سرباز، رخ، وزیر و مهره‌های سبک — وضعیت‌های کلیدی را یاد بگیر و در برابر Zorix بازی کن.",
          "کۆتاییەکانی شا و سەرباز، قەڵا، وەزیر و مۆرە سووکەکان — دۆخە سەرەکییەکان فێربە و دژی Zorix یارییان بکە."),
        chs)
