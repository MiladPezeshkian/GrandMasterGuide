"""Course 2: Checkmate patterns - basic mates against Zorix, classic mating patterns, mate-in-N practice."""
import chess
import cb
from cb import *

MATE1 = T("Checkmate in one move!", "در یک حرکت مات کن!", "لە یەک جوڵەدا کش‌مات بکە!")
MATE2 = T("Checkmate in two moves!", "در دو حرکت مات کن!", "لە دوو جوڵەدا کش‌مات بکە!")
def MATEN(n):
    fa = "۰۱۲۳۴۵۶۷۸۹"[n]; ck = "٠١٢٣٤٥٦٧٨٩"[n]
    return T(f"Checkmate in {n} moves!", f"در {fa} حرکت مات کن!", f"لە {ck} جوڵەدا کش‌مات بکە!")

def play_mate(fen, moves, prompt):
    return play(fen, "win", moves, prompt)

WIN_Q = T("Checkmate the king with your queen — Zorix defends.", "با وزیرت شاه را مات کن — Zorix دفاع می‌کند.", "بە وەزیرەکەت شاکە کش‌مات بکە — Zorix بەرگری دەکات.")
WIN_R = T("Checkmate with the rook — Zorix defends.", "با رخ مات کن — Zorix دفاع می‌کند.", "بە قەڵا کش‌مات بکە — Zorix بەرگری دەکات.")
WIN_RR = T("Checkmate with the two rooks.", "با دو رخ مات کن.", "بە دوو قەڵا کش‌مات بکە.")
PROMO = T("Promote the pawn safely — Zorix defends.", "سرباز را با خیال راحت ارتقا بده — Zorix دفاع می‌کند.", "سەربازەکە بە سەلامەتی بەرز بکەرەوە — Zorix بەرگری دەکات.")

def basic_chapter():
    return chapter("basic-mates", T("Basic checkmates", "مات‌های پایه", "کش‌ماتە بنەڕەتییەکان"), [
        lesson("mates.basic.queen-box", T("King and queen: the box", "شاه و وزیر: جعبه", "شا و وەزیر: سندووق"),
            T("With king and queen against a lone king you can always win. Use the queen to build a 'box' around the enemy king — a knight's move away — and shrink it step by step. When the king is on the edge, bring your own king closer and deliver mate. Never leave the enemy king without a legal move unless it is mate: that is stalemate!",
              "با شاه و وزیر در برابر شاه تنها همیشه می‌توانی ببری. با وزیر دور شاه حریف یک «جعبه» بساز — به فاصله‌ی یک حرکت اسب — و آن را قدم‌به‌قدم کوچک کن. وقتی شاه به لبه رسید، شاه خودت را نزدیک بیاور و مات کن. هرگز شاه حریف را بدون حرکت مجاز رها نکن مگر اینکه مات باشد: این پات است!",
              "بە شا و وەزیر بەرامبەر شایەکی تەنها هەمیشە دەتوانیت بیبەیتەوە. بە وەزیر «سندووقێک» بە دەوری شای ڕکابەردا دروست بکە — بە دووری جوڵەیەکی ئەسپ — و هەنگاو بە هەنگاو بچووکی بکەرەوە. کاتێک شاکە گەیشتە لێوار، شاکەی خۆت نزیک بخەرەوە و کش‌مات بکە. هەرگیز شای ڕکابەر بەبێ جوڵەی ڕێگەپێدراو مەهێڵە مەگەر کش‌مات بێت: ئەوە پاتە!"),
            1, [theory(T("The queen on c6 keeps the black king inside the box a7–b8… Shrink the box, never give stalemate.", "وزیر c6 شاه سیاه را در جعبه نگه می‌دارد… جعبه را کوچک کن و هرگز پات نکن.", "وەزیری c6 شای ڕەش لە ناو سندووقەکەدا دەهێڵێتەوە… سندووقەکە بچووک بکەرەوە و هەرگیز پات مەکە."),
                       fen="1k6/8/2Q5/8/8/8/8/4K3 w - - 0 1", marks=["a8", "b8", "a7", "b7"]),
                play_mate("8/8/3k4/8/8/8/8/2Q1K3 w - - 0 1", 20, WIN_Q),
                play_mate("8/8/8/8/4k3/8/8/Q3K3 w - - 0 1", 20, WIN_Q),
                play_mate("k7/8/8/8/8/8/8/4K1Q1 w - - 0 1", 15, WIN_Q)]),
        lesson("mates.basic.queen-practice", T("Queen mate practice", "تمرین مات با وزیر", "ڕاهێنانی کش‌مات بە وەزیر"),
            T("Now mate faster: fewer than 12 moves. Plan: box, shrink, king up, mate.", "حالا سریع‌تر مات کن: کمتر از ۱۲ حرکت. نقشه: جعبه، کوچک کردن، آوردن شاه، مات.", "ئێستا خێراتر کش‌مات بکە: کەمتر لە ١٢ جوڵە. پلان: سندووق، بچووککردنەوە، هێنانی شا، کش‌مات."),
            2, [play_mate("8/8/8/4k3/8/8/8/1Q2K3 w - - 0 1", 12, WIN_Q),
                play_mate("8/2k5/8/8/8/8/8/4K2Q w - - 0 1", 12, WIN_Q),
                play_mate("8/8/8/8/2k5/8/8/4K1Q1 w - - 0 1", 12, WIN_Q)]),
        lesson("mates.basic.rook", T("King and rook", "شاه و رخ", "شا و قەڵا"),
            T("The rook mate needs teamwork. Use the rook to cut the enemy king off along a rank or file, bring your king in front of the enemy king ('opposition'), and then check along the edge. When the kings don't face each other, make a waiting rook move.",
              "مات با رخ به همکاری نیاز دارد. با رخ شاه حریف را روی یک ردیف یا ستون محدود کن، شاه خودت را روبه‌روی شاه حریف بیاور («اپوزیسیون») و بعد در امتداد لبه کیش بده. وقتی شاه‌ها روبه‌روی هم نیستند، با رخ یک حرکت صبر بکن.",
              "کش‌مات بە قەڵا هاوکاری دەوێت. بە قەڵا شای ڕکابەر لە ڕیز یان ستوونێکدا ببڕە، شاکەی خۆت بهێنە بەرامبەر شای ڕکابەر («ئۆپۆزیسیۆن») و پاشان بە درێژایی لێوارەکە کش بدە. کاتێک شاکان ڕووبەڕووی یەک نین، جوڵەیەکی چاوەڕوانی بە قەڵا بکە."),
            2, [theory(T("White's king faces the black king; now Ra8 is mate.", "شاه سفید روبه‌روی شاه سیاه است؛ حالا Ra8 مات است.", "شای سپی بەرامبەر شای ڕەشە؛ ئێستا Ra8 کش‌ماتە."), fen="4k3/8/4K3/8/8/8/8/R7 w - - 0 1", arrows=["a1a8"]),
                play_mate("8/8/8/3k4/8/8/8/R3K3 w - - 0 1", 30, WIN_R),
                play_mate("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", 25, WIN_R)]),
        lesson("mates.basic.rook-practice", T("Rook mate practice", "تمرین مات با رخ", "ڕاهێنانی کش‌مات بە قەڵا"),
            T("Practise the rook mate from different positions. Cut off, approach, check, repeat.", "مات با رخ را از وضعیت‌های مختلف تمرین کن. محدود کن، نزدیک شو، کیش بده، تکرار کن.", "کش‌مات بە قەڵا لە دۆخە جیاوازەکانەوە ڕابهێنە. ببڕە، نزیک ببەرەوە، کش بدە، دووبارە بکەرەوە."),
            3, [play_mate("8/8/8/8/4k3/8/8/4K2R w - - 0 1", 30, WIN_R),
                play_mate("8/8/2k5/8/8/8/8/6KR w - - 0 1", 30, WIN_R),
                play_mate("8/5k2/8/8/8/8/8/R5K1 w - - 0 1", 30, WIN_R)]),
        lesson("mates.basic.two-rooks", T("The ladder mate", "مات نردبانی", "کش‌ماتی پەیژە"),
            T("Two heavy pieces mate without the king's help: they take turns checking and guarding the next rank, walking the king to the edge like climbing a ladder. Keep your rooks far from the enemy king so it cannot attack them.",
              "دو مهره‌ی سنگین بدون کمک شاه مات می‌کنند: به نوبت کیش می‌دهند و ردیف بعدی را پوشش می‌دهند و شاه را مثل بالا رفتن از نردبان به لبه می‌برند. رخ‌ها را از شاه حریف دور نگه دار تا نتواند به آن‌ها حمله کند.",
              "دوو مۆرەی قورس بەبێ یارمەتی شا کش‌مات دەکەن: بە نۆرە کش دەدەن و ڕیزی دواتر دەپارێزن و شاکە وەک سەرکەوتن بە پەیژەدا بەرەو لێوار دەبەن. قەڵاکان لە شای ڕکابەر دوور بهێڵەوە تا نەتوانێت هێرشیان بکاتە سەر."),
            1, [play_mate("8/8/8/4k3/8/8/8/RR4K1 w - - 0 1", 12, WIN_RR),
                play_mate("8/8/3k4/8/8/8/8/1RR3K1 w - - 0 1", 12, WIN_RR),
                play_mate("8/8/8/2k5/8/8/8/Q5KR w - - 0 1", 10, T("Checkmate with queen and rook.", "با وزیر و رخ مات کن.", "بە وەزیر و قەڵا کش‌مات بکە."))]),
        lesson("mates.basic.two-bishops", T("Two bishops", "دو فیل", "دوو فیل"),
            T("Two bishops side by side form a wall the king cannot cross. Drive the king into a corner with bishops and king working together. This mate takes patience — up to about 20 moves.",
              "دو فیل کنار هم دیواری می‌سازند که شاه نمی‌تواند از آن عبور کند. با همکاری فیل‌ها و شاه، شاه حریف را به گوشه ببر. این مات صبر می‌خواهد — تا حدود ۲۰ حرکت.",
              "دوو فیل لە تەنیشت یەکەوە دیوارێک دروست دەکەن کە شا ناتوانێت لێی تێپەڕێت. بە هاوکاری فیلەکان و شا، شای ڕکابەر بەرەو گۆشەیەک ببە. ئەم کش‌ماتە ئارامی دەوێت — تا نزیکەی ٢٠ جوڵە."),
            4, [play_mate("8/8/8/4k3/8/8/8/2BBK3 w - - 0 1", 40, T("Checkmate with the two bishops.", "با دو فیل مات کن.", "بە دوو فیل کش‌مات بکە."))]),
        lesson("mates.basic.pawn", T("King and pawn: make a queen", "شاه و سرباز: وزیر بساز", "شا و سەرباز: وەزیر دروست بکە"),
            T("With king and pawn against king, lead with your king: place it in front of the pawn and take the 'opposition' (kings facing each other with one square between, the other side to move). Then the pawn walks through.",
              "با شاه و سرباز در برابر شاه، شاهت را جلو بفرست: آن را جلوی سرباز بگذار و «اپوزیسیون» را بگیر (شاه‌ها با یک خانه فاصله روبه‌روی هم، نوبت حریف). بعد سرباز راهش را باز می‌کند.",
              "بە شا و سەرباز بەرامبەر شا، شاکەت بخە پێشەوە: لە بەردەم سەربازەکەدا دایبنێ و «ئۆپۆزیسیۆن» بگرە (شاکان بە یەک خانە دووری ڕووبەڕووی یەکن و نۆرەی ڕکابەرە). پاشان سەربازەکە ڕێگای خۆی دەکاتەوە."),
            2, [play("4k3/8/4K3/4P3/8/8/8/8 w - - 0 1", "promote", 15, PROMO),
                play("8/4k3/8/3K4/8/3P4/8/8 w - - 0 1", "promote", 20, PROMO),
                play("8/2k5/8/1K6/1P6/8/8/8 w - - 0 1", "promote", 20, PROMO)]),
    ])

PATTERNS = [
    # key, title, intro, n1 count, n2 count, level
    ("backRank", T("Back-rank mate", "مات ردیف آخر", "کش‌ماتی ڕیزی دواوە"),
     T("The king is trapped behind its own pawns on the back rank and a rook or queen mates along it. Defend it by giving your king 'luft' (a pawn move like h3) or by keeping a rook on the back rank.",
       "شاه پشت سربازهای خودش در ردیف آخر گیر افتاده و رخ یا وزیر در امتداد آن مات می‌کند. برای دفاع، به شاهت «جای نفس» بده (حرکت سربازی مثل h3) یا یک رخ در ردیف آخر نگه دار.",
       "شاکە لە پشت سەربازەکانی خۆی لە ڕیزی دواوە گیری خواردووە و قەڵا یان وەزیر بە درێژاییەکەی کش‌مات دەکات. بۆ بەرگری، «جێی هەناسە» بدە بە شاکەت (جوڵەی سەربازێک وەک h3) یان قەڵایەک لە ڕیزی دواوە بهێڵەوە."), 6, 5, 2),
    ("smothered", T("Smothered mate", "مات خفه", "کش‌ماتی خنکاو"),
     T("The king is surrounded by its own pieces and a knight delivers mate — the king is 'smothered'. The famous version starts with a queen sacrifice that forces a rook next to the king.",
       "شاه در محاصره‌ی مهره‌های خودش است و اسب مات می‌کند — شاه «خفه» می‌شود. نسخه‌ی معروفش با قربانی وزیر شروع می‌شود که یک رخ را مجبور می‌کند کنار شاه بیاید.",
       "شاکە لە ناو مۆرەکانی خۆیدا گیراوە و ئەسپ کش‌مات دەکات — شاکە «دەخنکێت». وەشانە بەناوبانگەکەی بە قوربانیکردنی وەزیر دەست پێدەکات کە قەڵایەک ناچار دەکات بێتە تەنیشت شاکە."), 5, 4, 3),
    ("anastasia", T("Anastasia's mate", "مات آناستازیا", "کش‌ماتی ئاناستاسیا"),
     T("A knight controls the escape squares of a king on the edge, the king's own pawn blocks it, and a rook or queen mates along the edge file.",
       "اسب خانه‌های فرار شاهی را که در لبه است کنترل می‌کند، سرباز خود شاه راهش را بسته و رخ یا وزیر در امتداد ستون لبه مات می‌کند.",
       "ئەسپ خانەکانی ڕاکردنی شایەک کە لە لێوارە کۆنترۆڵ دەکات، سەربازی خۆی ڕێگای گرتووە و قەڵا یان وەزیر بە درێژایی ستوونی لێوار کش‌مات دەکات."), 4, 3, 3),
    ("arabian", T("Arabian mate", "مات عربی", "کش‌ماتی عەرەبی"),
     T("One of the oldest known mates: a knight and a rook trap a king in the corner. The knight protects the rook and covers the last escape square.",
       "یکی از قدیمی‌ترین مات‌های شناخته‌شده: اسب و رخ شاه را در گوشه به دام می‌اندازند. اسب از رخ دفاع می‌کند و آخرین خانه‌ی فرار را می‌پوشاند.",
       "یەکێک لە کۆنترین کش‌ماتە ناسراوەکان: ئەسپ و قەڵا شا لە گۆشەدا دەخەنە تەڵەوە. ئەسپ بەرگری لە قەڵا دەکات و دوایین خانەی ڕاکردن دادەپۆشێت."), 4, 3, 2),
    ("boden", T("Mates with two bishops", "مات با دو فیل", "کش‌مات بە دوو فیل"),
     T("Two bishops on crossing diagonals can mate a king that is hemmed in by its own pieces — like Boden's mate, where a castled king falls to criss-crossing bishops.",
       "دو فیل روی قطرهای متقاطع می‌توانند شاهی را که مهره‌های خودش دورش را گرفته‌اند مات کنند — مثل مات بودن، که در آن شاهِ قلعه‌رفته با فیل‌های متقاطع مات می‌شود.",
       "دوو فیل لەسەر لاکێشە یەکتربڕەکان دەتوانن شایەک کش‌مات بکەن کە مۆرەکانی خۆی دەوریان گرتووە — وەک کش‌ماتی بۆدن، کە شای قەڵابەندکراو بە فیلە یەکتربڕەکان کش‌مات دەبێت."), 4, 3, 3),
    ("dovetail", T("Dovetail mate", "مات دم‌چلچله‌ای", "کش‌ماتی کلکی پەڕەسێلکە"),
     T("The queen mates from a diagonal next to the king, protected by another piece, while the two squares behind the king are blocked by its own pieces.",
       "وزیر از خانه‌ای قطری کنار شاه، با پشتیبانی مهره‌ای دیگر، مات می‌کند در حالی که دو خانه‌ی پشت شاه با مهره‌های خودش بسته شده‌اند.",
       "وەزیر لە خانەیەکی لاکێشی تەنیشت شاوە، بە پشتگیریی مۆرەیەکی تر، کش‌مات دەکات لە کاتێکدا دوو خانەی پشت شا بە مۆرەکانی خۆی گیراون."), 4, 3, 3),
    ("opera", T("Opera mate", "مات اپرا", "کش‌ماتی ئۆپێرا"),
     T("A rook mates next to the king on the back rank, protected by a bishop from far away. Named after Morphy's famous 'Opera game' of 1858.",
       "رخ در ردیف آخر کنار شاه مات می‌کند و فیلی از دور از آن دفاع می‌کند. نامش از «بازی اپرا»ی معروف مورفی در سال ۱۸۵۸ گرفته شده است.",
       "قەڵا لە ڕیزی دواوە لە تەنیشت شاوە کش‌مات دەکات و فیلێک لە دوورەوە بەرگری لێ دەکات. ناوەکەی لە «یاریی ئۆپێرا»ی بەناوبانگی مۆرفی لە ساڵی ١٨٥٨ وەرگیراوە."), 4, 3, 3),
    ("queenBishop", T("Queen and bishop battery", "باتری وزیر و فیل", "باتریی وەزیر و فیل"),
     T("A queen supported by a bishop mates right next to the king — the classic h7 (or h2) mate against a castled king. Line the two pieces up on the same diagonal and strike.",
       "وزیر با پشتیبانی فیل درست کنار شاه مات می‌کند — مات کلاسیک h7 (یا h2) در برابر شاهِ قلعه‌رفته. دو مهره را روی یک قطر ردیف کن و ضربه بزن.",
       "وەزیر بە پشتگیریی فیل ڕێک لە تەنیشت شاوە کش‌مات دەکات — کش‌ماتی کلاسیکی h7 (یان h2) دژی شای قەڵابەندکراو. دوو مۆرەکە لەسەر هەمان لاکێش ڕیز بکە و لێبدە."), 5, 4, 2),
    ("queenKnight", T("Queen and knight", "وزیر و اسب", "وەزیر و ئەسپ"),
     T("The knight is the queen's best partner in an attack: it covers squares the queen cannot and protects her when she lands next to the king.",
       "اسب بهترین همراه وزیر در حمله است: خانه‌هایی را می‌پوشاند که وزیر نمی‌تواند و وقتی وزیر کنار شاه فرود می‌آید از او دفاع می‌کند.",
       "ئەسپ باشترین هاوبەشی وەزیرە لە هێرشدا: ئەو خانانە دادەپۆشێت کە وەزیر ناتوانێت و کاتێک وەزیر لە تەنیشت شاوە دادەنیشێت بەرگری لێ دەکات."), 5, 4, 2),
    ("queenPawn", T("Queen and pawn", "وزیر و سرباز", "وەزیر و سەرباز"),
     T("An advanced pawn (on f6 or h6, for example) is a great anchor for the queen: supported by the pawn, the queen mates on g7 or h7. Known as Lolli's and Damiano's mates.",
       "سرباز پیشرفته (مثلاً روی f6 یا h6) تکیه‌گاه عالی برای وزیر است: وزیر با پشتیبانی سرباز روی g7 یا h7 مات می‌کند. این‌ها به مات لولی و مات دامیانو معروف‌اند.",
       "سەربازێکی پێشکەوتوو (بۆ نموونە لەسەر f6 یان h6) پاڵپشتێکی نایابە بۆ وەزیر: وەزیر بە پشتگیریی سەرباز لەسەر g7 یان h7 کش‌مات دەکات. بە کش‌ماتی لۆلی و دامیانۆ ناسراون."), 4, 3, 2),
    ("corridor", T("Corridor mate", "مات راهرو", "کش‌ماتی ڕاڕەو"),
     T("Like the back-rank mate but along a file: the king on the edge is blocked by its own pieces beside it, and a rook or queen mates down the corridor.",
       "مثل مات ردیف آخر اما در امتداد ستون: شاهِ لبه با مهره‌های خودش در کنارش محاصره شده و رخ یا وزیر در طول راهرو مات می‌کند.",
       "وەک کش‌ماتی ڕیزی دواوە بەڵام بە درێژایی ستوون: شای لێوار لە تەنیشتییەوە بە مۆرەکانی خۆی گیراوە و قەڵا یان وەزیر بە درێژایی ڕاڕەوەکە کش‌مات دەکات."), 4, 2, 2),
    ("ladder", T("Rook ladders in the middlegame", "نردبان رخ در وسط بازی", "پەیژەی قەڵا لە ناوەڕاستی یاری"),
     T("Two heavy pieces working on neighbouring ranks mate a king on the edge — the ladder mate appears in real games too.",
       "دو مهره‌ی سنگین که روی ردیف‌های مجاور کار می‌کنند شاه لبه را مات می‌کنند — مات نردبانی در بازی‌های واقعی هم پیش می‌آید.",
       "دوو مۆرەی قورس کە لەسەر ڕیزە دراوسێکان کار دەکەن شای لێوار کش‌مات دەکەن — کش‌ماتی پەیژە لە یارییە ڕاستەقینەکانیشدا ڕوودەدات."), 4, 3, 2),
    ("pawnMate", T("Mate with a pawn", "مات با سرباز", "کش‌مات بە سەرباز"),
     T("Even the humble pawn can give the final blow when the king has no squares left.", "حتی سرباز ساده هم می‌تواند ضربه‌ی آخر را بزند وقتی شاه دیگر خانه‌ای ندارد.", "تەنانەت سەربازی سادەش دەتوانێت دوایین زەربە لێبدات کاتێک شا هیچ خانەیەکی نەماوە."), 3, 2, 2),
    ("doubleCheckMate", T("Double-check mates", "مات با کیش دوبل", "کش‌مات بە کشی دووانە"),
     T("A double check can only be answered by moving the king. If the king has no square, it is mate — even if both checking pieces could be captured!",
       "به کیش دوبل فقط با حرکت شاه می‌شود جواب داد. اگر شاه خانه‌ای نداشته باشد، مات است — حتی اگر هر دو مهره‌ی کیش‌دهنده قابل زدن باشند!",
       "کشی دووانە تەنها بە جوڵاندنی شا وەڵام دەدرێتەوە. ئەگەر شا هیچ خانەیەکی نەبێت، کش‌ماتە — تەنانەت ئەگەر هەردوو مۆرە کشدەرەکە بگیرێن!"), 4, 3, 3),
]

def pattern_chapter():
    M = cb.DATA["mates"]
    lessons = []
    for key, title, intro, n1, n2, level in PATTERNS:
        one = [mate(e["fen"], 1, MATE1) for e in M.get(key, [])[:n1]]
        two = [mate(e["fen"], 2, MATE2) for e in M.get(key + "2", [])[:n2]]
        if not one:
            raise ContentError(f"no positions for pattern {key}")
        final = chess.Board(M[key][0]["fen"]); final.push(chess.Move.from_uci(M[key][0]["moves"][0]))
        steps = [theory(T("The final position of this pattern.", "وضعیت پایانی این الگو.", "دۆخی کۆتایی ئەم شێوازە."), fen=final.fen())] + one
        lessons.append(lesson(f"mates.pattern.{key}", title, intro, level, steps))
        if two:
            lessons.append(lesson(f"mates.pattern.{key}2", T(title["en"] + " in two", title["fa"] + " در دو حرکت", title["ckb"] + " لە دوو جوڵەدا"),
                T("Now the same pattern needs a preparing move first — often a check or a sacrifice.", "حالا همین الگو اول به یک حرکت آماده‌سازی نیاز دارد — اغلب یک کیش یا یک قربانی.", "ئێستا هەمان شێواز پێویستی بە جوڵەیەکی ئامادەکاری هەیە — زۆرجار کشێک یان قوربانییەک."),
                level + 1, two))
    return chapter("patterns", T("Mating patterns", "الگوهای مات", "شێوازەکانی کش‌مات"), lessons)

def review_chapter():
    """More positions for each pattern, then mixed sets where the pattern is not named: recognising it is the skill."""
    M = cb.DATA["mates"]
    lessons, rest = [], []
    for key, title, intro, n1, n2, level in PATTERNS:
        extra = M.get(key, [])[n1:]
        if len(extra) >= 8:
            lessons.append(lesson(f"mates.review.{key}", T(title["en"] + " — practice", title["fa"] + " — تمرین", title["ckb"] + " — ڕاهێنان"),
                T("More positions with the same pattern. Picture the final position first, then find the move.",
                  "وضعیت‌های بیشتر با همین الگو. اول وضعیت پایانی را در ذهنت تصور کن، بعد حرکت را پیدا کن.",
                  "دۆخی زیاتر بە هەمان شێواز. سەرەتا دۆخی کۆتایی لە مێشکتدا وێنا بکە، پاشان جوڵەکە بدۆزەرەوە."),
                level, [mate(e["fen"], 1, MATE1) for e in extra[:6]]))
            rest += extra[6:]
    rng = __import__("random").Random(11)
    rng.shuffle(rest)
    per = 8
    fa_num = "۰۱۲۳۴۵۶۷۸۹"; ck_num = "٠١٢٣٤٥٦٧٨٩"
    for i in range(min(10, len(rest) // per)):
        chosen = rest[i * per:(i + 1) * per]
        lessons.append(lesson(f"mates.mixed.{i + 1}",
            T(f"Which pattern? Set {i + 1}", f"کدام الگو؟ سری {fa_num[(i + 1) % 10] if i + 1 < 10 else '۱۰'}", f"کام شێواز؟ کۆمەڵەی {ck_num[(i + 1) % 10] if i + 1 < 10 else '١٠'}"),
            T("Mixed mating patterns without their names — recognise the pattern and deliver mate.",
              "الگوهای مختلف مات بدون نامشان — الگو را تشخیص بده و مات کن.",
              "شێوازە جیاوازەکانی کش‌مات بەبێ ناویان — شێوازەکە بناسەرەوە و کش‌مات بکە."),
            min(5, 2 + i // 3), [mate(e["fen"], 1, MATE1) for e in chosen]))
    return chapter("pattern-review", T("Pattern practice", "تمرین الگوها", "ڕاهێنانی شێوازەکان"), lessons)

def practice_chapter():
    """Mate-in-N ladders from mined games (fixed solutions) and generated mates."""
    P = cb.DATA["puzzles"]
    def mates(n, lo, hi, k):
        pool = [p for p in P if f"mateIn{n}" in p["themes"] and lo <= p["rating"] < hi and p["id"] not in cb.USED]
        pool.sort(key=lambda p: p["rating"])
        chosen = pool[:k]
        for p in chosen:
            cb.USED.add(p["id"])
        return chosen
    lessons = []
    bands = {1: [(0, 700), (700, 900), (900, 1100), (1100, 1400), (1400, 9999)],
             2: [(0, 900), (900, 1100), (1100, 1300), (1300, 1500), (1500, 1800), (1800, 9999)],
             3: [(0, 1300), (1300, 1600), (1600, 1900), (1900, 9999)],
             4: [(0, 1700), (1700, 9999)]}
    counts = {1: 8, 2: 6, 3: 5, 4: 4}
    fa_num = "۰۱۲۳۴۵۶۷۸۹"; ck_num = "٠١٢٣٤٥٦٧٨٩"
    for n, bl in bands.items():
        for i, (lo, hi) in enumerate(bl):
            chosen = mates(n, lo, hi, counts[n])
            if len(chosen) < 3:
                continue
            lessons.append(lesson(f"mates.practice.m{n}.{i + 1}",
                T(f"Mate in {n} — level {i + 1}", f"مات در {fa_num[n]} — سطح {fa_num[i + 1]}", f"کش‌مات لە {ck_num[n]} — ئاست {ck_num[i + 1]}"),
                T("Real positions from games. Look at every check first — and remember the opponent will defend in the best way.",
                  "وضعیت‌های واقعی از بازی‌ها. اول همه‌ی کیش‌ها را بررسی کن — و یادت باشد حریف به بهترین شکل دفاع می‌کند.",
                  "دۆخی ڕاستەقینە لە یارییەکانەوە. سەرەتا هەموو کشەکان بپشکنە — و لەبیرت بێت ڕکابەر بە باشترین شێوە بەرگری دەکات."),
                min(5, 1 + n + i // 2), [puzzle(p, MATEN(n)) for p in chosen]))
    return chapter("mate-practice", T("Mate in N", "مات در چند حرکت", "کش‌مات لە چەند جوڵەدا"), lessons)

def build():
    return course("mates", T("Checkmate Patterns", "الگوهای مات", "شێوازەکانی کش‌مات"),
        T("Basic mates against Zorix, the classic mating patterns every player must know, and mate-in-N training.",
          "مات‌های پایه در برابر Zorix، الگوهای کلاسیک مات که هر شطرنج‌بازی باید بداند و تمرین مات در چند حرکت.",
          "کش‌ماتە بنەڕەتییەکان دژی Zorix، شێوازە کلاسیکەکانی کش‌مات کە هەموو یاریزانێک دەبێت بیانزانێت و ڕاهێنانی کش‌مات لە چەند جوڵەدا."),
        [basic_chapter(), pattern_chapter(), review_chapter(), practice_chapter()])
