"""Course 1: Chess basics - the board, the pieces, captures, check, mate, special moves, draws, notation."""
import chess
from cb import *

START = chess.STARTING_FEN

def P(en, fa, ckb):  # prompt helper
    return T(en, fa, ckb)

STAR_PROMPT = P("Collect all the stars.", "همه‌ی ستاره‌ها را جمع کن.", "هەموو ئەستێرەکان کۆبکەرەوە.")
CAPTURE_PROMPT = P("Capture all the black pieces.", "همه‌ی مهره‌های سیاه را بزن.", "هەموو مۆرە ڕەشەکان بگرە.")

def star_steps(ptype, specs):
    return [stars(*random_stars(ptype, n, obstacles=o), STAR_PROMPT) for n, o in specs]

def capture_steps(ptype, specs):
    return [capture(random_capture(ptype, n), CAPTURE_PROMPT) for n in specs]

# ------------------------------------------------------------------------------------------------ helpers for goal positions

def gen_goal(spec, g, count, extra=None):
    out, seen = [], set()
    tries = 0
    while len(out) < count and tries < 200:
        tries += 1
        def cond(b):
            ok = [m for m in b.legal_moves if satisfies(b, m, g)]
            if not ok or len(ok) == b.legal_moves.count():
                return False
            return extra(b) if extra else True
        fen = random_position(spec, cond=cond)
        if fen.split()[0] in seen:
            continue
        seen.add(fen.split()[0])
        out.append(fen)
    return out

def in_check(b):
    return b.is_check()

def only_capture_escape(b):
    # capturing the checker must be possible; for teaching we allow other escapes too
    return b.is_check() and not b.is_checkmate()

def board_chapter():
    return chapter("board", T("The board", "صفحه‌ی شطرنج", "تەختەی شەترەنج"), [
        lesson("basics.board.1", T("The chessboard", "صفحه‌ی شطرنج", "تەختەی شەترەنج"),
            T("Chess is played on a board of 64 squares, 8 by 8, in alternating light and dark colours. Place the board so that each player has a light square in the right-hand corner — 'light on the right'.",
              "شطرنج روی صفحه‌ای با ۶۴ خانه (۸ در ۸) بازی می‌شود که خانه‌هایش یکی‌درمیان روشن و تیره‌اند. صفحه را طوری بگذار که گوشه‌ی سمت راستِ هر بازیکن یک خانه‌ی روشن باشد: «روشن در راست».",
              "شەترەنج لەسەر تەختەیەکی ٦٤ خانەیی (٨ لە ٨) یاری دەکرێت کە خانەکانی یەک لە دوای یەک ڕووناک و تاریکن. تەختەکە وا دابنێ کە گۆشەی لای ڕاستی هەر یاریزانێک خانەیەکی ڕووناک بێت: «ڕووناک لە ڕاست»."),
            1, [
            theory(T("Every square has a name made of a letter and a number. The letters a–h name the columns (files) from left to right, the numbers 1–8 name the rows (ranks) from White's side. The square in the bottom-left corner is a1, the one in the top-right corner is h8.",
                     "هر خانه یک نام دارد که از یک حرف و یک عدد ساخته شده است. حروف a تا h ستون‌ها را از چپ به راست نام‌گذاری می‌کنند و اعداد ۱ تا ۸ ردیف‌ها را از طرف سفید. خانه‌ی گوشه‌ی پایین چپ a1 و خانه‌ی گوشه‌ی بالا راست h8 است.",
                     "هەر خانەیەک ناوێکی هەیە کە لە پیتێک و ژمارەیەک پێکهاتووە. پیتەکانی a تا h ستوونەکان لە چەپەوە بۆ ڕاست ناو دەنێن و ژمارەکانی ١ تا ٨ ڕیزەکان لە لای سپییەوە. خانەی گۆشەی خوارەوەی چەپ a1ە و گۆشەی سەرەوەی ڕاست h8ە."),
                   fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=["a1", "h8", "e4"]),
            quiz(T("What colour is the square h1?", "خانه‌ی h1 چه رنگی است؟", "خانەی h1 چ ڕەنگێکە؟"),
                 [T("Light", "روشن", "ڕووناک"), T("Dark", "تیره", "تاریک")], 0,
                 T("Correct: 'light on the right'. h1 is in White's right-hand corner.", "درست است: «روشن در راست». h1 گوشه‌ی سمت راست سفید است.", "ڕاستە: «ڕووناک لە ڕاست». h1 گۆشەی لای ڕاستی سپییە."),
                 fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=["h1"]),
            square(10, T("Tap the square that is named.", "روی خانه‌ای که نامش آمده بزن.", "دەست لەو خانەیە بدە کە ناوی هاتووە.")),
        ]),
        lesson("basics.board.2", T("Files, ranks and diagonals", "ستون‌ها، ردیف‌ها و قطرها", "ستوون، ڕیز و لاکێشەکان"),
            T("Pieces move along three kinds of lines: files (up and down), ranks (left and right) and diagonals (squares of one colour touching at the corners). Knowing these lines makes it easy to see how pieces move.",
              "مهره‌ها روی سه نوع خط حرکت می‌کنند: ستون‌ها (بالا و پایین)، ردیف‌ها (چپ و راست) و قطرها (خانه‌های هم‌رنگ که از گوشه به هم وصل‌اند). شناختن این خط‌ها دیدن حرکت مهره‌ها را آسان می‌کند.",
              "مۆرەکان لەسەر سێ جۆر هێڵ دەجوڵێن: ستوون (سەرەوە و خوارەوە)، ڕیز (چەپ و ڕاست) و لاکێش (خانە هاوڕەنگەکان کە لە گۆشەوە بەیەکەوە لکاون). ناسینی ئەم هێڵانە بینینی جوڵەی مۆرەکان ئاسان دەکات."),
            1, [
            theory(T("The e-file is marked: all squares from e1 to e8.", "ستون e علامت خورده است: همه‌ی خانه‌ها از e1 تا e8.", "ستوونی e نیشانە کراوە: هەموو خانەکان لە e1 تا e8."),
                   fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=[f"e{i}" for i in range(1, 9)]),
            theory(T("The 4th rank is marked: from a4 to h4.", "ردیف چهارم علامت خورده است: از a4 تا h4.", "ڕیزی چوارەم نیشانە کراوە: لە a4 تا h4."),
                   fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=[f"{c}4" for c in "abcdefgh"]),
            theory(T("The long diagonal a1–h8 is marked. A diagonal always stays on one colour.", "قطر بلند a1 تا h8 علامت خورده است. یک قطر همیشه روی یک رنگ می‌ماند.", "لاکێشی درێژی a1 تا h8 نیشانە کراوە. لاکێش هەمیشە لەسەر یەک ڕەنگ دەمێنێتەوە."),
                   fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=[chess.square_name(chess.square(i, i)) for i in range(8)]),
            quiz(T("Which square is on the same diagonal as c1?", "کدام خانه روی همان قطر c1 است؟", "کام خانە لەسەر هەمان لاکێشی c1ە؟"),
                 [T("f4", "f4", "f4"), T("c4", "c4", "c4"), T("e1", "e1", "e1")], 0,
                 T("c1, d2, e3, f4 form a diagonal.", "c1، d2، e3 و f4 یک قطر می‌سازند.", "c1، d2، e3 و f4 لاکێشێک پێکدەهێنن."),
                 fen="8/8/8/8/8/8/8/8 w - - 0 1", marks=["c1"]),
            quiz(T("Which squares are on the d-file?", "کدام خانه‌ها روی ستون d هستند؟", "کام خانانە لەسەر ستوونی dن؟"),
                 [T("d1 and d8", "d1 و d8", "d1 و d8"), T("a4 and h4", "a4 و h4", "a4 و h4"), T("a1 and h8", "a1 و h8", "a1 و h8")], 0),
            square(10, T("Find the squares.", "خانه‌ها را پیدا کن.", "خانەکان بدۆزەرەوە.")),
        ]),
        lesson("basics.board.3", T("Setting up the pieces", "چیدن مهره‌ها", "ڕێکخستنی مۆرەکان"),
            T("Each side starts with 16 pieces: a king, a queen, two rooks, two bishops, two knights and eight pawns. The rooks go in the corners, then the knights, then the bishops. The queen stands on her own colour (the white queen on the light square d1), and the king next to her.",
              "هر طرف با ۱۶ مهره شروع می‌کند: یک شاه، یک وزیر، دو رخ، دو فیل، دو اسب و هشت سرباز. رخ‌ها در گوشه‌ها، کنارشان اسب‌ها و بعد فیل‌ها قرار می‌گیرند. وزیر روی رنگ خودش می‌ایستد (وزیر سفید روی خانه‌ی روشن d1) و شاه کنار او.",
              "هەر لایەک بە ١٦ مۆرە دەست پێدەکات: شا، وەزیر، دوو قەڵا، دوو فیل، دوو ئەسپ و هەشت سەرباز. قەڵاکان لە گۆشەکان، پاشان ئەسپەکان و دواتر فیلەکان. وەزیر لەسەر ڕەنگی خۆی دەوەستێت (وەزیری سپی لەسەر خانەی ڕووناکی d1) و شا لە تەنیشتییەوە."),
            1, [
            theory(T("This is the starting position. White always moves first.", "این وضعیت شروع بازی است. همیشه سفید اول حرکت می‌کند.", "ئەمە دۆخی دەستپێکە. هەمیشە سپی یەکەم جوڵە دەکات."), fen=START),
            quiz(T("On which square does the white queen start?", "وزیر سفید روی کدام خانه شروع می‌کند؟", "وەزیری سپی لەسەر کام خانە دەست پێدەکات؟"),
                 [T("d1", "d1", "d1"), T("e1", "e1", "e1"), T("c1", "c1", "c1")], 0, T("The queen stands on her own colour: d1 is light.", "وزیر روی رنگ خودش است: d1 روشن است.", "وەزیر لەسەر ڕەنگی خۆیەتی: d1 ڕووناکە."), fen=START, marks=["d1"]),
            quiz(T("On which square does the black king start?", "شاه سیاه روی کدام خانه شروع می‌کند؟", "شای ڕەش لەسەر کام خانە دەست پێدەکات؟"),
                 [T("d8", "d8", "d8"), T("e8", "e8", "e8"), T("e1", "e1", "e1")], 1, None, fen=START),
            quiz(T("Which piece stands in the corners?", "کدام مهره در گوشه‌ها قرار دارد؟", "کام مۆرە لە گۆشەکاندایە؟"),
                 [T("The knight", "اسب", "ئەسپ"), T("The bishop", "فیل", "فیل"), T("The rook", "رخ", "قەڵا")], 2, None, fen=START, marks=["a1", "h1", "a8", "h8"]),
            quiz(T("How many pieces does each player have at the start?", "هر بازیکن در شروع چند مهره دارد؟", "هەر یاریزانێک لە سەرەتادا چەند مۆرەی هەیە؟"),
                 [T("16", "۱۶", "١٦"), T("12", "۱۲", "١٢"), T("8", "۸", "٨")], 0),
        ]),
        lesson("basics.board.4", T("Coordinates training", "تمرین مختصات", "ڕاهێنانی ناونیشانی خانەکان"),
            T("Strong players know every square by name without thinking. Train your eyes: find the squares as fast as you can — first from White's side, then from Black's side.",
              "بازیکنان قوی نام هر خانه را بدون فکر کردن می‌دانند. چشمت را تمرین بده: خانه‌ها را هر چه سریع‌تر پیدا کن — اول از طرف سفید و بعد از طرف سیاه.",
              "یاریزانە بەهێزەکان ناوی هەموو خانەیەک بەبێ بیرکردنەوە دەزانن. چاوت ڕابهێنە: خانەکان بە خێراترین شێوە بدۆزەرەوە — سەرەتا لە لای سپی، پاشان لە لای ڕەش."),
            1, [
            square(15, T("White's view: find the squares.", "از دید سفید: خانه‌ها را پیدا کن.", "لە لای سپییەوە: خانەکان بدۆزەرەوە.")),
            square(15, T("Black's view: the board is turned around.", "از دید سیاه: صفحه برعکس شده است.", "لە لای ڕەشەوە: تەختەکە هەڵگەڕاوەتەوە."), black=True),
        ]),
    ])

# ------------------------------------------------------------------------------------------------ the pieces

PIECE_TEXT = {
    chess.ROOK: (
        T("The rook", "رخ", "قەڵا"),
        T("The rook moves any number of squares along a file or a rank — straight up, down, left or right — but it cannot jump over pieces. It is worth about 5 pawns.",
          "رخ در امتداد ستون یا ردیف به هر تعداد خانه حرکت می‌کند — مستقیم به بالا، پایین، چپ یا راست — اما نمی‌تواند از روی مهره‌ها بپرد. ارزش آن حدود ۵ سرباز است.",
          "قەڵا بە درێژایی ستوون یان ڕیز بە هەر ژمارەیەک خانە دەجوڵێت — ڕاستەوخۆ بۆ سەرەوە، خوارەوە، چەپ یان ڕاست — بەڵام ناتوانێت بەسەر مۆرەکاندا باز بدات. بەهاکەی نزیکەی ٥ سەربازە."),
    ),
    chess.BISHOP: (
        T("The bishop", "فیل", "فیل"),
        T("The bishop moves any number of squares diagonally. A bishop stays on the colour it starts on for the whole game, so each side has a light-squared and a dark-squared bishop. It is worth about 3 pawns.",
          "فیل به‌صورت قطری به هر تعداد خانه حرکت می‌کند. فیل در تمام بازی روی رنگی که از آن شروع کرده می‌ماند؛ پس هر طرف یک فیل خانه‌روشن و یک فیل خانه‌تیره دارد. ارزش آن حدود ۳ سرباز است.",
          "فیل بە شێوەی لاکێش بە هەر ژمارەیەک خانە دەجوڵێت. فیل لە هەموو یارییەکەدا لەسەر ئەو ڕەنگە دەمێنێتەوە کە لێیەوە دەستی پێکردووە، بۆیە هەر لایەک فیلێکی خانە-ڕووناک و فیلێکی خانە-تاریکی هەیە. بەهاکەی نزیکەی ٣ سەربازە."),
    ),
    chess.QUEEN: (
        T("The queen", "وزیر", "وەزیر"),
        T("The queen is the most powerful piece: she moves like a rook and a bishop together — any number of squares in a straight line or diagonally. She is worth about 9 pawns, so look after her!",
          "وزیر قوی‌ترین مهره است: مثل رخ و فیل با هم حرکت می‌کند — به هر تعداد خانه در خط مستقیم یا قطری. ارزش آن حدود ۹ سرباز است، پس مراقبش باش!",
          "وەزیر بەهێزترین مۆرەیە: وەک قەڵا و فیل پێکەوە دەجوڵێت — بە هەر ژمارەیەک خانە بە هێڵی ڕاست یان لاکێش. بەهاکەی نزیکەی ٩ سەربازە، بۆیە ئاگات لێی بێت!"),
    ),
    chess.KING: (
        T("The king", "شاه", "شا"),
        T("The king moves one square in any direction. He is the most important piece: if your king is trapped (checkmate), you lose the game. The king can never move to a square attacked by the opponent.",
          "شاه یک خانه به هر طرف حرکت می‌کند. او مهم‌ترین مهره است: اگر شاه تو گیر بیفتد (مات شود)، بازی را می‌بازی. شاه هرگز نمی‌تواند به خانه‌ای برود که حریف به آن حمله می‌کند.",
          "شا یەک خانە بۆ هەر لایەک دەجوڵێت. گرنگترین مۆرەیە: ئەگەر شاکەت گیر بخوات (کش‌مات)، یارییەکە دەدۆڕێنیت. شا هەرگیز ناتوانێت بچێتە خانەیەک کە ڕکابەر هێرشی دەکاتە سەر."),
    ),
    chess.KNIGHT: (
        T("The knight", "اسب", "ئەسپ"),
        T("The knight moves in an 'L': two squares in one direction and then one square to the side. It is the only piece that can jump over other pieces. A knight always lands on a square of the other colour. It is worth about 3 pawns.",
          "اسب به شکل «L» حرکت می‌کند: دو خانه در یک جهت و سپس یک خانه به کنار. اسب تنها مهره‌ای است که می‌تواند از روی مهره‌های دیگر بپرد. اسب همیشه روی خانه‌ای با رنگ مخالف فرود می‌آید. ارزش آن حدود ۳ سرباز است.",
          "ئەسپ بە شێوەی «L» دەجوڵێت: دوو خانە بە ئاڕاستەیەکدا و پاشان یەک خانە بۆ لاوە. ئەسپ تاکە مۆرەیە کە دەتوانێت بەسەر مۆرەکانی تردا باز بدات. ئەسپ هەمیشە لەسەر خانەیەکی ڕەنگی پێچەوانە دەنیشێتەوە. بەهاکەی نزیکەی ٣ سەربازە."),
    ),
}

def piece_lessons():
    L = []
    order = [(chess.ROOK, "rook"), (chess.BISHOP, "bishop"), (chess.QUEEN, "queen"), (chess.KING, "king"), (chess.KNIGHT, "knight")]
    for ptype, key in order:
        name, text = PIECE_TEXT[ptype]
        sym = chess.piece_symbol(ptype).upper()
        demo = chess.Board(None); demo.set_piece_at(chess.D4, chess.Piece(ptype, chess.WHITE))
        marks = [chess.square_name(s) for s in demo.attacks(chess.D4)]
        L.append(lesson(f"basics.pieces.{key}1", T(name["en"] + " I", name["fa"] + " ۱", name["ckb"] + " ١"), text, 1, [
            theory(T("The marked squares show where it can go from d4.", "خانه‌های علامت‌دار نشان می‌دهند از d4 به کجا می‌تواند برود.", "خانە نیشانەکراوەکان پیشان دەدەن لە d4ەوە دەتوانێت بۆ کوێ بچێت."),
                   fen=demo.fen(), marks=marks),
            *star_steps(ptype, [(1, 0), (2, 0), (2, 0), (3, 0)]),
        ]))
        L.append(lesson(f"basics.pieces.{key}2", T(name["en"] + " II", name["fa"] + " ۲", name["ckb"] + " ٢"),
            T("Now there are pawns in the way. Your pieces cannot pass through their own pawns: find a route around them and collect the stars in as few moves as possible.",
              "حالا سربازها سر راه هستند. مهره‌ها نمی‌توانند از روی سربازهای خودی رد شوند: مسیری دور آن‌ها پیدا کن و ستاره‌ها را با کمترین حرکت جمع کن.",
              "ئێستا سەربازەکان لەسەر ڕێگان. مۆرەکان ناتوانن بە ناو سەربازە خۆییەکاندا تێپەڕن: ڕێگەیەک بە دەوریاندا بدۆزەرەوە و ئەستێرەکان بە کەمترین جوڵە کۆبکەرەوە."),
            2, star_steps(ptype, [(2, 3), (3, 4), (3, 5), (4, 5)])))
    # pawn lessons
    L.append(lesson("basics.pieces.pawn1", T("The pawn", "سرباز", "سەرباز"),
        T("Pawns move straight forward one square. On its very first move a pawn may move two squares. Pawns never move backwards. They are worth 1 point each, but together they shape the whole game.",
          "سرباز یک خانه مستقیم به جلو می‌رود. در اولین حرکتش می‌تواند دو خانه جلو برود. سرباز هرگز به عقب برنمی‌گردد. ارزش هر سرباز ۱ امتیاز است، اما سربازها با هم شکل کل بازی را می‌سازند.",
          "سەرباز یەک خانە ڕاستەوخۆ بۆ پێشەوە دەڕوات. لە یەکەم جوڵەیدا دەتوانێت دوو خانە بڕوات. سەرباز هەرگیز بۆ دواوە ناگەڕێتەوە. هەر سەربازێک ١ خاڵ بەهای هەیە، بەڵام سەربازەکان پێکەوە شێوەی هەموو یارییەکە دیاری دەکەن."),
        1, [
        theory(T("From its starting square the e-pawn may go to e3 or e4.", "سرباز e از خانه‌ی شروعش می‌تواند به e3 یا e4 برود.", "سەربازی e لە خانەی دەستپێکەوە دەتوانێت بچێتە e3 یان e4."),
               fen="8/8/8/8/8/8/4P3/8 w - - 0 1", marks=["e3", "e4"]),
        stars("8/8/8/8/8/8/4P3/8 w - - 0 1", ["e5"], STAR_PROMPT),
        stars("8/8/8/8/8/8/1P6/8 w - - 0 1", ["b4", "b6"], STAR_PROMPT),
        stars("8/8/8/8/8/2P5/8/8 w - - 0 1", ["c5", "c7"], STAR_PROMPT),
    ]))
    L.append(lesson("basics.pieces.pawn2", T("Pawns capture diagonally", "سرباز قطری می‌زند", "سەرباز بە لاکێش دەگرێت"),
        T("A pawn moves straight, but it captures one square diagonally forward. A pawn is blocked by any piece standing right in front of it.",
          "سرباز مستقیم حرکت می‌کند، اما یک خانه به‌صورت قطری رو به جلو می‌زند. هر مهره‌ای که درست جلوی سرباز باشد راهش را می‌بندد.",
          "سەرباز ڕاستەوخۆ دەجوڵێت، بەڵام یەک خانە بە لاکێش بۆ پێشەوە دەگرێت. هەر مۆرەیەک ڕێک لە بەردەم سەربازدا بێت ڕێگای دەگرێت."),
        1, [
        theory(T("The pawn on e4 can capture on d5 or f5, but it cannot move to e5.", "سرباز e4 می‌تواند در d5 یا f5 بزند، اما نمی‌تواند به e5 برود.", "سەربازی e4 دەتوانێت لە d5 یان f5 بگرێت، بەڵام ناتوانێت بچێتە e5."),
               fen="8/8/8/3pnp2/4P3/8/8/8 w - - 0 1", marks=["d5", "f5"], arrows=["e4d5", "e4f5"]),
        capture("8/8/8/8/8/3p4/4P3/8 w - - 0 1", CAPTURE_PROMPT),
        capture("8/8/8/3p4/2p5/1P6/8/8 w - - 0 1", CAPTURE_PROMPT),
        capture("8/8/2p5/3p4/2p5/3P4/8/8 w - - 0 1", CAPTURE_PROMPT),
    ]))
    promo = []
    for fen in ["8/4P3/8/8/8/8/8/k6K w - - 0 1", "k7/6P1/8/8/8/8/8/7K w - - 0 1", "7k/1P6/8/8/8/8/8/K7 w - - 0 1"]:
        promo.append(goal(fen, "promote", T("Promote the pawn: move it to the last rank and choose a piece.", "سرباز را ارتقا بده: آن را به ردیف آخر ببر و یک مهره انتخاب کن.", "سەربازەکە بەرز بکەرەوە: بیبە بۆ دوایین ڕیز و مۆرەیەک هەڵبژێرە.")))
    L.append(lesson("basics.pieces.promotion", T("Promotion", "ارتقای سرباز", "بەرزبوونەوەی سەرباز"),
        T("When a pawn reaches the last rank it must be promoted: you replace it with a queen, rook, bishop or knight of your colour. Almost always you choose a queen — but sometimes a knight gives check or avoids stalemate!",
          "وقتی سرباز به ردیف آخر برسد باید ارتقا پیدا کند: آن را با وزیر، رخ، فیل یا اسبِ هم‌رنگ خودت عوض می‌کنی. تقریباً همیشه وزیر انتخاب می‌شود — اما گاهی اسب کیش می‌دهد یا از پات جلوگیری می‌کند!",
          "کاتێک سەرباز دەگاتە دوایین ڕیز دەبێت بەرز بکرێتەوە: دەیگۆڕیت بە وەزیر، قەڵا، فیل یان ئەسپێکی ڕەنگی خۆت. نزیکەی هەمیشە وەزیر هەڵدەبژێردرێت — بەڵام هەندێک جار ئەسپ کش دەدات یان ڕێگری لە پات دەکات!"),
        1, promo + [goal("8/5P1k/8/8/8/8/8/K7 w - - 0 1", "promote_knight",
                         T("Promote to a knight — with check!", "به اسب ارتقا بده — با کیش!", "بیکە بە ئەسپ — بە کش!"))]))
    L.append(lesson("basics.pieces.review", T("All the pieces", "همه‌ی مهره‌ها", "هەموو مۆرەکان"),
        T("Time to review every piece. Remember: rook — straight lines; bishop — diagonals; queen — both; king — one square; knight — the L-jump.",
          "وقت مرور همه‌ی مهره‌هاست. یادت باشد: رخ — خط مستقیم؛ فیل — قطر؛ وزیر — هر دو؛ شاه — یک خانه؛ اسب — پرش L.",
          "کاتی پێداچوونەوەی هەموو مۆرەکانە. لەبیرت بێت: قەڵا — هێڵی ڕاست؛ فیل — لاکێش؛ وەزیر — هەردووکیان؛ شا — یەک خانە؛ ئەسپ — بازی L."),
        2, [stars(*random_stars(pt, n, obstacles=o), STAR_PROMPT) for pt, n, o in
            [(chess.ROOK, 3, 4), (chess.BISHOP, 3, 3), (chess.KNIGHT, 3, 2), (chess.QUEEN, 4, 5), (chess.KING, 3, 2)]]))
    L.append(lesson("basics.pieces.values", T("How much is each piece worth?", "ارزش هر مهره چقدر است؟", "بەهای هەر مۆرەیەک چەندە؟"),
        T("To decide whether a trade is good, players count points: pawn 1, knight 3, bishop 3, rook 5, queen 9. The king is priceless. Giving a knight (3) for a rook (5) wins 'the exchange'; giving a queen (9) for a rook (5) loses 4 points.",
          "برای اینکه بفهمی یک معاوضه خوب است یا نه، امتیازها را بشمار: سرباز ۱، اسب ۳، فیل ۳، رخ ۵، وزیر ۹. شاه بی‌قیمت است. دادن اسب (۳) در برابر رخ (۵) یعنی «بردن کیفیت»؛ دادن وزیر (۹) در برابر رخ (۵) یعنی از دست دادن ۴ امتیاز.",
          "بۆ ئەوەی بزانیت ئاڵوگۆڕێک باشە یان نا، خاڵەکان بژمێرە: سەرباز ١، ئەسپ ٣، فیل ٣، قەڵا ٥، وەزیر ٩. شا بێ نرخە. دانی ئەسپ (٣) بەرامبەر قەڵا (٥) واتە «بردنەوەی جیاوازی»؛ دانی وەزیر (٩) بەرامبەر قەڵا (٥) واتە لەدەستدانی ٤ خاڵ."),
        1, [
        quiz(T("Which is worth more?", "کدام ارزش بیشتری دارد؟", "کامیان بەهای زیاترە؟"), [T("A rook", "یک رخ", "قەڵایەک"), T("A knight", "یک اسب", "ئەسپێک")], 0),
        quiz(T("How many pawns is a queen worth?", "وزیر معادل چند سرباز است؟", "وەزیر بەرامبەر چەند سەربازە؟"), [T("3", "۳", "٣"), T("5", "۵", "٥"), T("9", "۹", "٩")], 2),
        quiz(T("You give a bishop and get a rook. Is that good?", "یک فیل می‌دهی و یک رخ می‌گیری. خوب است؟", "فیلێک دەدەیت و قەڵایەک وەردەگریت. باشە؟"),
             [T("Yes, +2 points", "بله، ۲+ امتیاز", "بەڵێ، +٢ خاڵ"), T("No, −2 points", "نه، ۲- امتیاز", "نەخێر، −٢ خاڵ"), T("It is equal", "مساوی است", "یەکسانە")], 0),
        quiz(T("A knight and a bishop are worth about…", "اسب و فیل تقریباً…", "ئەسپ و فیل نزیکەی…"),
             [T("the same", "هم‌ارزند", "هاوبەهان"), T("the knight is much better", "اسب خیلی بهتر است", "ئەسپ زۆر باشترە"), T("a rook each", "هر کدام یک رخ", "هەریەکە قەڵایەک")], 0),
        quiz(T("Two rooks versus a queen: who has more points?", "دو رخ در برابر یک وزیر: امتیاز کدام بیشتر است؟", "دوو قەڵا بەرامبەر وەزیرێک: کامیان خاڵی زیاترە؟"),
             [T("Two rooks (10)", "دو رخ (۱۰)", "دوو قەڵا (١٠)"), T("The queen (9)", "وزیر (۹)", "وەزیر (٩)")], 0),
    ]))
    return chapter("pieces", T("The pieces", "مهره‌ها", "مۆرەکان"), L)

# ------------------------------------------------------------------------------------------------ capturing

def material_positions(count, extra):
    spec = [("K", ["g1", "h1", "b1", "a1", "c1", "f1"]), ("k", ["g8", "h8", "b8", "a8", "c8", "f8"])]
    return spec

def win_material_positions(count):
    out, seen = [], set()
    while len(out) < count:
        spec = [("K", ["g1", "h1", "b1", "a1"]), ("k", ["g8", "h8", "b8", "a8"]),
                (RNG.choice(["Q", "R", "B", "N"]), None), (RNG.choice(["R", "B", "N", "P"]), None),
                (RNG.choice(["q", "r", "b", "n"]), None), (RNG.choice(["p", "n", "b", "r"]), None), ("p", None)]
        def cond(b):
            wins = [m for m in b.legal_moves if satisfies(b, m, "win_material")]
            if len(wins) != 1:
                return False
            return not hanging(b, chess.WHITE) and not b.is_check()
        fen = random_position(spec, cond=cond)
        if fen not in seen:
            seen.add(fen); out.append(fen)
    return out

def save_piece_positions(count):
    out, seen = [], set()
    while len(out) < count:
        spec = [("K", ["g1", "h1", "b1", "a1"]), ("k", ["g8", "h8", "b8", "a8"]),
                (RNG.choice(["Q", "R", "B", "N"]), None), ("P", None), ("P", None),
                (RNG.choice(["p", "n", "b"]), None), (RNG.choice(["p", "r", "n"]), None)]
        def cond(b):
            h = hanging(b, chess.WHITE)
            if len(h) != 1 or b.is_check():
                return False
            # the opponent must not already have something hanging (keep the lesson focused)
            if hanging(b, chess.BLACK):
                return False
            saves = [m for m in b.legal_moves if satisfies(b, m, "save_piece")]
            return 0 < len(saves) < b.legal_moves.count() // 2
        fen = random_position(spec, cond=cond)
        if fen not in seen:
            seen.add(fen); out.append(fen)
    return out

def capture_chapter():
    WIN = T("Win material: capture a piece that is not defended.", "مهره ببر: مهره‌ای را بزن که دفاع نشده است.", "مۆرە ببەرەوە: مۆرەیەک بگرە کە پارێزراو نییە.")
    SAVE = T("One of your pieces is attacked. Save it!", "یکی از مهره‌هایت زیر حمله است. نجاتش بده!", "یەکێک لە مۆرەکانت لە ژێر هێرشدایە. ڕزگاری بکە!")
    return chapter("capture", T("Capturing", "زدن مهره‌ها", "گرتنی مۆرەکان"), [
        lesson("basics.capture.rook", T("Capture with the rook", "زدن با رخ", "گرتن بە قەڵا"),
            T("You capture by moving your piece onto a square occupied by an enemy piece; the enemy piece leaves the board. Pieces capture the same way they move. Take all the black pieces in as few moves as you can.",
              "با بردن مهره‌ات روی خانه‌ای که مهره‌ی حریف در آن است، آن را می‌زنی و مهره‌ی حریف از صفحه خارج می‌شود. مهره‌ها همان‌طور که حرکت می‌کنند می‌زنند. همه‌ی مهره‌های سیاه را با کمترین حرکت بزن.",
              "بە بردنی مۆرەکەت بۆ سەر خانەیەک کە مۆرەی ڕکابەری تێدایە، دەیگریت و مۆرەی ڕکابەر لە تەختەکە دەردەچێت. مۆرەکان هەروەک چۆن دەجوڵێن ئاوا دەگرن. هەموو مۆرە ڕەشەکان بە کەمترین جوڵە بگرە."),
            1, capture_steps(chess.ROOK, [2, 3, 3, 4])),
        lesson("basics.capture.bishop", T("Capture with the bishop", "زدن با فیل", "گرتن بە فیل"),
            T("The bishop captures along diagonals. Plan your route: which piece should go first?",
              "فیل در امتداد قطرها می‌زند. مسیرت را برنامه‌ریزی کن: اول کدام مهره را بزنی؟",
              "فیل بە درێژایی لاکێشەکان دەگرێت. ڕێگاکەت پلان بکە: یەکەم کام مۆرە بگریت؟"),
            1, capture_steps(chess.BISHOP, [2, 3, 3, 4])),
        lesson("basics.capture.queen-knight", T("Capture with the queen and the knight", "زدن با وزیر و اسب", "گرتن بە وەزیر و ئەسپ"),
            T("The queen can reach almost anything; the knight needs clever jumps. Find the shortest path.",
              "وزیر تقریباً به هر جایی می‌رسد؛ اسب به پرش‌های هوشمندانه نیاز دارد. کوتاه‌ترین مسیر را پیدا کن.",
              "وەزیر دەتوانێت بگاتە نزیکەی هەموو شوێنێک؛ ئەسپ پێویستی بە بازی زیرەکانە هەیە. کورتترین ڕێگا بدۆزەرەوە."),
            2, capture_steps(chess.QUEEN, [3, 4]) + capture_steps(chess.KNIGHT, [2, 3])),
        lesson("basics.capture.free", T("Take free pieces", "مهره‌های مفت را بزن", "مۆرە بەخۆڕاییەکان بگرە"),
            T("A piece is 'hanging' when it is attacked and not defended enough. Capturing a hanging piece wins material for free. Before capturing, always check: can my piece be taken back?",
              "مهره‌ای «آویزان» است که به آن حمله شده و به‌اندازه‌ی کافی دفاع نشده باشد. زدن مهره‌ی آویزان یعنی بردن مهره به‌صورت مفت. قبل از زدن همیشه بررسی کن: آیا مهره‌ی من پس گرفته می‌شود؟",
              "مۆرەیەک «هەڵواسراوە» کاتێک هێرشی لەسەرە و بە باشی پارێزراو نییە. گرتنی مۆرەی هەڵواسراو واتە بردنەوەی مۆرە بە خۆڕایی. پێش گرتن هەمیشە بپشکنە: ئایا مۆرەکەم دەگیرێتەوە؟"),
            2, [goal(f, "win_material", WIN) for f in win_material_positions(6)]),
        lesson("basics.capture.protect", T("Protect your pieces", "از مهره‌هایت محافظت کن", "بەرگری لە مۆرەکانت بکە"),
            T("When the opponent attacks one of your pieces you can move it away, defend it with another piece, or block the attack. Look at every attacked piece before you make a move.",
              "وقتی حریف به یکی از مهره‌هایت حمله می‌کند، می‌توانی آن را جابه‌جا کنی، با مهره‌ی دیگری از آن دفاع کنی یا جلوی حمله را بگیری. قبل از هر حرکت به همه‌ی مهره‌های زیر حمله نگاه کن.",
              "کاتێک ڕکابەر هێرش دەکاتە سەر یەکێک لە مۆرەکانت، دەتوانیت بیجوڵێنیت، بە مۆرەیەکی تر بەرگری لێ بکەیت یان ڕێگری لە هێرشەکە بکەیت. پێش هەر جوڵەیەک سەیری هەموو مۆرە هێرشکراوەکان بکە."),
            2, [goal(f, "save_piece", SAVE) for f in save_piece_positions(6)]),
    ])

# ------------------------------------------------------------------------------------------------ check

def check_positions(g, count, only=False):
    out, seen = [], set()
    tries = 0
    while len(out) < count and tries < 400:
        tries += 1
        if g == "check":
            spec = [("K", None), ("k", None), (RNG.choice(["Q", "R", "B", "N"]), None), (RNG.choice(["R", "B", "N", "P"]), None), ("p", None)]
        else:
            spec = [("K", ["e1", "d1", "f1", "g1", "e2", "d3", "e3"]), ("k", None),
                    (RNG.choice(["q", "r", "b"]), None), (RNG.choice(["R", "B", "N", "Q"]), None), (RNG.choice(["N", "B", "P"]), None)]
        def cond(b):
            if g == "check":
                if b.is_check():
                    return False
            else:
                b2 = b.copy(); b2.turn = chess.WHITE
                if not b.is_check() or b.is_checkmate() or len(b.checkers()) != 1:
                    return False
            moves = list(b.legal_moves)
            ok = [m for m in moves if satisfies(b, m, g)]
            if not ok or len(ok) == len(moves):
                return False
            if only:
                return len(ok) == len(moves)
            return True
        def cond_only(b):
            if not b.is_check() or b.is_checkmate() or len(b.checkers()) != 1:
                return False
            moves = list(b.legal_moves)
            return all(satisfies(b, m, g) for m in moves)
        try:
            fen = random_position(spec, cond=cond_only if only else cond, tries=40000)
        except ContentError:
            continue
        if fen not in seen:
            seen.add(fen); out.append(fen)
    if len(out) < count:
        raise ContentError(f"not enough {g} positions")
    return out

def goal_only(fen, g, prompt):
    """Goal step where every legal move already satisfies the goal is pointless; build it without that check."""
    b = chess.Board(fen)
    assert any(satisfies(b, m, g) for m in b.legal_moves)
    return {"type": "goal", "fen": fen, "goal": g, "prompt": prompt}

def check_chapter():
    GIVE = T("Give check!", "کیش بده!", "کش بدە!")
    KING = T("You are in check: move the king to safety.", "کیش هستی: شاه را به جای امن ببر.", "لە کشدایت: شاکە ببە بۆ شوێنێکی ئارام.")
    CAP = T("You are in check: capture the attacking piece.", "کیش هستی: مهره‌ی حمله‌کننده را بزن.", "لە کشدایت: مۆرە هێرشبەرەکە بگرە.")
    BLOCK = T("You are in check: block the attack with a piece.", "کیش هستی: با یک مهره جلوی حمله را بگیر.", "لە کشدایت: بە مۆرەیەک ڕێگری لە هێرشەکە بکە.")
    ONLY = T("You are in check. Only one way out works here — find it!", "کیش هستی. این‌جا فقط یک راه نجات هست — پیدایش کن!", "لە کشدایت. لێرەدا تەنها یەک ڕێگای دەربازبوون هەیە — بیدۆزەرەوە!")
    return chapter("check", T("Check", "کیش", "کش"), [
        lesson("basics.check.give", T("What is check?", "کیش چیست؟", "کش چییە؟"),
            T("A king is in check when an enemy piece attacks it. Checking the opponent's king forces them to react at once, so checks are powerful. Find a move that attacks the black king.",
              "وقتی مهره‌ی حریف به شاه حمله کند، شاه «کیش» است. کیش دادن حریف را مجبور می‌کند فوراً واکنش نشان دهد، برای همین کیش‌ها قدرتمندند. حرکتی پیدا کن که به شاه سیاه حمله کند.",
              "شا لە کشدایە کاتێک مۆرەیەکی ڕکابەر هێرشی دەکاتە سەر. کشدان ڕکابەر ناچار دەکات یەکسەر وەڵام بداتەوە، بۆیە کشەکان بەهێزن. جوڵەیەک بدۆزەرەوە کە هێرش بکاتە سەر شای ڕەش."),
            1, [goal(f, "check", GIVE) for f in check_positions("check", 6)]),
        lesson("basics.check.king", T("Escape: move the king", "فرار: شاه را جابه‌جا کن", "دەربازبوون: شاکە بجوڵێنە"),
            T("When you are in check you must get out of it immediately. There are exactly three ways: move the king, capture the attacker, or put a piece in between. First way: step the king to a square that is not attacked.",
              "وقتی کیش هستی باید فوراً از آن خارج شوی. دقیقاً سه راه وجود دارد: شاه را حرکت بدهی، مهره‌ی حمله‌کننده را بزنی، یا مهره‌ای را بین آن‌ها بگذاری. راه اول: شاه را به خانه‌ای ببر که زیر حمله نیست.",
              "کاتێک لە کشدایت دەبێت یەکسەر لێی دەربچیت. ڕێک سێ ڕێگا هەیە: شاکە بجوڵێنیت، هێرشبەرەکە بگریت، یان مۆرەیەک بخەیتە نێوانیان. ڕێگای یەکەم: شاکە ببە بۆ خانەیەک کە هێرشی لەسەر نییە."),
            1, [goal(f, "king_escape", KING) for f in check_positions("king_escape", 5)]),
        lesson("basics.check.capture", T("Escape: capture the attacker", "فرار: مهره‌ی حمله‌کننده را بزن", "دەربازبوون: هێرشبەرەکە بگرە"),
            T("Second way out of check: capture the piece that gives check — with the king (if the piece is not protected) or with any other piece.",
              "راه دوم خروج از کیش: مهره‌ای را که کیش داده بزن — با شاه (اگر آن مهره دفاع نشده باشد) یا با هر مهره‌ی دیگر.",
              "ڕێگای دووەمی دەربازبوون لە کش: ئەو مۆرەیە بگرە کە کشی داوە — بە شا (ئەگەر مۆرەکە پارێزراو نەبێت) یان بە هەر مۆرەیەکی تر."),
            1, [goal(f, "capture_checker", CAP) for f in check_positions("capture_checker", 5)]),
        lesson("basics.check.block", T("Escape: block the check", "فرار: جلوی کیش را بگیر", "دەربازبوون: ڕێگری لە کش بکە"),
            T("Third way: put one of your pieces between your king and a rook, bishop or queen that gives check. A knight's check cannot be blocked.",
              "راه سوم: یکی از مهره‌هایت را بین شاه و رخ، فیل یا وزیری که کیش داده قرار بده. کیشِ اسب را نمی‌شود سد کرد.",
              "ڕێگای سێیەم: یەکێک لە مۆرەکانت بخە نێوان شاکەت و ئەو قەڵا، فیل یان وەزیرەی کشی داوە. کشی ئەسپ ناتوانرێت ڕێگری لێ بکرێت."),
            2, [goal(f, "block_check", BLOCK) for f in check_positions("block_check", 5)]),
        lesson("basics.check.review", T("Three ways out of check", "سه راه خروج از کیش", "سێ ڕێگای دەربازبوون لە کش"),
            T("Now you must decide which way works. In each position only one method saves the king.",
              "حالا باید تصمیم بگیری کدام راه جواب می‌دهد. در هر وضعیت فقط یک روش شاه را نجات می‌دهد.",
              "ئێستا دەبێت بڕیار بدەیت کام ڕێگا کار دەکات. لە هەر دۆخێکدا تەنها یەک ڕێگا شاکە ڕزگار دەکات."),
            2, [goal_only(f, "capture_checker", ONLY) for f in check_positions("capture_checker", 2, only=True)] +
               [goal_only(f, "block_check", ONLY) for f in check_positions("block_check", 2, only=True)] +
               [goal_only(f, "king_escape", ONLY) for f in check_positions("king_escape", 2, only=True)]),
    ])

# ------------------------------------------------------------------------------------------------ checkmate

import cb as _cb

def simple_mates(attacker, count):
    """K + attacker vs K (plus maybe a black pawn) with a mate in one."""
    out, seen = [], set()
    while len(out) < count:
        spec = [("k", ["a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8", "a7", "h7", "a5", "h4"]), ("K", None), (attacker, None)]
        def cond(b):
            if b.is_check():
                return False
            mates = [m for m in b.legal_moves if (b.push(m) or True) and (b.is_checkmate(), b.pop())[0]]
            return 1 <= len(mates) <= 2
        fen = random_position(spec, cond=cond)
        if fen not in seen:
            seen.add(fen); out.append(fen)
    return out

def mate_state_positions():
    """Black to move positions: checkmate, stalemate, or check (not mate), found by enumeration."""
    res = {"mate": [], "stale": [], "check": []}
    kings = [chess.A8, chess.H8, chess.H1, chess.E8, chess.A5]
    cands = []
    for k in kings:
        for K in chess.SQUARES:
            for Q in chess.SQUARES:
                if len({k, K, Q}) < 3 or chess.square_distance(k, K) < 2:
                    continue
                b = chess.Board(None)
                b.set_piece_at(k, chess.Piece(chess.KING, chess.BLACK))
                b.set_piece_at(K, chess.Piece(chess.KING, chess.WHITE))
                b.set_piece_at(Q, chess.Piece(chess.QUEEN, chess.WHITE))
                b.turn = chess.BLACK
                if not b.is_valid():
                    continue
                kind = "mate" if b.is_checkmate() else "stale" if b.is_stalemate() else "check" if b.is_check() else None
                if kind:
                    cands.append((kind, b.fen()))
    RNG.shuffle(cands)
    for kind, fen in cands:
        if len(res[kind]) < 2:
            res[kind].append(fen)
    return res

def mate_chapter():
    MATE1 = T("Checkmate in one move!", "در یک حرکت مات کن!", "لە یەک جوڵەدا کش‌مات بکە!")
    ms = mate_state_positions()
    Q = T("Black is to move. What is the situation?", "نوبت سیاه است. وضعیت چیست؟", "نۆرەی ڕەشە. دۆخەکە چییە؟")
    OPTS = [T("Checkmate", "مات", "کش‌مات"), T("Stalemate", "پات", "پات"), T("Check, but not mate", "کیش، اما نه مات", "کش، بەڵام کش‌مات نییە")]
    kinds = [("mate", 0), ("stale", 1), ("check", 2)]
    quizzes = []
    for i in range(2):
        for k, ans in kinds:
            quizzes.append(quiz(Q, OPTS, ans, None, fen=ms[k][i]))
    M = _cb.DATA["mates"]
    def pat(name, n):
        return [mate(e["fen"], 1, MATE1) for e in M[name][:n]]
    return chapter("mate", T("Checkmate", "مات", "کش‌مات"), [
        lesson("basics.mate.what", T("What is checkmate?", "مات چیست؟", "کش‌مات چییە؟"),
            T("Checkmate means the king is in check and there is no way out: it cannot move, the attacker cannot be captured and the check cannot be blocked. Checkmate ends the game — the side that gives mate wins.",
              "مات یعنی شاه کیش است و هیچ راه نجاتی ندارد: نمی‌تواند حرکت کند، مهره‌ی حمله‌کننده زده نمی‌شود و جلوی کیش هم گرفته نمی‌شود. مات بازی را تمام می‌کند — کسی که مات می‌کند برنده است.",
              "کش‌مات واتە شا لە کشدایە و هیچ ڕێگای دەربازبوونی نییە: ناتوانێت بجوڵێت، هێرشبەرەکە ناگیرێت و ڕێگری لە کشەکەش ناکرێت. کش‌مات یارییەکە کۆتایی پێدەهێنێت — ئەوەی کش‌مات دەکات دەیباتەوە."),
            1, [theory(T("The black king is in check from the rook and every escape square is covered: checkmate.",
                         "شاه سیاه از طرف رخ کیش است و همه‌ی خانه‌های فرارش زیر پوشش‌اند: مات.",
                         "شای ڕەش لە لایەن قەڵاوە لە کشدایە و هەموو خانەکانی ڕاکردنی داپۆشراون: کش‌مات."),
                       fen="R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1", marks=["g8"], arrows=["a8g8"])]
               + quizzes[:3] + [mate(f, 1, MATE1) for f in simple_mates("Q", 3)]),
        lesson("basics.mate.queen", T("Mate with the queen", "مات با وزیر", "کش‌مات بە وەزیر"),
            T("The queen can mate on her own when the enemy king is on the edge of the board and your king helps by guarding the squares next to it.",
              "وزیر وقتی شاه حریف در لبه‌ی صفحه است و شاه خودت خانه‌های کنارش را پوشش می‌دهد، می‌تواند به‌تنهایی مات کند.",
              "وەزیر دەتوانێت بە تەنها کش‌مات بکات کاتێک شای ڕکابەر لە لێواری تەختەکەدایە و شاکەت یارمەتی دەدات بە پاراستنی خانەکانی دەوروبەری."),
            1, [mate(f, 1, MATE1) for f in simple_mates("Q", 6)]),
        lesson("basics.mate.rook", T("Mate with the rook", "مات با رخ", "کش‌مات بە قەڵا"),
            T("A rook mates along the edge: it checks on the edge rank or file while your king stands opposite the enemy king and takes away its escape squares.",
              "رخ در لبه مات می‌کند: روی ردیف یا ستون لبه کیش می‌دهد در حالی که شاه تو روبه‌روی شاه حریف ایستاده و خانه‌های فرارش را می‌گیرد.",
              "قەڵا لە لێوارەکەدا کش‌مات دەکات: لەسەر ڕیز یان ستوونی لێوار کش دەدات لە کاتێکدا شاکەت بەرامبەر شای ڕکابەر وەستاوە و خانەکانی ڕاکردنی دەگرێت."),
            1, [mate(f, 1, MATE1) for f in simple_mates("R", 6)]),
        lesson("basics.mate.backrank", T("The back-rank mate", "مات ردیف آخر", "کش‌ماتی ڕیزی دواوە"),
            T("A king that has castled often hides behind its own pawns. If no piece guards the back rank, a rook or queen can mate there — the king is trapped by its own pawns. It is one of the most common mates in real games!",
              "شاهی که قلعه رفته معمولاً پشت سربازهای خودش پنهان است. اگر هیچ مهره‌ای از ردیف آخر محافظت نکند، رخ یا وزیر همان‌جا مات می‌کند — شاه توسط سربازهای خودش زندانی شده است. این یکی از رایج‌ترین مات‌ها در بازی‌های واقعی است!",
              "شایەک کە قەڵابەندی کردووە زۆرجار لە پشت سەربازەکانی خۆی خۆی دەشارێتەوە. ئەگەر هیچ مۆرەیەک ڕیزی دواوە نەپارێزێت، قەڵا یان وەزیر لەوێ کش‌مات دەکات — شاکە لەلایەن سەربازەکانی خۆیەوە گیراوە. ئەمە یەکێکە لە باوترین کش‌ماتەکان لە یارییە ڕاستەقینەکاندا!"),
            2, pat("backRank", 6)),
        lesson("basics.mate.minor", T("Mates with bishops and knights", "مات با فیل و اسب", "کش‌مات بە فیل و ئەسپ"),
            T("Bishops and knights can also deliver mate, usually with help from other pieces — or when the enemy king is surrounded by its own men. Look for checks that leave no escape.",
              "فیل و اسب هم می‌توانند مات کنند، معمولاً با کمک مهره‌های دیگر — یا وقتی شاه حریف در محاصره‌ی مهره‌های خودش است. دنبال کیشی باش که راه فراری باقی نگذارد.",
              "فیل و ئەسپیش دەتوانن کش‌مات بکەن، زۆرجار بە یارمەتی مۆرەکانی تر — یان کاتێک شای ڕکابەر لە ناو مۆرەکانی خۆیدا گیراوە. بەدوای کشێکدا بگەڕێ کە ڕێگای ڕاکردن نەهێڵێت."),
            2, pat("bishopMate", 3) + pat("knightMate", 3)),
        lesson("basics.mate.practice", T("Mate in one: practice", "مات در یک حرکت: تمرین", "کش‌مات لە یەک جوڵەدا: ڕاهێنان"),
            T("Checks, captures, threats — look at every check first. Each of these positions has a mate in one.",
              "کیش‌ها، زدن‌ها، تهدیدها — اول همه‌ی کیش‌ها را بررسی کن. هر یک از این وضعیت‌ها یک مات در یک حرکت دارد.",
              "کش، گرتن، هەڕەشە — سەرەتا هەموو کشەکان بپشکنە. هەر یەکێک لەم دۆخانە کش‌ماتێکی یەک جوڵەیی تێدایە."),
            2, pat("queenBishop", 2) + pat("queenKnight", 2) + pat("queenPawn", 2) + pat("ladder", 2)),
        lesson("basics.mate.stalemate", T("Checkmate or stalemate?", "مات یا پات؟", "کش‌مات یان پات؟"),
            T("Stalemate is different from checkmate: the player to move is NOT in check but has no legal move. Stalemate is a draw! When you are winning, be careful not to stalemate your opponent.",
              "پات با مات فرق دارد: بازیکنی که نوبتش است کیش نیست، اما هیچ حرکت مجازی ندارد. پات یعنی تساوی! وقتی در حال بردن هستی، مراقب باش حریف را پات نکنی.",
              "پات جیاوازە لە کش‌مات: یاریزانی خاوەن نۆرە لە کشدا نییە بەڵام هیچ جوڵەیەکی ڕێگەپێدراوی نییە. پات واتە یەکسانی! کاتێک دەیبەیتەوە، ئاگادار بە ڕکابەر پات نەکەیت."),
            2, [theory(T("Black to move: the king is not in check, but every square is covered. Stalemate — a draw.",
                         "نوبت سیاه: شاه کیش نیست، اما همه‌ی خانه‌ها زیر پوشش‌اند. پات — تساوی.",
                         "نۆرەی ڕەش: شاکە لە کشدا نییە، بەڵام هەموو خانەکان داپۆشراون. پات — یەکسانی."),
                       fen="k7/8/1QK5/8/8/8/8/8 b - - 0 1", marks=["a8"])] + quizzes[3:]),
    ])

# ------------------------------------------------------------------------------------------------ special moves

def castle_positions(side, count):
    out = []
    bases = ["r3k2r/pppq1ppp/2n1bn2/3pp3/3PP3/2N1BN2/PPPQ1PPP/R3K2R w KQkq - 0 1",
             "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1",
             "r3kb1r/ppp1pppp/2nq1n2/3p1b2/3P1B2/2NQ1N2/PPP1PPPP/R3KB1R w KQkq - 0 1",
             "rnbqk2r/ppp1bppp/4pn2/3p4/2PP4/5NP1/PP2PPBP/RNBQK2R w KQkq - 0 1",
             "r3kbnr/ppp1pppp/2nq4/3p1b2/3P1B2/2NQ4/PPP1PPPP/R3KBNR w KQkq - 0 1"]
    for f in bases:
        b = chess.Board(f)
        if any(satisfies(b, m, side) for m in b.legal_moves):
            out.append(f)
    return out[:count]

def special_chapter():
    CS = T("Castle kingside.", "قلعه‌ی کوتاه برو.", "قەڵابەندی کورت بکە.")
    CL = T("Castle queenside.", "قلعه‌ی بلند برو.", "قەڵابەندی درێژ بکە.")
    EP = T("Capture en passant!", "آنپاسان بزن!", "ئەنپاسان بگرە!")
    CANQ = T("Can White castle kingside right now?", "آیا سفید همین حالا می‌تواند قلعه‌ی کوتاه برود؟", "ئایا سپی ئێستا دەتوانێت قەڵابەندی کورت بکات؟")
    YES, NO = T("Yes", "بله", "بەڵێ"), T("No", "نه", "نەخێر")
    ep_fens = ["rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
               "rnbqkbnr/pp1ppppp/8/8/2pPP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 3",
               "4k3/8/8/8/1pP5/8/8/4K3 b - c3 0 1",
               "4k3/8/8/2pP4/8/8/8/4K3 w - c6 0 1"]
    ep_steps = []
    for f in ep_fens:
        ep_steps.append(goal(f, "en_passant", EP))
    cant = [
        ("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", 0, T("Nothing prevents it: king and rook have not moved and nothing is in between.", "هیچ مانعی نیست: شاه و رخ حرکت نکرده‌اند و چیزی بینشان نیست.", "هیچ ڕێگرییەک نییە: شا و قەڵا نەجوڵاون و هیچ شتێک لە نێوانیاندا نییە.")),
        ("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1", 1, T("The rook on f2 attacks f1: the king may not pass through an attacked square.", "رخ f2 به f1 حمله می‌کند: شاه نمی‌تواند از خانه‌ی زیر حمله عبور کند.", "قەڵای f2 هێرش دەکاتە سەر f1: شا ناتوانێت بە خانەیەکی هێرشکراودا تێپەڕێت.")),
        ("4k3/8/8/8/8/8/4r3/R3K2R w KQ - 0 1", 1, T("White is in check. You cannot castle out of check.", "سفید کیش است. نمی‌شود برای فرار از کیش قلعه رفت.", "سپی لە کشدایە. ناتوانیت بۆ دەربازبوون لە کش قەڵابەندی بکەیت.")),
        ("4k3/8/8/8/8/8/8/R3KB1R w KQ - 0 1", 1, T("The bishop on f1 is in the way.", "فیل f1 سر راه است.", "فیلی f1 لەسەر ڕێگایە.")),
    ]
    promo_tactics = [goal("8/1P6/8/8/8/8/k7/4K3 w - - 0 1", "promote", T("Make a new queen!", "یک وزیر جدید بساز!", "وەزیرێکی نوێ دروست بکە!")),
                     goal("3r2k1/2P2ppp/8/8/8/8/5PPP/6K1 w - - 0 1", "promote", T("Promote — capturing is allowed too.", "ارتقا بده — با زدن هم می‌شود.", "بەرزی بکەرەوە — بە گرتنیش دەبێت."))]
    return chapter("special", T("Special moves", "حرکت‌های ویژه", "جوڵە تایبەتەکان"), [
        lesson("basics.special.castling", T("Castling", "قلعه رفتن", "قەڵابەندی"),
            T("Castling is the only move where two pieces move at once: the king goes two squares towards a rook, and the rook jumps to the other side of the king. Castling puts your king in safety and brings a rook into the game. Kingside castling is written O-O, queenside O-O-O.",
              "قلعه تنها حرکتی است که در آن دو مهره هم‌زمان حرکت می‌کنند: شاه دو خانه به سمت رخ می‌رود و رخ به طرف دیگر شاه می‌پرد. قلعه شاه را در امان می‌گذارد و رخ را وارد بازی می‌کند. قلعه‌ی کوتاه O-O و قلعه‌ی بلند O-O-O نوشته می‌شود.",
              "قەڵابەندی تاکە جوڵەیە کە دوو مۆرە پێکەوە دەجوڵێن: شا دوو خانە بەرەو قەڵا دەچێت و قەڵا باز دەداتە ئەولای شا. قەڵابەندی شاکەت دەپارێزێت و قەڵایەک دەهێنێتە ناو یارییەکە. قەڵابەندی کورت O-O و درێژ O-O-O دەنووسرێت."),
            1, [theory(T("After O-O the white king stands on g1 and the rook on f1.", "بعد از O-O شاه سفید روی g1 و رخ روی f1 است.", "دوای O-O شای سپی لەسەر g1 و قەڵا لەسەر f1ە."),
                       fen="r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1", arrows=["e1g1", "h1f1"])]
               + [goal(f, "castle_short", CS) for f in castle_positions("castle_short", 2)]
               + [goal(f, "castle_long", CL) for f in castle_positions("castle_long", 2)]),
        lesson("basics.special.castling-rules", T("When you cannot castle", "وقتی نمی‌شود قلعه رفت", "کەی ناتوانیت قەڵابەندی بکەیت"),
            T("You may castle only if: the king and that rook have never moved, no pieces stand between them, the king is not in check, and the king does not pass through or land on an attacked square.",
              "فقط وقتی می‌توانی قلعه بروی که: شاه و آن رخ هرگز حرکت نکرده باشند، هیچ مهره‌ای بینشان نباشد، شاه کیش نباشد و شاه از خانه‌ی زیر حمله عبور نکند یا در آن فرود نیاید.",
              "تەنها کاتێک دەتوانیت قەڵابەندی بکەیت کە: شا و ئەو قەڵایە هەرگیز نەجوڵابن، هیچ مۆرەیەک لە نێوانیاندا نەبێت، شا لە کشدا نەبێت و شا بە خانەیەکی هێرشکراودا تێنەپەڕێت و لەسەری نەنیشێتەوە."),
            2, [quiz(CANQ, [YES, NO], a, e, fen=f) for f, a, e in cant]),
        lesson("basics.special.enpassant", T("En passant", "آنپاسان", "ئەنپاسان"),
            T("If a pawn moves two squares and lands right next to an enemy pawn, that enemy pawn may capture it as if it had moved only one square. This special capture is called en passant ('in passing') and is allowed only on the very next move.",
              "اگر سربازی دو خانه حرکت کند و درست کنار سرباز حریف بایستد، سرباز حریف می‌تواند آن را طوری بزند که انگار فقط یک خانه رفته است. این زدن ویژه آنپاسان («در حال عبور») نام دارد و فقط در حرکت بلافاصله بعدی مجاز است.",
              "ئەگەر سەربازێک دوو خانە بجوڵێت و ڕێک بکەوێتە تەنیشت سەربازی ڕکابەر، ئەو سەربازە دەتوانێت بیگرێت وەک ئەوەی تەنها یەک خانە جوڵابێت. ئەم گرتنە تایبەتە ئەنپاسان («لە کاتی تێپەڕبوون») ناوی لێنراوە و تەنها لە جوڵەی دواتردا ڕێگەپێدراوە."),
            2, [theory(T("Black just played f7–f5. The e5-pawn can capture it by moving to f6.", "سیاه همین حالا f7 به f5 را بازی کرد. سرباز e5 می‌تواند با رفتن به f6 آن را بزند.", "ڕەش ئێستا f7 بۆ f5ی یاری کرد. سەربازی e5 دەتوانێت بە چوون بۆ f6 بیگرێت."),
                       fen="rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", arrows=["e5f6"], marks=["f5"])] + ep_steps),
        lesson("basics.special.promotion", T("Promotion in practice", "ارتقا در عمل", "بەرزبوونەوە لە کرداردا"),
            T("A passed pawn that reaches the eighth rank becomes a queen and usually decides the game. A pawn may promote by moving straight or by capturing diagonally.",
              "سرباز رونده‌ای که به ردیف هشتم برسد وزیر می‌شود و معمولاً سرنوشت بازی را تعیین می‌کند. سرباز می‌تواند با حرکت مستقیم یا با زدن قطری ارتقا پیدا کند.",
              "سەربازێکی ڕاکەر کە بگاتە ڕیزی هەشتەم دەبێتە وەزیر و زۆرجار یارییەکە یەکلا دەکاتەوە. سەرباز دەتوانێت بە جوڵەی ڕاست یان بە گرتنی لاکێش بەرز بێتەوە."),
            2, promo_tactics + [goal("8/k1P5/8/1K6/8/8/8/8 w - - 0 1", "promote", T("Promote the pawn and win!", "سرباز را ارتقا بده و ببر!", "سەربازەکە بەرز بکەرەوە و بیبەرەوە!"))]),
    ])

# ------------------------------------------------------------------------------------------------ draws

def stalemate_traps(count):
    """K+Q vs K, White to move: a mate in one exists, but at least two queen moves give stalemate."""
    out = []
    squares = list(chess.SQUARES)
    RNG.shuffle(squares)
    for k in [chess.A8, chess.H8, chess.H1, chess.A1, chess.E8, chess.A4]:
        for K in squares:
            for Q in squares:
                if len({k, K, Q}) < 3 or chess.square_distance(k, K) < 2:
                    continue
                b = chess.Board(None)
                b.set_piece_at(k, chess.Piece(chess.KING, chess.BLACK))
                b.set_piece_at(K, chess.Piece(chess.KING, chess.WHITE))
                b.set_piece_at(Q, chess.Piece(chess.QUEEN, chess.WHITE))
                b.turn = chess.WHITE
                if not b.is_valid() or b.is_check():
                    continue
                mates = stales = 0
                for m in b.legal_moves:
                    b.push(m)
                    if b.is_checkmate(): mates += 1
                    elif b.is_stalemate(): stales += 1
                    b.pop()
                if mates >= 1 and stales >= 2:
                    out.append((k, K, b.fen()))
    RNG.shuffle(out)
    picked, used = [], set()
    for k, K, fen in out:
        if (k, K) in used:
            continue
        used.add((k, K))
        picked.append(fen)
    return picked[:count]

def draws_chapter():
    DRAWQ = T("Is this position a draw by insufficient material?", "آیا این وضعیت به دلیل کمبود مهره مساوی است؟", "ئایا ئەم دۆخە بەهۆی کەمیی مۆرەوە یەکسانە؟")
    YES, NO = T("Yes, nobody can mate", "بله، هیچ‌کس نمی‌تواند مات کند", "بەڵێ، کەس ناتوانێت کش‌مات بکات"), T("No, mate is still possible", "نه، هنوز مات ممکن است", "نەخێر، هێشتا کش‌مات دەکرێت")
    return chapter("draws", T("Draws", "تساوی", "یەکسانی"), [
        lesson("basics.draws.rules", T("How a game can be drawn", "بازی چطور مساوی می‌شود", "یاری چۆن یەکسان دەبێت"),
            T("A game is drawn by stalemate, by agreement, when the same position appears three times (threefold repetition), when 50 moves pass without a capture or pawn move, or when neither side has enough material to mate.",
              "بازی در این حالت‌ها مساوی می‌شود: پات، توافق دو بازیکن، تکرار سه‌باره‌ی یک وضعیت، گذشتن ۵۰ حرکت بدون زدن مهره یا حرکت سرباز، یا وقتی هیچ‌کدام مهره‌ی کافی برای مات کردن نداشته باشند.",
              "یاری لەم حاڵەتانەدا یەکسان دەبێت: پات، ڕێککەوتنی هەردوو یاریزان، دووبارەبوونەوەی هەمان دۆخ سێ جار، تێپەڕبوونی ٥٠ جوڵە بەبێ گرتنی مۆرە یان جوڵەی سەرباز، یان کاتێک هیچ لایەک مۆرەی پێویستی بۆ کش‌مات نییە."),
            1, [
            quiz(T("The same position has appeared three times. What happens?", "یک وضعیت سه بار تکرار شده است. چه می‌شود؟", "هەمان دۆخ سێ جار دووبارە بووەتەوە. چی ڕوودەدات؟"),
                 [T("The game can be claimed a draw", "بازی مساوی اعلام می‌شود", "یاری یەکسان ڕادەگەیەنرێت"), T("White wins", "سفید می‌برد", "سپی دەیباتەوە"), T("Nothing", "هیچ", "هیچ")], 0),
            quiz(T("50 moves in a row without a capture or a pawn move…", "۵۰ حرکت پشت سر هم بدون زدن مهره یا حرکت سرباز…", "٥٠ جوڵەی لەسەریەک بەبێ گرتن یان جوڵەی سەرباز…"),
                 [T("…is a draw", "…تساوی است", "…یەکسانییە"), T("…is a win for the stronger side", "…برد طرف قوی‌تر است", "…بردنەوەی لایەنی بەهێزترە")], 0),
            quiz(DRAWQ, [YES, NO], 0, T("King and bishop cannot mate a lone king.", "شاه و فیل نمی‌توانند شاه تنها را مات کنند.", "شا و فیل ناتوانن شای تەنها کش‌مات بکەن."), fen="8/8/4k3/8/8/2B5/8/4K3 w - - 0 1"),
            quiz(DRAWQ, [YES, NO], 1, T("King and rook can always force mate.", "شاه و رخ همیشه می‌توانند مات کنند.", "شا و قەڵا هەمیشە دەتوانن کش‌مات بکەن."), fen="8/8/4k3/8/8/2R5/8/4K3 w - - 0 1"),
            quiz(DRAWQ, [YES, NO], 0, T("A single knight cannot mate.", "یک اسب تنها نمی‌تواند مات کند.", "ئەسپێکی تەنها ناتوانێت کش‌مات بکات."), fen="8/8/4k3/8/8/2N5/8/4K3 w - - 0 1"),
            quiz(DRAWQ, [YES, NO], 1, T("A pawn can still become a queen.", "یک سرباز هنوز می‌تواند وزیر شود.", "سەربازێک هێشتا دەتوانێت ببێتە وەزیر."), fen="8/8/4k3/8/8/2P5/8/4K3 w - - 0 1"),
        ]),
        lesson("basics.draws.stalemate-traps", T("Don't stalemate!", "پات نکن!", "پات مەکە!"),
            T("When you are far ahead, the biggest danger is stalemate. Before every move, check that your opponent still has a legal move — unless you are giving checkmate.",
              "وقتی خیلی جلو هستی، بزرگ‌ترین خطر پات است. قبل از هر حرکت مطمئن شو حریف هنوز حرکت مجازی دارد — مگر اینکه داری مات می‌کنی.",
              "کاتێک زۆر لە پێشیت، گەورەترین مەترسی پاتە. پێش هەر جوڵەیەک دڵنیابە کە ڕکابەر هێشتا جوڵەیەکی ڕێگەپێدراوی هەیە — مەگەر کش‌مات بکەیت."),
            2, [mate(f, 1, T("Mate in one — careful, some queen moves are stalemate!", "مات در یک حرکت — مراقب باش، بعضی حرکت‌های وزیر پات می‌کنند!", "کش‌مات لە یەک جوڵەدا — ئاگادار بە، هەندێک جوڵەی وەزیر پات دەکەن!")) for f in stalemate_traps(5)]),
    ])

# ------------------------------------------------------------------------------------------------ notation

def notation_chapter():
    def play_move(fen, san, prompt):
        b = chess.Board(fen)
        m = b.parse_san(san)
        return best(fen, prompt, accept=[m.uci()])
    AFTER_E4E5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
    return chapter("notation", T("Chess notation", "نوشتن حرکات شطرنج", "نووسینی جوڵەکانی شەترەنج"), [
        lesson("basics.notation.read", T("Reading chess moves", "خواندن حرکت‌های شطرنج", "خوێندنەوەی جوڵەکانی شەترەنج"),
            T("Moves are written with the piece letter and the destination square: K king, Q queen, R rook, B bishop, N knight; pawns have no letter. 'Nf3' means a knight moves to f3, 'e4' means a pawn moves to e4.",
              "حرکت‌ها با حرف مهره و خانه‌ی مقصد نوشته می‌شوند: K شاه، Q وزیر، R رخ، B فیل، N اسب؛ سرباز حرف ندارد. «Nf3» یعنی اسب به f3 می‌رود و «e4» یعنی سرباز به e4 می‌رود.",
              "جوڵەکان بە پیتی مۆرە و خانەی مەبەست دەنووسرێن: K شا، Q وەزیر، R قەڵا، B فیل، N ئەسپ؛ سەرباز پیتی نییە. «Nf3» واتە ئەسپ دەچێتە f3 و «e4» واتە سەرباز دەچێتە e4."),
            1, [
            quiz(T("What does 'Nf3' mean?", "«Nf3» یعنی چه؟", "«Nf3» واتە چی؟"), [T("A knight moves to f3", "اسب به f3 می‌رود", "ئەسپ دەچێتە f3"), T("A pawn moves to f3", "سرباز به f3 می‌رود", "سەرباز دەچێتە f3"), T("The king moves to f3", "شاه به f3 می‌رود", "شا دەچێتە f3")], 0),
            quiz(T("Which letter stands for the bishop?", "کدام حرف برای فیل است؟", "کام پیت بۆ فیلە؟"), [T("B", "B", "B"), T("K", "K", "K"), T("N", "N", "N")], 0),
            quiz(T("What does 'e4' mean?", "«e4» یعنی چه؟", "«e4» واتە چی؟"), [T("A pawn moves to e4", "سرباز به e4 می‌رود", "سەرباز دەچێتە e4"), T("The queen moves to e4", "وزیر به e4 می‌رود", "وەزیر دەچێتە e4")], 0),
            quiz(T("Which letter is used for the knight?", "کدام حرف برای اسب است؟", "کام پیت بۆ ئەسپە؟"), [T("K", "K", "K"), T("N", "N", "N"), T("H", "H", "H")], 1, T("K is taken by the king, so the knight is N.", "K مال شاه است، پس اسب N است.", "K بۆ شایە، بۆیە ئەسپ Nە.")),
        ]),
        lesson("basics.notation.play", T("Play the written move", "حرکت نوشته‌شده را بازی کن", "جوڵە نووسراوەکە یاری بکە"),
            T("Now turn notation into moves on the board.", "حالا نوشته‌ها را به حرکت روی صفحه تبدیل کن.", "ئێستا نووسینەکان بکە بە جوڵە لەسەر تەختەکە."),
            1, [play_move(START, "e4", T("Play e4.", "e4 را بازی کن.", "e4 یاری بکە.")),
                play_move(START, "Nf3", T("Play Nf3.", "Nf3 را بازی کن.", "Nf3 یاری بکە.")),
                play_move(AFTER_E4E5, "Nf3", T("Play Nf3.", "Nf3 را بازی کن.", "Nf3 یاری بکە.")),
                play_move("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", "Bb5", T("Play Bb5.", "Bb5 را بازی کن.", "Bb5 یاری بکە.")),
                play_move("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1", "O-O", T("Play O-O.", "O-O را بازی کن.", "O-O یاری بکە.")),
                play_move("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "exd5", T("Play exd5.", "exd5 را بازی کن.", "exd5 یاری بکە."))]),
        lesson("basics.notation.symbols", T("Captures, checks and more", "زدن، کیش و علامت‌های دیگر", "گرتن، کش و هێماکانی تر"),
            T("An 'x' means a capture (Bxc6), '+' means check, '#' means checkmate, 'O-O' is kingside castling and 'O-O-O' queenside castling, '=Q' is promotion to a queen. Books also add '!' for a good move and '?' for a mistake.",
              "«x» یعنی زدن (Bxc6)، «+» یعنی کیش، «#» یعنی مات، «O-O» قلعه‌ی کوتاه و «O-O-O» قلعه‌ی بلند است و «=Q» یعنی ارتقا به وزیر. در کتاب‌ها «!» برای حرکت خوب و «?» برای اشتباه هم می‌آید.",
              "«x» واتە گرتن (Bxc6)، «+» واتە کش، «#» واتە کش‌مات، «O-O» قەڵابەندی کورت و «O-O-O» قەڵابەندی درێژە و «=Q» واتە بەرزبوونەوە بۆ وەزیر. لە کتێبەکاندا «!» بۆ جوڵەی باش و «?» بۆ هەڵەش دادەنرێت."),
            1, [
            quiz(T("What does '#' mean?", "«#» یعنی چه؟", "«#» واتە چی؟"), [T("Check", "کیش", "کش"), T("Checkmate", "مات", "کش‌مات"), T("Capture", "زدن", "گرتن")], 1),
            quiz(T("What does 'O-O-O' mean?", "«O-O-O» یعنی چه؟", "«O-O-O» واتە چی؟"), [T("Castling queenside", "قلعه‌ی بلند", "قەڵابەندی درێژ"), T("Castling kingside", "قلعه‌ی کوتاه", "قەڵابەندی کورت")], 0),
            quiz(T("What does 'Bxc6+' mean?", "«Bxc6+» یعنی چه؟", "«Bxc6+» واتە چی؟"), [T("The bishop captures on c6 with check", "فیل در c6 می‌زند و کیش می‌دهد", "فیل لە c6 دەگرێت و کش دەدات"), T("The bishop moves to c6 and is captured", "فیل به c6 می‌رود و زده می‌شود", "فیل دەچێتە c6 و دەگیرێت")], 0),
            quiz(T("What does 'e8=Q' mean?", "«e8=Q» یعنی چه؟", "«e8=Q» واتە چی؟"), [T("A pawn promotes to a queen on e8", "سرباز در e8 وزیر می‌شود", "سەرباز لە e8 دەبێتە وەزیر"), T("The queen moves to e8", "وزیر به e8 می‌رود", "وەزیر دەچێتە e8")], 0),
        ]),
    ])

# ------------------------------------------------------------------------------------------------ first games

OPENING_POSITIONS = {
    "center": [START, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"],
    "develop": ["rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                "rnbqkb1r/pppp1ppp/5n2/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR w KQkq - 2 3",
                "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
                "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"],
    "castle": ["r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 1 5",
               "r1bqk2r/ppppbppp/2n2n2/4p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R b KQkq - 0 5",
               "rnbqk2r/ppp1bppp/4pn2/3p4/2PP4/5NP1/PP2PPBP/RNBQK2R w KQkq - 2 5"],
}

def first_games_chapter():
    GOOD = T("Find a good move that follows the opening principles.", "یک حرکت خوب پیدا کن که با اصول گشایش جور باشد.", "جوڵەیەکی باش بدۆزەرەوە کە لەگەڵ بنەماکانی دەستپێک بگونجێت.")
    SAFE = T("Your piece is attacked — make a move that keeps everything safe.", "مهره‌ات زیر حمله است — حرکتی بکن که همه‌چیز امن بماند.", "مۆرەکەت لە ژێر هێرشدایە — جوڵەیەک بکە کە هەموو شت ئارام بمێنێتەوە.")
    return chapter("first-games", T("Your first games", "اولین بازی‌های تو", "یەکەم یارییەکانت"), [
        lesson("basics.first.center", T("Fight for the center", "برای مرکز بجنگ", "بۆ ناوەند تێبکۆشە"),
            T("The four central squares d4, e4, d5 and e5 are the most important on the board. Pieces in the center control more squares and can quickly go anywhere. Start the game by occupying or attacking the center with pawns.",
              "چهار خانه‌ی مرکزی d4، e4، d5 و e5 مهم‌ترین خانه‌های صفحه‌اند. مهره‌ها در مرکز خانه‌های بیشتری را کنترل می‌کنند و سریع به هر جا می‌رسند. بازی را با اشغال یا حمله به مرکز با سربازها شروع کن.",
              "چوار خانە ناوەندییەکە d4، e4، d5 و e5 گرنگترین خانەکانی تەختەکەن. مۆرەکان لە ناوەنددا خانەی زیاتر کۆنترۆڵ دەکەن و خێرا دەگەنە هەموو شوێنێک. یارییەکە بە داگیرکردن یان هێرشکردنە سەر ناوەند بە سەربازەکان دەست پێبکە."),
            1, [theory(T("The four central squares.", "چهار خانه‌ی مرکزی.", "چوار خانە ناوەندییەکە."), fen=START, marks=["d4", "e4", "d5", "e5"])]
               + [best(f, GOOD, margin=35) for f in OPENING_POSITIONS["center"]]),
        lesson("basics.first.develop", T("Develop your pieces", "مهره‌هایت را وارد بازی کن", "مۆرەکانت بهێنە ناو یاری"),
            T("In the opening, bring your knights and bishops off the back rank towards the center — 'knights before bishops' is a good rule. Don't move the same piece twice without a reason, and don't bring the queen out too early: she can be chased and you lose time.",
              "در گشایش، اسب‌ها و فیل‌ها را از ردیف اول به سمت مرکز بیاور — «اول اسب‌ها، بعد فیل‌ها» قانون خوبی است. بدون دلیل یک مهره را دو بار حرکت نده و وزیر را زود بیرون نیاور: حریف دنبالش می‌کند و تو وقت از دست می‌دهی.",
              "لە دەستپێکدا، ئەسپ و فیلەکان لە ڕیزی یەکەمەوە بەرەو ناوەند بهێنە — «سەرەتا ئەسپەکان، پاشان فیلەکان» یاسایەکی باشە. بەبێ هۆ یەک مۆرە دوو جار مەجوڵێنە و وەزیر زوو دەرمەهێنە: ڕکابەر ڕاوی دەنێت و تۆ کات لەدەست دەدەیت."),
            1, [best(f, GOOD, margin=35) for f in OPENING_POSITIONS["develop"]]),
        lesson("basics.first.castle", T("Keep your king safe", "شاهت را در امان نگه دار", "شاکەت بپارێزە"),
            T("A king left in the center is a target once the position opens. Castle early — usually kingside — to tuck the king away and connect your rooks.",
              "شاهی که در مرکز بماند، وقتی وضعیت باز شود هدف حمله است. زود قلعه برو — معمولاً قلعه‌ی کوتاه — تا شاه در امان باشد و رخ‌ها به هم وصل شوند.",
              "شایەک کە لە ناوەند بمێنێتەوە، کاتێک دۆخەکە دەکرێتەوە دەبێتە ئامانج. زوو قەڵابەندی بکە — زۆرجار کورت — تا شاکە بپارێزرێت و قەڵاکان پێکەوە ببەسترێن."),
            1, [best(f, T("Find the best move — think about king safety.", "بهترین حرکت را پیدا کن — به امنیت شاه فکر کن.", "باشترین جوڵە بدۆزەرەوە — بیر لە ئاسایشی شا بکەرەوە."), margin=35) for f in OPENING_POSITIONS["castle"]]),
        lesson("basics.first.blunders", T("Don't give pieces away", "مهره مفت نده", "مۆرە بە خۆڕایی مەدە"),
            T("Most games between beginners are decided by pieces left hanging. Before every move ask two questions: what does my opponent's last move threaten? After my move, is any of my pieces undefended?",
              "بیشتر بازی‌های مبتدیان با مهره‌هایی که بی‌دفاع رها شده‌اند تعیین می‌شود. قبل از هر حرکت دو سؤال بپرس: آخرین حرکت حریف چه تهدیدی دارد؟ بعد از حرکت من، آیا مهره‌ای بی‌دفاع می‌ماند؟",
              "زۆربەی یارییەکانی سەرەتاییەکان بەو مۆرانە یەکلا دەبنەوە کە بێ پارێزەر بەجێهێڵراون. پێش هەر جوڵەیەک دوو پرسیار بکە: دوایین جوڵەی ڕکابەر چ هەڕەشەیەکی هەیە؟ دوای جوڵەکەم، ئایا هیچ مۆرەیەکم بێ پارێزەر دەمێنێتەوە؟"),
            1, [goal(f, "save_piece", SAFE) for f in save_piece_positions(5)]),
    ])

def build():
    return course("basics", T("Chess Basics", "مبانی شطرنج", "بنەماکانی شەترەنج"),
        T("From the board and the pieces to checkmate, special moves and your first games.", "از صفحه و مهره‌ها تا مات، حرکت‌های ویژه و اولین بازی‌هایت.", "لە تەختە و مۆرەکانەوە تا کش‌مات، جوڵە تایبەتەکان و یەکەم یارییەکانت."),
        [board_chapter(), piece_lessons(), capture_chapter(), check_chapter(), mate_chapter(), special_chapter(), draws_chapter(), notation_chapter(), first_games_chapter()])
