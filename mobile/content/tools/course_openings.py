"""Course 5: Openings - principles, classic traps and a repertoire of main openings with their ideas."""
import chess, chess.engine
import cb
from cb import *

PLAY_W = T("Play White's moves of this opening.", "حرکت‌های سفید این گشایش را بازی کن.", "جوڵەکانی سپی لەم دەستپێکەدا یاری بکە.")
PLAY_B = T("Play Black's moves of this opening.", "حرکت‌های سیاه این گشایش را بازی کن.", "جوڵەکانی ڕەش لەم دەستپێکەدا یاری بکە.")
GOOD = T("Find a good move in this typical position.", "در این وضعیت نمونه یک حرکت خوب پیدا کن.", "لەم دۆخە نموونەییەدا جوڵەیەکی باش بدۆزەرەوە.")
TRAP = T("Spring the trap!", "تله را فعال کن!", "تەڵەکە کارا بکە!")

def pos_after(san):
    b = chess.Board()
    for s in san.split():
        b.push_san(s)
    return b.fen()

# id, title, side, line (SAN), ideas
REPERTOIRE = [
    ("italian", T("Italian Game", "گشایش ایتالیایی", "دەستپێکی ئیتاڵی"), "white",
     "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O O-O",
     T("One of the oldest openings. White develops fast, aims the bishop at f7 (Black's weakest point) and prepares d4 with c3. In the modern 'Giuoco Pianissimo' White plays d3 and builds up slowly, often with Re1, Nbd2–f1–g3 and a kingside attack.",
       "یکی از قدیمی‌ترین گشایش‌ها. سفید سریع مهره‌هایش را وارد می‌کند، فیل را به سمت f7 (ضعیف‌ترین نقطه‌ی سیاه) نشانه می‌گیرد و با c3 حرکت d4 را آماده می‌کند. در «جوکو پیانیسیموی» مدرن، سفید d3 بازی می‌کند و آرام آرایش می‌گیرد، اغلب با Re1 و مانور اسب Nbd2–f1–g3 و حمله در جناح شاه.",
       "یەکێک لە کۆنترین دەستپێکەکان. سپی خێرا مۆرەکانی دەهێنێتە ناو یاری، فیلەکە ئاراستەی f7 دەکات (لاوازترین خاڵی ڕەش) و بە c3 ئامادەکاری بۆ d4 دەکات. لە «جۆکۆ پیانیسیمۆ»ی نوێدا سپی d3 یاری دەکات و هێواش ڕێکدەخات، زۆرجار بە Re1 و مانۆڕی ئەسپ Nbd2–f1–g3 و هێرش لە لای شا.")),
    ("two-knights", T("Two Knights Defense", "دفاع دو اسب", "بەرگریی دوو ئەسپ"), "black",
     "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Be7 O-O O-O Re1 d6",
     T("Black counter-attacks e4 with 3...Nf6 instead of copying White. Against the sharp 4.Ng5 Black must know the lines (4...d5 5.exd5 Na5); against the quiet 4.d3 Black develops calmly with ...Be7, ...O-O and ...d6.",
       "سیاه به‌جای تقلید از سفید با 3...Nf6 به e4 ضدحمله می‌کند. در برابر 4.Ng5 تند، سیاه باید خط‌ها را بداند (4...d5 5.exd5 Na5)؛ در برابر 4.d3 آرام، سیاه با ...Be7، ...O-O و ...d6 با آرامش گسترش می‌یابد.",
       "ڕەش لە جیاتی لاساییکردنەوەی سپی بە 3...Nf6 دژەهێرش دەکاتە سەر e4. دژی 4.Ng5ی تیژ ڕەش دەبێت هێڵەکان بزانێت (4...d5 5.exd5 Na5)؛ دژی 4.d3ی ئارام، ڕەش بە ...Be7، ...O-O و ...d6 بە ئارامی مۆرەکانی دەهێنێتە ناو یاری.")),
    ("ruy-lopez", T("Ruy Lopez", "روی لوپز", "ڕوی لۆپێز"), "white",
     "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3",
     T("The 'Spanish torture': White pressures the knight that defends e5, castles, and slowly builds a big center with c3 and d4. The bishop retreats to b3 and later c2, aiming at the black king. The Ruy Lopez has been the main battleground of world champions for 150 years.",
       "«شکنجه‌ی اسپانیایی»: سفید به اسبی که از e5 دفاع می‌کند فشار می‌آورد، قلعه می‌رود و با c3 و d4 آرام‌آرام مرکز بزرگی می‌سازد. فیل به b3 و بعد c2 عقب می‌رود و شاه سیاه را نشانه می‌گیرد. روی لوپز ۱۵۰ سال است میدان اصلی نبرد قهرمانان جهان است.",
       "«ئازاری ئیسپانی»: سپی فشار دەخاتە سەر ئەو ئەسپەی کە بەرگری لە e5 دەکات، قەڵابەندی دەکات و بە c3 و d4 هێواش هێواش ناوەندێکی گەورە دروست دەکات. فیلەکە دەگەڕێتەوە b3 و دواتر c2 و شای ڕەش دەکاتە ئامانج. ڕوی لۆپێز ١٥٠ ساڵە مەیدانی سەرەکیی شەڕی پاڵەوانانی جیهانە.")),
    ("scotch", T("Scotch Game", "گشایش اسکاتلندی", "یاریی سکۆتلەندی"), "white",
     "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bc4",
     T("White opens the center at once with 3.d4. After the exchange on d4 White gets free, active piece play and a strong e4-pawn. A favourite of Kasparov.",
       "سفید با 3.d4 فوراً مرکز را باز می‌کند. بعد از تعویض در d4، سفید بازی آزاد و فعال مهره‌ها و سرباز قوی e4 را به دست می‌آورد. یکی از گشایش‌های محبوب کاسپاروف.",
       "سپی بە 3.d4 یەکسەر ناوەند دەکاتەوە. دوای ئاڵوگۆڕ لە d4، سپی یاریی ئازاد و چالاکی مۆرەکان و سەربازێکی بەهێزی e4 بەدەست دەهێنێت. یەکێک لە دەستپێکە دڵخوازەکانی کاسپارۆڤ.")),
    ("kings-gambit", T("King's Gambit", "گامبی شاه", "گامبیتی شا"), "white",
     "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5",
     T("The romantic opening of the 19th century: White offers the f-pawn to open the f-file and build a big center with d4. It leads to wild attacking games — both kings can be in danger!",
       "گشایش رمانتیک قرن نوزدهم: سفید سرباز f را پیشکش می‌کند تا ستون f باز شود و با d4 مرکز بزرگی بسازد. به بازی‌های حمله‌ای وحشی می‌رسد — هر دو شاه ممکن است در خطر باشند!",
       "دەستپێکی ڕۆمانسیی سەدەی نۆزدەهەم: سپی سەربازی f پێشکەش دەکات بۆ کردنەوەی ستوونی f و دروستکردنی ناوەندێکی گەورە بە d4. دەگاتە یاریی هێرشی توند — هەردوو شا لەوانەیە لە مەترسیدا بن!")),
    ("vienna", T("Vienna Game", "گشایش وین", "یاریی ڤیەننا"), "white",
     "e4 e5 Nc3 Nf6 Bc4 Nc6 d3 Bc5 f4",
     T("White develops the queen's knight first and keeps the f-pawn free to advance to f4 — a King's Gambit with better preparation.",
       "سفید اول اسب وزیر را وارد می‌کند و سرباز f را آزاد نگه می‌دارد تا به f4 برود — یک گامبی شاه با آمادگی بهتر.",
       "سپی سەرەتا ئەسپی وەزیر دەهێنێتە ناو یاری و سەربازی f ئازاد دەهێڵێتەوە تا بچێتە f4 — گامبیتی شا بە ئامادەکاریی باشتر.")),
    ("petrov", T("Petrov's Defense", "دفاع پتروف", "بەرگریی پێترۆف"), "black",
     "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4 d5 Bd3 Nc6",
     T("A solid, symmetrical defense: Black counter-attacks e4 immediately. Careful: after 3.Nxe5 Black must play 3...d6 first — taking on e4 at once runs into Qe2!",
       "دفاعی محکم و قرینه: سیاه فوراً به e4 ضدحمله می‌کند. مراقب باش: بعد از 3.Nxe5 سیاه باید اول 3...d6 بازی کند — زدن فوری e4 به Qe2! برمی‌خورد.",
       "بەرگرییەکی پتەو و هاوتەریب: ڕەش یەکسەر دژەهێرش دەکاتە سەر e4. ئاگادار بە: دوای 3.Nxe5 ڕەش دەبێت سەرەتا 3...d6 یاری بکات — گرتنی یەکسەری e4 تووشی Qe2! دەبێت.")),
    ("sicilian-najdorf", T("Sicilian Najdorf", "سیسیلی نایدورف", "سیسیلی نایدۆرف"), "black",
     "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6",
     T("The Sicilian (1...c5) is the most popular answer to 1.e4: Black fights for d4 from the side and gets an unbalanced game. The Najdorf's 5...a6 keeps White's pieces off b5 and prepares ...e5 or ...b5 — the choice of Fischer and Kasparov.",
       "سیسیلی (1...c5) محبوب‌ترین پاسخ به 1.e4 است: سیاه از کنار برای d4 می‌جنگد و بازی نامتقارنی به دست می‌آورد. حرکت 5...a6 در نایدورف مهره‌های سفید را از b5 دور نگه می‌دارد و ...e5 یا ...b5 را آماده می‌کند — انتخاب فیشر و کاسپاروف.",
       "سیسیلی (1...c5) بەناوبانگترین وەڵامی 1.e4ە: ڕەش لە لاوە بۆ d4 تێدەکۆشێت و یارییەکی نایەکسان بەدەست دەهێنێت. جوڵەی 5...a6ی نایدۆرف مۆرەکانی سپی لە b5 دوور دەخاتەوە و ...e5 یان ...b5 ئامادە دەکات — هەڵبژاردەی فیشەر و کاسپارۆڤ.")),
    ("sicilian-dragon", T("Sicilian Dragon", "سیسیلی اژدها", "سیسیلی ئەژدیها"), "black",
     "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6",
     T("Black fianchettoes the bishop on g7, where it breathes fire down the long diagonal like a dragon. Games are races: White castles long and attacks the king with h4–h5, Black attacks on the queenside with ...Rc8 and ...Nc4.",
       "سیاه فیل را در g7 فیانکتو می‌کند، جایی که مثل اژدها روی قطر بلند آتش می‌ریزد. بازی‌ها مسابقه‌اند: سفید قلعه‌ی بلند می‌رود و با h4–h5 به شاه حمله می‌کند و سیاه با ...Rc8 و ...Nc4 در جناح وزیر حمله می‌کند.",
       "ڕەش فیلەکە لە g7 فیانکێتۆ دەکات، کە وەک ئەژدیها بە درێژایی لاکێشە درێژەکەدا ئاگر دەبارێنێت. یارییەکان پێشبڕکێن: سپی قەڵابەندی درێژ دەکات و بە h4–h5 هێرش دەکاتە سەر شا، ڕەش لە لای وەزیر بە ...Rc8 و ...Nc4 هێرش دەکات.")),
    ("alapin", T("Sicilian Alapin", "سیسیلی آلاپین", "سیسیلی ئالاپین"), "white",
     "e4 c5 c3 Nf6 e5 Nd5 d4 cxd4 Nf3 Nc6 cxd4 d6 Bc4",
     T("An easy-to-learn weapon against the Sicilian: 2.c3 prepares d4 so that White can recapture with a pawn and keep a classical center.",
       "سلاحی آسان در برابر سیسیلی: 2.c3 حرکت d4 را آماده می‌کند تا سفید بتواند با سرباز پس بگیرد و مرکز کلاسیک داشته باشد.",
       "چەکێکی ئاسان دژی سیسیلی: 2.c3 ئامادەکاری بۆ d4 دەکات بۆ ئەوەی سپی بتوانێت بە سەرباز بیگرێتەوە و ناوەندێکی کلاسیکی هەبێت.")),
    ("french", T("French Defense", "دفاع فرانسوی", "بەرگریی فەرەنسی"), "black",
     "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 Be2 cxd4 cxd4 Nh6",
     T("Solid and strategic: Black builds a pawn chain e6–d5 and attacks White's center from the side with ...c5 and ...f6. The weak point is the light-squared bishop on c8, which is often blocked by its own pawns.",
       "محکم و راهبردی: سیاه زنجیره‌ی سربازی e6–d5 می‌سازد و با ...c5 و ...f6 از کنار به مرکز سفید حمله می‌کند. نقطه‌ی ضعف، فیل خانه‌روشن c8 است که اغلب پشت سربازهای خودش گیر می‌کند.",
       "پتەو و ستراتیژی: ڕەش زنجیرە سەربازی e6–d5 دروست دەکات و بە ...c5 و ...f6 لە لاوە هێرش دەکاتە سەر ناوەندی سپی. خاڵی لاواز فیلی خانە-ڕووناکی c8ە کە زۆرجار لە پشت سەربازەکانی خۆیدا گیر دەخوات.")),
    ("caro-kann", T("Caro-Kann Defense", "دفاع کاروکان", "بەرگریی کارۆ-کان"), "black",
     "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7",
     T("Like the French, Black challenges e4 with ...d5 — but prepares it with ...c6 so the light-squared bishop can come out to f5 first. Very solid, with a healthy pawn structure; a favourite of Karpov and Carlsen.",
       "مثل فرانسوی، سیاه با ...d5 به e4 حمله می‌کند — اما با ...c6 آماده‌اش می‌کند تا فیل خانه‌روشن بتواند اول به f5 بیاید. بسیار محکم با ساختار سربازی سالم؛ محبوب کارپوف و کارلسن.",
       "وەک فەرەنسی، ڕەش بە ...d5 هێرش دەکاتە سەر e4 — بەڵام بە ...c6 ئامادەی دەکات بۆ ئەوەی فیلی خانە-ڕووناک سەرەتا بێتە f5. زۆر پتەوە و پێکهاتەی سەربازیی تەندروستی هەیە؛ دڵخوازی کارپۆڤ و کارلسن.")),
    ("scandinavian", T("Scandinavian Defense", "دفاع اسکاندیناوی", "بەرگریی سکاندیناڤی"), "black",
     "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6 Nf3 c6 Bc4 Bf5",
     T("Black attacks e4 on move one. The queen comes out early, but after ...Qa5 it stands safely and Black develops smoothly with ...Nf6, ...c6 and ...Bf5.",
       "سیاه در حرکت اول به e4 حمله می‌کند. وزیر زود بیرون می‌آید، اما بعد از ...Qa5 در امان است و سیاه با ...Nf6، ...c6 و ...Bf5 راحت گسترش پیدا می‌کند.",
       "ڕەش لە جوڵەی یەکەمدا هێرش دەکاتە سەر e4. وەزیر زوو دەردەچێت، بەڵام دوای ...Qa5 ئارامە و ڕەش بە ...Nf6، ...c6 و ...Bf5 بە ئاسانی گەشە دەکات.")),
    ("pirc", T("Pirc Defense", "دفاع پیرتس", "بەرگریی پیرتس"), "black",
     "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O c6",
     T("A 'hypermodern' defense: Black lets White build a big center and plans to attack it later with ...c5 or ...e5, using the fianchettoed bishop on g7.",
       "یک دفاع «هیپرمدرن»: سیاه اجازه می‌دهد سفید مرکز بزرگی بسازد و قصد دارد بعداً با ...c5 یا ...e5 و به کمک فیل فیانکتوشده‌ی g7 به آن حمله کند.",
       "بەرگرییەکی «هایپەرمۆدێرن»: ڕەش ڕێگە دەدات سپی ناوەندێکی گەورە دروست بکات و پلانی هەیە دواتر بە ...c5 یان ...e5 و بە یارمەتی فیلی g7 هێرشی بکاتە سەر.")),
    ("qgd", T("Queen's Gambit Declined", "گامبی وزیر پذیرفته‌نشده", "گامبیتی وەزیری ڕەتکراو"), "black",
     "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 b6",
     T("The classical answer to the Queen's Gambit: Black keeps the pawn on d5 with ...e6, develops solidly and castles. Black's light-squared bishop needs care — often ...b6 and ...Bb7 solve the problem.",
       "پاسخ کلاسیک به گامبی وزیر: سیاه سرباز را با ...e6 در d5 نگه می‌دارد، محکم گسترش می‌یابد و قلعه می‌رود. فیل خانه‌روشن سیاه باید مراقبت شود — اغلب ...b6 و ...Bb7 مشکل را حل می‌کنند.",
       "وەڵامی کلاسیکی گامبیتی وەزیر: ڕەش بە ...e6 سەربازەکە لە d5 دەهێڵێتەوە، بە پتەوی گەشە دەکات و قەڵابەندی دەکات. فیلی خانە-ڕووناکی ڕەش پێویستی بە ئاگاداری هەیە — زۆرجار ...b6 و ...Bb7 کێشەکە چارەسەر دەکەن.")),
    ("queens-gambit", T("Queen's Gambit", "گامبی وزیر", "گامبیتی وەزیر"), "white",
     "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Rc1 c6 Bd3",
     T("White offers the c-pawn to deflect Black's d-pawn from the center. It is not a real sacrifice: if Black takes, White usually regains the pawn. White gets a lasting initiative and central control.",
       "سفید سرباز c را پیشکش می‌کند تا سرباز d سیاه را از مرکز منحرف کند. این قربانی واقعی نیست: اگر سیاه بگیرد، سفید معمولاً سرباز را پس می‌گیرد. سفید ابتکار عمل پایدار و کنترل مرکز را به دست می‌آورد.",
       "سپی سەربازی c پێشکەش دەکات بۆ لادانی سەربازی dی ڕەش لە ناوەند. ئەمە قوربانییەکی ڕاستەقینە نییە: ئەگەر ڕەش بیگرێت، سپی زۆرجار سەربازەکە دەگرێتەوە. سپی دەستپێشخەرییەکی بەردەوام و کۆنترۆڵی ناوەند بەدەست دەهێنێت.")),
    ("slav", T("Slav Defense", "دفاع اسلاو", "بەرگریی سلاڤ"), "black",
     "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5 e3 e6 Bxc4 Bb4",
     T("Black supports d5 with ...c6, keeping the c8-bishop free to develop to f5 or g4 before playing ...e6. Very solid and popular at every level.",
       "سیاه با ...c6 از d5 پشتیبانی می‌کند و فیل c8 را آزاد نگه می‌دارد تا قبل از ...e6 به f5 یا g4 برود. بسیار محکم و در همه‌ی سطح‌ها محبوب.",
       "ڕەش بە ...c6 پشتگیری لە d5 دەکات و فیلی c8 ئازاد دەهێڵێتەوە تا پێش ...e6 بچێتە f5 یان g4. زۆر پتەوە و لە هەموو ئاستێکدا بەناوبانگە.")),
    ("london", T("London System", "سیستم لندن", "سیستەمی لەندەن"), "white",
     "d4 d5 Bf4 Nf6 e3 e6 Nf3 c5 c3 Nc6 Nbd2 Bd6 Bg3 O-O Bd3",
     T("A system you can play against almost anything: d4, Bf4, e3, Nf3, c3, Nbd2 and Bd3. Solid, easy to learn and full of attacking chances on the kingside.",
       "سیستمی که تقریباً در برابر هر چیزی بازی می‌شود: d4، Bf4، e3، Nf3، c3، Nbd2 و Bd3. محکم، آسان برای یادگیری و پر از فرصت‌های حمله در جناح شاه.",
       "سیستەمێک کە دژی نزیکەی هەموو شتێک یاری دەکرێت: d4، Bf4، e3، Nf3، c3، Nbd2 و Bd3. پتەو، ئاسان بۆ فێربوون و پڕ لە دەرفەتی هێرش لە لای شا.")),
    ("kings-indian", T("King's Indian Defense", "دفاع هندی شاه", "بەرگریی هیندیی شا"), "black",
     "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7",
     T("A fighting defense: Black allows White a big center, castles quickly, then strikes with ...e5. After White closes the center with d5, Black attacks on the kingside with ...Nh5 and ...f5 while White attacks on the queenside.",
       "دفاعی جنگنده: سیاه اجازه‌ی مرکز بزرگ را به سفید می‌دهد، سریع قلعه می‌رود و بعد با ...e5 ضربه می‌زند. بعد از اینکه سفید با d5 مرکز را می‌بندد، سیاه با ...Nh5 و ...f5 در جناح شاه حمله می‌کند و سفید در جناح وزیر.",
       "بەرگرییەکی شەڕکەر: ڕەش ڕێگە دەدات سپی ناوەندێکی گەورەی هەبێت، خێرا قەڵابەندی دەکات و پاشان بە ...e5 لێدەدات. دوای ئەوەی سپی بە d5 ناوەند دادەخات، ڕەش بە ...Nh5 و ...f5 لە لای شا هێرش دەکات و سپی لە لای وەزیر.")),
    ("nimzo-indian", T("Nimzo-Indian Defense", "دفاع نیمزو-هندی", "بەرگریی نیمزۆ-هیندی"), "black",
     "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O",
     T("Black pins the c3-knight so that White cannot play e4 easily. Black is often happy to give the bishop for the knight, doubling White's pawns.",
       "سیاه اسب c3 را آچمز می‌کند تا سفید نتواند به‌راحتی e4 بازی کند. سیاه اغلب با کمال میل فیل را با اسب تعویض می‌کند تا سربازهای سفید دوبله شوند.",
       "ڕەش ئەسپی c3 دەبەستێتەوە بۆ ئەوەی سپی نەتوانێت بە ئاسانی e4 یاری بکات. ڕەش زۆرجار بە خۆشحاڵییەوە فیلەکە بە ئەسپەکە دەگۆڕێتەوە بۆ دووبارەکردنی سەربازەکانی سپی.")),
    ("grunfeld", T("Grünfeld Defense", "دفاع گرونفلد", "بەرگریی گرونفێلد"), "black",
     "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Nf3 c5",
     T("Black lets White build a big pawn center and then attacks it with pieces: the g7-bishop, ...c5 and ...Nc6. Dynamic and sharp — Kasparov's weapon against Karpov.",
       "سیاه اجازه می‌دهد سفید مرکز سربازی بزرگی بسازد و بعد با مهره‌ها به آن حمله می‌کند: فیل g7، ...c5 و ...Nc6. پویا و تند — سلاح کاسپاروف در برابر کارپوف.",
       "ڕەش ڕێگە دەدات سپی ناوەندێکی سەربازیی گەورە دروست بکات و پاشان بە مۆرەکان هێرشی دەکاتە سەر: فیلی g7، ...c5 و ...Nc6. چالاک و تیژ — چەکی کاسپارۆڤ دژی کارپۆڤ.")),
    ("english", T("English Opening", "گشایش انگلیسی", "دەستپێکی ئینگلیزی"), "white",
     "c4 e5 Nc3 Nf6 g3 d5 cxd5 Nxd5 Bg2 Nb6 Nf3 Nc6 O-O",
     T("1.c4 controls d5 from the side. White usually fianchettoes the bishop on g2, where it pressures the long diagonal — a Sicilian Defense with an extra move.",
       "1.c4 خانه‌ی d5 را از کنار کنترل می‌کند. سفید معمولاً فیل را در g2 فیانکتو می‌کند تا روی قطر بلند فشار بیاورد — مثل دفاع سیسیلی با یک حرکت اضافه.",
       "1.c4 خانەی d5 لە لاوە کۆنترۆڵ دەکات. سپی زۆرجار فیلەکە لە g2 فیانکێتۆ دەکات بۆ فشار خستنە سەر لاکێشە درێژەکە — وەک بەرگریی سیسیلی بە جوڵەیەکی زیادەوە.")),
    ("reti", T("Réti Opening", "گشایش رتی", "دەستپێکی ڕێتی"), "white",
     "Nf3 d5 c4 e6 g3 Nf6 Bg2 Be7 O-O O-O b3 c5 Bb2",
     T("A flexible hypermodern opening: White develops the knight, fianchettoes the bishops and attacks the center from the flanks with c4 instead of occupying it with pawns.",
       "گشایش هیپرمدرن و انعطاف‌پذیر: سفید اسب را وارد می‌کند، فیل‌ها را فیانکتو می‌کند و به‌جای اشغال مرکز با سرباز، با c4 از کناره‌ها به آن حمله می‌کند.",
       "دەستپێکێکی هایپەرمۆدێرنی نەرم: سپی ئەسپەکە دەهێنێتە ناو یاری، فیلەکان فیانکێتۆ دەکات و لە جیاتی داگیرکردنی ناوەند بە سەرباز، بە c4 لە لاکانەوە هێرشی دەکاتە سەر.")),
]

TRAPS = [
    ("scholar-defense", T("Scholar's Mate — and how to stop it", "مات چهارحرکتی — و چطور جلویش را بگیریم", "کش‌ماتی قوتابخانە — و چۆن ڕێگری لێ بکەین"),
     "e4 e5 Bc4 Nc6 Qh5 g6 Qf3 Nf6", "black", None,
     T("The queen and bishop attack f7 together, threatening Qxf7# on move four. The defence is simple: block with ...g6 (or ...Qe7), then develop with tempo by attacking the queen with ...Nf6.",
       "وزیر و فیل با هم به f7 حمله می‌کنند و در حرکت چهارم Qxf7# را تهدید می‌کنند. دفاع ساده است: با ...g6 (یا ...Qe7) سد کن و بعد با حمله به وزیر با ...Nf6 با تمپو گسترش پیدا کن.",
       "وەزیر و فیل پێکەوە هێرش دەکەنە سەر f7 و لە جوڵەی چوارەمدا هەڕەشەی Qxf7# دەکەن. بەرگرییەکە سادەیە: بە ...g6 (یان ...Qe7) ڕێگری بکە و پاشان بە هێرشکردنە سەر وەزیر بە ...Nf6 بە تێمپۆ گەشە بکە.")),
    ("fried-liver", T("The Fried Liver Attack", "حمله‌ی جگر سرخ‌کرده", "هێرشی جگەری سوورکراو"),
     "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7 Kxf7 Qf3+ Ke6 Nc3", "white", None,
     T("If Black recaptures 5...Nxd5 in the Two Knights, White can sacrifice the knight on f7, drag the king into the center and attack it with Qf3+ and Nc3. Black should play 5...Na5 instead.",
       "اگر سیاه در دفاع دو اسب با 5...Nxd5 پس بگیرد، سفید می‌تواند اسب را در f7 قربانی کند، شاه را به مرکز بکشاند و با Qf3+ و Nc3 به آن حمله کند. سیاه باید به‌جایش 5...Na5 بازی کند.",
       "ئەگەر ڕەش لە بەرگریی دوو ئەسپدا بە 5...Nxd5 بیگرێتەوە، سپی دەتوانێت ئەسپەکە لە f7 بکاتە قوربانی، شا ڕابکێشێتە ناوەند و بە Qf3+ و Nc3 هێرشی بکاتە سەر. ڕەش دەبێت لە جیاتی ئەوە 5...Na5 یاری بکات.")),
    ("legal", T("Légal's Mate", "مات لگال", "کش‌ماتی لێگال"),
     "e4 e5 Nf3 d6 Bc4 Bg4 Nc3 g6 Nxe5 Bxd1 Bxf7+ Ke7 Nd5#", "white", None,
     T("A 250-year-old trap: White 'blunders' the queen with Nxe5, but if Black greedily takes it, the minor pieces mate the king. The pinned knight was not really pinned!",
       "تله‌ای ۲۵۰ ساله: سفید با Nxe5 وزیر را «مفت می‌دهد»، اما اگر سیاه با حرص آن را بگیرد، مهره‌های سبک شاه را مات می‌کنند. اسبِ آچمز شده در واقع آچمز نبود!",
       "تەڵەیەکی ٢٥٠ ساڵە: سپی بە Nxe5 وەزیرەکە «بە خۆڕایی دەدات»، بەڵام ئەگەر ڕەش بە چاوچنۆکی بیگرێت، مۆرە سووکەکان شاکە کش‌مات دەکەن. ئەسپە بەستراوەکە لە ڕاستیدا نەبەسترابوو!")),
    ("blackburne-shilling", T("The Blackburne Shilling trap", "تله‌ی بلکبرن", "تەڵەی بلاکبێرن"),
     "e4 e5 Nf3 Nc6 Bc4 Nd4 Nxe5 Qg5 Nxf7 Qxg2 Rf1 Qxe4+ Be2 Nf3#", "black", None,
     T("Black's 3...Nd4 is a dubious move with a sting: if White grabs the e5-pawn, ...Qg5 hits the knight and g2, and White's king gets smothered.",
       "3...Nd4 سیاه حرکت مشکوکی است اما نیش دارد: اگر سفید سرباز e5 را بگیرد، ...Qg5 به اسب و g2 حمله می‌کند و شاه سفید خفه می‌شود.",
       "3...Nd4ی ڕەش جوڵەیەکی گومانلێکراوە بەڵام پێوەدانی هەیە: ئەگەر سپی سەربازی e5 بگرێت، ...Qg5 لە ئەسپەکە و g2 دەدات و شای سپی دەخنکێت.")),
    ("elephant", T("The Elephant Trap", "تله‌ی فیل", "تەڵەی فیل"),
     "d4 d5 c4 e6 Nc3 Nf6 Bg5 Nbd7 cxd5 exd5 Nxd5 Nxd5 Bxd8 Bb4+ Qd2 Bxd2+ Kxd2 Kxd8", "black", None,
     T("In the Queen's Gambit Declined the d5-pawn looks weak, but if White takes it with the knight, Black gives up the queen and wins a whole piece back with a check on b4.",
       "در گامبی وزیر پذیرفته‌نشده سرباز d5 ضعیف به نظر می‌رسد، اما اگر سفید آن را با اسب بزند، سیاه وزیر را می‌دهد و با کیش در b4 یک مهره‌ی کامل را پس می‌گیرد.",
       "لە گامبیتی وەزیری ڕەتکراودا سەربازی d5 لاواز دەردەکەوێت، بەڵام ئەگەر سپی بە ئەسپ بیگرێت، ڕەش وەزیرەکە دەدات و بە کشێک لە b4 مۆرەیەکی تەواو دەباتەوە.")),
    ("englund", T("The Englund Gambit trap", "تله‌ی گامبی انگلوند", "تەڵەی گامبیتی ئینگلوند"),
     "d4 e5 dxe5 Nc6 Nf3 Qe7 Bf4 Qb4+ Bd2 Qxb2 Bc3 Bb4 Qd2 Bxc3 Qxc3 Qc1#", "black", None,
     T("A trick for Black after 1.d4 e5: the queen hunts the b2-pawn and the bishops. If White plays carelessly, ...Qc1 is mate on the back rank.",
       "ترفندی برای سیاه بعد از 1.d4 e5: وزیر سرباز b2 و فیل‌ها را شکار می‌کند. اگر سفید بی‌دقت بازی کند، ...Qc1 در ردیف آخر مات است.",
       "فێڵێک بۆ ڕەش دوای 1.d4 e5: وەزیر ڕاوی سەربازی b2 و فیلەکان دەکات. ئەگەر سپی بێ ئاگا یاری بکات، ...Qc1 لە ڕیزی دواوە کش‌ماتە.")),
    ("fishing-pole", T("The Fishing Pole trap", "تله‌ی چوب ماهیگیری", "تەڵەی قۆڵاپی ماسیگرتن"),
     "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Ng4 h3 h5 hxg4 hxg4 Ne1 Qh4", "black", None,
     T("In the Berlin, Black leaves a knight on g4 as bait. If White takes it with h3xg4, the h-file opens and the black queen and rook break through to the king.",
       "در دفاع برلین، سیاه اسبی را در g4 به‌عنوان طعمه رها می‌کند. اگر سفید آن را با h3xg4 بگیرد، ستون h باز می‌شود و وزیر و رخ سیاه به شاه می‌رسند.",
       "لە بەرگریی بەرلیندا، ڕەش ئەسپێک لە g4 وەک داو بەجێ دەهێڵێت. ئەگەر سپی بە h3xg4 بیگرێت، ستوونی h دەکرێتەوە و وەزیر و قەڵای ڕەش دەگەنە شاکە.")),
    ("noahs-ark", T("Noah's Ark trap", "تله‌ی کشتی نوح", "تەڵەی کەشتیی نووح"),
     "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 d6 d4 b5 Bb3 Nxd4 Nxd4 exd4 Qxd4 c5 Qd5 Be6 Qc6+ Bd7 Qd5 c4", "black", None,
     T("An old trap in the Ruy Lopez: the white bishop on b3 gets trapped by Black's queenside pawns ...a6, ...b5 and ...c4 — like animals boarding Noah's Ark.",
       "تله‌ای قدیمی در روی لوپز: فیل سفید در b3 توسط سربازهای جناح وزیر سیاه (...a6، ...b5 و ...c4) به دام می‌افتد — مثل حیواناتی که سوار کشتی نوح می‌شوند.",
       "تەڵەیەکی کۆن لە ڕوی لۆپێزدا: فیلی سپی لە b3 لەلایەن سەربازەکانی لای وەزیری ڕەشەوە (...a6، ...b5 و ...c4) دەکەوێتە تەڵەوە — وەک ئەو گیانلەبەرانەی سواری کەشتیی نووح دەبن.")),
]

PRINCIPLES = [
    ("tempo", T("Time matters: tempo", "زمان مهم است: تمپو", "کات گرنگە: تێمپۆ"),
     T("A 'tempo' is one move. In the opening every tempo counts: develop a new piece with each move, avoid moving the same piece twice, and gain time by attacking the opponent's pieces while developing.",
       "«تمپو» یعنی یک حرکت. در گشایش هر تمپو مهم است: با هر حرکت یک مهره‌ی جدید وارد کن، یک مهره را دو بار حرکت نده و با حمله به مهره‌های حریف در حین گسترش وقت بخر.",
       "«تێمپۆ» واتە یەک جوڵە. لە دەستپێکدا هەموو تێمپۆیەک گرنگە: بە هەر جوڵەیەک مۆرەیەکی نوێ بهێنە ناو یاری، یەک مۆرە دوو جار مەجوڵێنە و بە هێرشکردنە سەر مۆرەکانی ڕکابەر لە کاتی گەشەدا کات ببەرەوە."),
     ["rnbqkbnr/ppp2ppp/8/3pp3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3", "rnbqkbnr/ppp1pppp/8/3P4/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 2"]),
    ("gambits", T("Gambits", "گامبی‌ها", "گامبیتەکان"),
     T("A gambit gives up a pawn in the opening for faster development, open lines or a strong center. Accept a gambit only if you can develop quickly; often returning the pawn is the safest answer.",
       "گامبی یعنی دادن یک سرباز در گشایش برای گسترش سریع‌تر، خط‌های باز یا مرکز قوی. گامبی را فقط وقتی بپذیر که بتوانی سریع گسترش پیدا کنی؛ اغلب پس دادن سرباز امن‌ترین پاسخ است.",
       "گامبیت واتە دانی سەربازێک لە دەستپێکدا بۆ گەشەی خێراتر، هێڵی کراوە یان ناوەندێکی بەهێز. تەنها کاتێک گامبیت وەربگرە کە بتوانیت خێرا گەشە بکەیت؛ زۆرجار گەڕاندنەوەی سەربازەکە سەلامەتترین وەڵامە."),
     ["rnbqkbnr/pppp1ppp/8/4p3/4PP2/8/PPPP2PP/RNBQKBNR b KQkq - 0 2", "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq - 0 2"]),
    ("structure", T("Pawn structure", "ساختار سربازی", "پێکهاتەی سەربازی"),
     T("Your pawn moves in the opening decide the character of the game. Pawns cannot move back, so every pawn move leaves squares weak forever. Move central pawns, and keep the pawns in front of your castled king at home.",
       "حرکت‌های سربازی تو در گشایش شخصیت بازی را تعیین می‌کنند. سرباز به عقب برنمی‌گردد، پس هر حرکت سرباز خانه‌هایی را برای همیشه ضعیف می‌کند. سربازهای مرکزی را حرکت بده و سربازهای جلوی شاهِ قلعه‌رفته را سر جایشان نگه دار.",
       "جوڵە سەربازییەکانت لە دەستپێکدا کەسایەتی یارییەکە دیاری دەکەن. سەرباز ناگەڕێتەوە دواوە، بۆیە هەر جوڵەیەکی سەرباز خانەگەلێک بۆ هەمیشە لاواز دەکات. سەربازە ناوەندییەکان بجوڵێنە و سەربازەکانی بەردەم شای قەڵابەندکراو لە شوێنی خۆیان بهێڵەوە."),
     ["r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 1 2"]),
    ("connect-rooks", T("Finish development: connect the rooks", "پایان گسترش: رخ‌ها را به هم وصل کن", "کۆتایی گەشە: قەڵاکان پێکەوە ببەستەوە"),
     T("The opening is finished when your minor pieces are out, the king is castled and the queen has left the back rank so that the rooks see each other. Then the rooks go to open or half-open files.",
       "گشایش وقتی تمام می‌شود که مهره‌های سبک بیرون آمده‌اند، شاه قلعه رفته و وزیر ردیف اول را ترک کرده تا رخ‌ها همدیگر را ببینند. بعد رخ‌ها به ستون‌های باز یا نیمه‌باز می‌روند.",
       "دەستپێک کاتێک تەواو دەبێت کە مۆرە سووکەکان دەرچوون، شا قەڵابەندی کردووە و وەزیر ڕیزی یەکەمی بەجێهێشتووە بۆ ئەوەی قەڵاکان یەکتر ببینن. پاشان قەڵاکان دەچنە ستوونە کراوە یان نیوە-کراوەکان."),
     ["r2q1rk1/ppp1bppp/2np1n2/4p3/2B1P1b1/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 4 7", "r1bq1rk1/pp2bppp/2n1pn2/2pp4/3P1B2/2P1PN2/PP1N1PPP/R2QKB1R w KQ - 1 7"]),
]

def rep_lesson(oid, title, side, line_san, ideas):
    moves = line_san.split()
    steps = [theory(ideas, fen=pos_after(" ".join(moves[:min(len(moves), 6)])))]
    steps.append(line(line_san, side, PLAY_W if side == "white" else PLAY_B))
    # A typical position from the end of the line: find a good move for the player's side.
    b = chess.Board()
    for s in moves:
        b.push_san(s)
    if (b.turn == chess.WHITE) != (side == "white"):
        b.pop()
    steps.append(best(b.fen(), GOOD, margin=30))
    return lesson(f"openings.rep.{oid}", title, ideas, 2, steps)

RECALL = T("Play this line from memory — Zorix plays the other side.", "این خط را از حفظ بازی کن — Zorix طرف مقابل را بازی می‌کند.", "ئەم هێڵە لەبەرەوە یاری بکە — Zorix لایەنەکەی تر یاری دەکات.")
RECALL_OTHER = T("Now switch sides: play your opponent's moves. Knowing both sides means no surprises.",
                 "حالا جایت را عوض کن: حرکت‌های حریف را بازی کن. وقتی هر دو طرف را بدانی، غافلگیر نمی‌شوی.",
                 "ئێستا لایەنەکەت بگۆڕە: جوڵەکانی ڕکابەر یاری بکە. کاتێک هەردوو لایەن بزانیت، تووشی سەرسوڕمان نابیت.")

def recall_lesson(oid, title, side, line_san, ideas):
    """Spaced repetition: the whole line without notes, first with your side, then with the other side."""
    other = "black" if side == "white" else "white"
    intro = T(f"Can you play the {title['en']} from memory? First your own moves, then your opponent's.",
              f"آیا می‌توانی {title['fa']} را از حفظ بازی کنی؟ اول حرکت‌های خودت، بعد حرکت‌های حریف.",
              f"دەتوانیت {title['ckb']} لەبەرەوە یاری بکەیت؟ سەرەتا جوڵەکانی خۆت، پاشان هی ڕکابەر.")
    return lesson(f"openings.recall.{oid}",
                  T(title["en"] + " — from memory", title["fa"] + " — از حفظ", title["ckb"] + " — لەبەرەوە"),
                  intro, 2, [line(line_san, side, RECALL), line(line_san, other, RECALL_OTHER)])

def clearly_best(b, move):
    infos = cb.ENGINE.analyse(b, chess.engine.Limit(depth=18), multipv=2)
    if not infos or infos[0]["pv"][0] != move:
        return False
    if len(infos) < 2:
        return True
    s0 = infos[0]["score"].pov(b.turn); s1 = infos[1]["score"].pov(b.turn)
    if s0.is_mate() and s0.mate() > 0:
        return not (s1.is_mate() and s1.mate() > 0)
    if s1.is_mate():
        return True
    return s0.score() - s1.score() >= 150

def trap_lesson(tid, title, line_san, side, _unused, ideas):
    moves = line_san.split()
    steps = [theory(ideas, fen=pos_after(" ".join(moves[:max(1, len(moves) - 3)])))]
    steps.append(line(line_san, side, PLAY_W if side == "white" else PLAY_B))
    # Puzzle: a move of the trapping side that is clearly the best (engine-verified), played to the end of the line.
    for key_idx in range(len(moves) - 1, -1, -1):
        mover_white = (key_idx % 2 == 0)
        if mover_white != (side == "white"):
            continue
        b = chess.Board()
        for sm in moves[:key_idx]:
            b.push_san(sm)
        if clearly_best(b, b.parse_san(moves[key_idx])):
            rest = []
            bb = b.copy()
            for sm in moves[key_idx:]:
                mv = bb.parse_san(sm); rest.append(mv.uci()); bb.push(mv)
            if len(rest) % 2 == 0:
                rest = rest[:-1]
            # every later solver move must also be clearly best
            ok = True
            bb = b.copy()
            for i, u in enumerate(rest):
                mv = chess.Move.from_uci(u)
                if i % 2 == 0 and i > 0 and not clearly_best(bb, mv):
                    ok = False
                    break
                bb.push(mv)
            if ok:
                steps.append(puzzle({"fen": b.fen(), "moves": rest, "rating": 900, "themes": ["opening"]}, TRAP))
                break
    return lesson(f"openings.trap.{tid}", title, ideas, 2, steps)

def build():
    principles = []
    for pid, title, text, fens in PRINCIPLES:
        principles.append(lesson(f"openings.principle.{pid}", title, text, 2,
                                 [theory(text)] + [best(f, GOOD, margin=30) for f in fens]))
    traps = [trap_lesson(*t) for t in TRAPS]
    white = [rep_lesson(*r) for r in REPERTOIRE if r[2] == "white"]
    black = [rep_lesson(*r) for r in REPERTOIRE if r[2] == "black"]
    recall = [recall_lesson(*r) for r in REPERTOIRE]
    return course("openings", T("Openings", "گشایش‌ها", "دەستپێکەکان"),
        T("Opening principles, famous traps and a complete repertoire with White and Black — every move explained.",
          "اصول گشایش، تله‌های معروف و یک رپرتوار کامل با سفید و سیاه — همه‌ی حرکت‌ها با توضیح.",
          "بنەماکانی دەستپێک، تەڵە بەناوبانگەکان و ڕێپێرتوارێکی تەواو بە سپی و ڕەش — هەموو جوڵەکان بە ڕوونکردنەوە."),
        [chapter("principles", T("Opening principles", "اصول گشایش", "بنەماکانی دەستپێک"), principles),
         chapter("traps", T("Opening traps", "تله‌های گشایش", "تەڵەکانی دەستپێک"), traps),
         chapter("white", T("Repertoire with White", "رپرتوار با سفید", "ڕێپێرتوار بە سپی"), white),
         chapter("black", T("Repertoire with Black", "رپرتوار با سیاه", "ڕێپێرتوار بە ڕەش"), black),
         chapter("recall", T("Repertoire drills", "تمرین رپرتوار", "ڕاهێنانی ڕێپێرتوار"), recall)])
