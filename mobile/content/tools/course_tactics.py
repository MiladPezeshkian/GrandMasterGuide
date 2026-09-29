"""Course 3: Tactics - themed puzzle lessons with explanations, graded by difficulty, plus a tactics ladder."""
import chess
import cb
from cb import *

FA = "۰۱۲۳۴۵۶۷۸۹"; CK = "٠١٢٣٤٥٦٧٨٩"
def fa_num(n): return "".join(FA[int(c)] for c in str(n))
def ck_num(n): return "".join(CK[int(c)] for c in str(n))

FIND = T("Find the best move.", "بهترین حرکت را پیدا کن.", "باشترین جوڵە بدۆزەرەوە.")
PROMPTS = {
    "hangingPiece": T("Win material!", "مهره ببر!", "مۆرە ببەرەوە!"),
    "fork": T("Find the fork!", "چنگال را پیدا کن!", "دووشاخەکە بدۆزەرەوە!"),
    "pin": T("Use the pin!", "از آچمز استفاده کن!", "بەستنەوەکە بەکاربهێنە!"),
    "skewer": T("Find the skewer!", "سیخ را پیدا کن!", "شیشەکە بدۆزەرەوە!"),
    "discoveredAttack": T("Unleash a discovered attack!", "حمله‌ی کشف‌شده را اجرا کن!", "هێرشی ئاشکراکراو ئەنجام بدە!"),
    "discoveredCheck": T("Find the discovered check!", "کیش کشف‌شده را پیدا کن!", "کشی ئاشکراکراو بدۆزەرەوە!"),
    "doubleCheck": T("Find the double check!", "کیش دوبل را پیدا کن!", "کشی دووانە بدۆزەرەوە!"),
    "capturingDefender": T("Remove the defender!", "مدافع را حذف کن!", "بەرگریکار لابەرە!"),
    "deflection": T("Deflect the defender!", "مدافع را منحرف کن!", "بەرگریکار لابدە!"),
    "attraction": T("Lure a piece to a bad square!", "مهره‌ای را به خانه‌ی بد بکشان!", "مۆرەیەک ڕابکێشە بۆ خانەیەکی خراپ!"),
    "sacrifice": T("Find the sacrifice!", "قربانی را پیدا کن!", "قوربانییەکە بدۆزەرەوە!"),
    "promotion": T("Promote with a tactic!", "با یک تاکتیک ارتقا بده!", "بە تاکتیکێک بەرزی بکەرەوە!"),
    "quietMove": T("Find the quiet killer move!", "حرکت آرام و کشنده را پیدا کن!", "جوڵە ئارامە کوشندەکە بدۆزەرەوە!"),
}

THEMES = [
    # chapter id, chapter title, [(theme, filter, title, intro, levels, per lesson)]
    ("material", T("Winning material", "بردن مهره", "بردنەوەی مۆرە"), [
        ("hangingPiece", lambda t: "hangingPiece" in t,
         T("Hanging pieces", "مهره‌های بی‌دفاع", "مۆرە بێ پارێزەرەکان"),
         T("The simplest tactic: a piece is attacked more times than it is defended, or not defended at all. Count attackers and defenders on the square — if you have more, you win it. Always check the value of what you capture with and what you capture.",
           "ساده‌ترین تاکتیک: به مهره‌ای بیشتر از دفاعش حمله شده، یا اصلاً دفاع نشده است. تعداد حمله‌کننده‌ها و مدافع‌های یک خانه را بشمار — اگر بیشتر داری، آن را می‌بری. همیشه ارزش مهره‌ای را که با آن می‌زنی و مهره‌ای را که می‌زنی بسنج.",
           "سادەترین تاکتیک: هێرش کراوەتە سەر مۆرەیەک زیاتر لە بەرگرییەکەی، یان هیچ پارێزراو نییە. هێرشبەر و بەرگریکارانی خانەکە بژمێرە — ئەگەر زیاترت هەیە، دەیبەیتەوە. هەمیشە بەهای ئەو مۆرەیەی پێی دەگریت و ئەوەی دەیگریت بپێوە."), 4, 7),
    ]),
    ("forks", T("Forks", "چنگال", "دووشاخە"), [
        ("fork", lambda t: "fork" in t,
         T("Forks", "چنگال", "دووشاخە"),
         T("A fork is one piece attacking two (or more) enemy pieces at once. The opponent can save only one of them. Knights are the masters of forks because their attack cannot be blocked, but queens, pawns and even kings can fork too. Look for loose pieces and the king: they are the favourite targets.",
           "چنگال یعنی یک مهره هم‌زمان به دو (یا چند) مهره‌ی حریف حمله کند. حریف فقط یکی را می‌تواند نجات دهد. اسب‌ها استاد چنگال‌اند چون حمله‌شان را نمی‌شود سد کرد، اما وزیر، سرباز و حتی شاه هم چنگال می‌زنند. دنبال مهره‌های بی‌دفاع و شاه باش: آن‌ها هدف‌های محبوب‌اند.",
           "دووشاخە واتە یەک مۆرە لە هەمان کاتدا هێرش بکاتە سەر دوو (یان زیاتر) مۆرەی ڕکابەر. ڕکابەر تەنها دەتوانێت یەکێکیان ڕزگار بکات. ئەسپەکان ماستەری دووشاخەن چونکە هێرشەکەیان ڕێگری لێ ناکرێت، بەڵام وەزیر، سەرباز و تەنانەت شاش دووشاخە دەکەن. بەدوای مۆرە بێ پارێزەرەکان و شادا بگەڕێ: ئامانجە دڵخوازەکانن."), 8, 7),
    ]),
    ("pins", T("Pins", "آچمز", "بەستنەوە"), [
        ("pin", lambda t: "pin" in t,
         T("Pins", "آچمز", "بەستنەوە"),
         T("A pin attacks a piece that cannot move away without exposing something more valuable behind it. If the piece behind is the king, the pinned piece may not move at all (an absolute pin). Attack a pinned piece again — it cannot run away!",
           "آچمز یعنی به مهره‌ای حمله شود که نمی‌تواند کنار برود چون چیز ارزشمندتری پشتش است. اگر پشت آن شاه باشد، مهره‌ی آچمز شده اصلاً حق حرکت ندارد (آچمز مطلق). به مهره‌ی آچمز شده دوباره حمله کن — نمی‌تواند فرار کند!",
           "بەستنەوە واتە هێرش کردنە سەر مۆرەیەک کە ناتوانێت لابچێت چونکە شتێکی بەنرختر لە پشتییەوەیە. ئەگەر شا لە پشتییەوە بێت، مۆرە بەستراوەکە هیچ مافی جوڵەی نییە (بەستنەوەی ڕەها). دووبارە هێرش بکە سەر مۆرە بەستراوەکە — ناتوانێت ڕابکات!"), 6, 7),
    ]),
    ("skewers", T("Skewers", "سیخ", "شیش"), [
        ("skewer", lambda t: "skewer" in t,
         T("Skewers", "سیخ", "شیش"),
         T("A skewer is a pin turned around: you attack a valuable piece, it must move, and the piece behind it is captured. Rooks, bishops and queens skewer along lines — especially kings and queens standing on the same line.",
           "سیخ آچمزِ برعکس است: به مهره‌ای ارزشمند حمله می‌کنی، مجبور است کنار برود و مهره‌ی پشتش زده می‌شود. رخ، فیل و وزیر در امتداد خط‌ها سیخ می‌زنند — به‌خصوص وقتی شاه و وزیر روی یک خط باشند.",
           "شیش بەستنەوەیەکی هەڵگەڕاوەیە: هێرش دەکەیتە سەر مۆرەیەکی بەنرخ، ناچارە لابچێت و مۆرەی پشتی دەگیرێت. قەڵا، فیل و وەزیر بە درێژایی هێڵەکان شیش دەکەن — بەتایبەت کاتێک شا و وەزیر لەسەر یەک هێڵ بن."), 4, 6),
    ]),
    ("discovered", T("Discovered attacks", "حمله‌های کشف‌شده", "هێرشە ئاشکراکراوەکان"), [
        ("discoveredAttack", lambda t: "discoveredAttack" in t,
         T("Discovered attacks", "حمله‌ی کشف‌شده", "هێرشی ئاشکراکراو"),
         T("When a piece moves out of the way of a rook, bishop or queen behind it, the long-range piece suddenly attacks. The moving piece is free to make its own threat — two threats with one move!",
           "وقتی مهره‌ای از جلوی رخ، فیل یا وزیرِ پشت سرش کنار می‌رود، آن مهره‌ی دوربرد ناگهان حمله می‌کند. مهره‌ی کنار رفته هم آزاد است تهدید خودش را بسازد — دو تهدید با یک حرکت!",
           "کاتێک مۆرەیەک لە بەردەم قەڵا، فیل یان وەزیری پشتی لادەچێت، ئەو مۆرە دوورهاوێژە لەناکاو هێرش دەکات. مۆرە لاچووەکەش ئازادە هەڕەشەی خۆی دروست بکات — دوو هەڕەشە بە یەک جوڵە!"), 5, 7),
        ("discoveredCheck", lambda t: "discoveredCheck" in t,
         T("Discovered checks", "کیش کشف‌شده", "کشی ئاشکراکراو"),
         T("A discovered check is the strongest discovered attack: the opponent must answer the check, so the moving piece can grab anything it wants.",
           "کیش کشف‌شده قوی‌ترین حمله‌ی کشف‌شده است: حریف باید به کیش جواب بدهد، پس مهره‌ی حرکت‌کرده هر چه بخواهد می‌تواند بگیرد.",
           "کشی ئاشکراکراو بەهێزترین هێرشی ئاشکراکراوە: ڕکابەر دەبێت وەڵامی کشەکە بداتەوە، بۆیە مۆرە جوڵاوەکە دەتوانێت هەرچی بیەوێت بیگرێت."), 3, 6),
        ("doubleCheck", lambda t: "doubleCheck" in t,
         T("Double check", "کیش دوبل", "کشی دووانە"),
         T("Two pieces give check at the same time. You cannot block two checks or capture two pieces at once — the king has to move. Double checks often lead to mate.",
           "دو مهره هم‌زمان کیش می‌دهند. نمی‌شود دو کیش را سد کرد یا دو مهره را با هم زد — شاه باید حرکت کند. کیش دوبل اغلب به مات می‌رسد.",
           "دوو مۆرە لە هەمان کاتدا کش دەدەن. ناتوانیت ڕێگری لە دوو کش بکەیت یان دوو مۆرە پێکەوە بگریت — شا دەبێت بجوڵێت. کشی دووانە زۆرجار دەگاتە کش‌مات."), 2, 6),
    ]),
    ("defenders", T("Overloaded defenders", "مدافع‌های گرفتار", "بەرگریکارە بارقورسەکان"), [
        ("capturingDefender", lambda t: "capturingDefender" in t,
         T("Removing the defender", "حذف مدافع", "لابردنی بەرگریکار"),
         T("If a piece is defended only once, capture or chase away its defender first — then the piece falls.",
           "اگر از مهره‌ای فقط یک بار دفاع شده، اول مدافعش را بزن یا فراری بده — بعد خود آن مهره از دست می‌رود.",
           "ئەگەر مۆرەیەک تەنها یەک جار پارێزراوە، سەرەتا بەرگریکارەکەی بگرە یان ڕاوی بنێ — پاشان خودی مۆرەکە دەکەوێت."), 3, 6),
        ("deflection", lambda t: "deflection" in t,
         T("Deflection", "انحراف", "لادان"),
         T("Deflection forces a defending piece away from its post — usually with a capture or a check it must answer. Once it leaves, what it was guarding collapses.",
           "انحراف مهره‌ی مدافع را از جایش دور می‌کند — معمولاً با زدن یا کیشی که باید به آن جواب بدهد. وقتی آن‌جا را ترک کند، چیزی که از آن محافظت می‌کرد فرو می‌ریزد.",
           "لادان مۆرەی بەرگریکار ناچار دەکات شوێنەکەی بەجێ بهێڵێت — زۆرجار بە گرتن یان کشێک کە دەبێت وەڵامی بداتەوە. کاتێک دەڕوات، ئەوەی دەیپاراست دەڕوخێت."), 4, 6),
        ("attraction", lambda t: "attraction" in t,
         T("Attraction (decoy)", "جذب (طعمه)", "ڕاکێشان (داو)"),
         T("Attraction lures an enemy piece — often the king — onto a square where a second tactic (a fork, a mate, a skewer) works. The bait is usually a sacrifice that must be taken.",
           "جذب یک مهره‌ی حریف — اغلب شاه — را به خانه‌ای می‌کشاند که تاکتیک دوم (چنگال، مات، سیخ) در آن کار می‌کند. طعمه معمولاً قربانی‌ای است که باید گرفته شود.",
           "ڕاکێشان مۆرەیەکی ڕکابەر — زۆرجار شا — دەبات بۆ خانەیەک کە تاکتیکێکی دووەم (دووشاخە، کش‌مات، شیش) تێیدا کار دەکات. داوەکە زۆرجار قوربانییەکە کە دەبێت وەربگیرێت."), 4, 6),
        ("sacrifice", lambda t: "sacrifice" in t,
         T("Sacrifices", "قربانی", "قوربانی"),
         T("A sacrifice gives up material on purpose to get something bigger: mate, more material, or a crushing attack. Calculate the forcing moves (checks, captures, threats) to the end before you sacrifice.",
           "قربانی یعنی عمداً مهره بدهی تا چیز بزرگ‌تری بگیری: مات، مهره‌ی بیشتر یا حمله‌ای کوبنده. قبل از قربانی، حرکت‌های اجباری (کیش، زدن، تهدید) را تا آخر حساب کن.",
           "قوربانی واتە بە ئەنقەست مۆرە بدەیت بۆ ئەوەی شتێکی گەورەتر بەدەست بهێنیت: کش‌مات، مۆرەی زیاتر یان هێرشێکی کوشندە. پێش قوربانی، جوڵە ناچارییەکان (کش، گرتن، هەڕەشە) تا کۆتایی حساب بکە."), 3, 6),
    ]),
    ("pawn-tactics", T("Pawns and promotion", "سرباز و ارتقا", "سەرباز و بەرزبوونەوە"), [
        ("promotion", lambda t: "promotion" in t,
         T("Promotion tactics", "تاکتیک‌های ارتقا", "تاکتیکەکانی بەرزبوونەوە"),
         T("A pawn close to promotion is worth far more than one point. Deflect the blockers, sacrifice to clear the path, and make a new queen.",
           "سربازی که به ارتقا نزدیک است خیلی بیشتر از یک امتیاز ارزش دارد. مدافع‌ها را منحرف کن، با قربانی راه را باز کن و یک وزیر جدید بساز.",
           "سەربازێک کە لە بەرزبوونەوە نزیکە زۆر زیاتر لە یەک خاڵ بەهای هەیە. ڕێگرەکان لابدە، بە قوربانی ڕێگاکە بکەرەوە و وەزیرێکی نوێ دروست بکە."), 3, 6),
    ]),
    ("quiet", T("Quiet moves", "حرکت‌های آرام", "جوڵە ئارامەکان"), [
        ("quietMove", lambda t: "quietMove" in t,
         T("Quiet moves", "حرکت‌های آرام", "جوڵە ئارامەکان"),
         T("Not every winning move is a check or a capture. A quiet move — a retreat, a pawn push, a simple threat — can create two threats the opponent cannot parry. These are the hardest tactics to see.",
           "هر حرکت برنده‌ای کیش یا زدن نیست. یک حرکت آرام — عقب‌نشینی، پیشروی سرباز یا یک تهدید ساده — می‌تواند دو تهدید بسازد که حریف نتواند جلوی هر دو را بگیرد. این‌ها سخت‌ترین تاکتیک‌ها برای دیدن‌اند.",
           "هەموو جوڵەیەکی براوە کش یان گرتن نییە. جوڵەیەکی ئارام — کشانەوە، پێشڕەوی سەرباز یان هەڕەشەیەکی سادە — دەتوانێت دوو هەڕەشە دروست بکات کە ڕکابەر نەتوانێت هەردووکیان بگرێتەوە. ئەمانە قورسترین تاکتیکەکانن بۆ بینین."), 4, 6),
    ]),
    ("phases", T("Tactics in every phase", "تاکتیک در هر مرحله", "تاکتیک لە هەموو قۆناغێکدا"), [
        ("opening", lambda t: "opening" in t,
         T("Opening tactics", "تاکتیک‌های گشایش", "تاکتیکەکانی دەستپێک"),
         T("Games are often decided in the first dozen moves: undeveloped pieces, an uncastled king and loose pawns give many chances. Punish opening mistakes!",
           "بازی‌ها اغلب در ده دوازده حرکت اول تعیین می‌شوند: مهره‌های وارد نشده، شاهِ قلعه‌نرفته و سربازهای بی‌دفاع فرصت‌های زیادی می‌سازند. اشتباهات گشایش را تنبیه کن!",
           "یارییەکان زۆرجار لە دە دوازدە جوڵەی یەکەمدا یەکلا دەبنەوە: مۆرە نەهاتووەکان، شای قەڵابەندنەکراو و سەربازە بێ پارێزەرەکان دەرفەتی زۆر دروست دەکەن. هەڵەکانی دەستپێک سزا بدە!"), 4, 7),
        ("endgame", lambda t: "endgame" in t,
         T("Endgame tactics", "تاکتیک‌های آخر بازی", "تاکتیکەکانی کۆتایی یاری"),
         T("Fewer pieces does not mean fewer tricks: skewers, promotions and king forks decide many endgames.",
           "مهره‌ی کمتر یعنی ترفند کمتر نیست: سیخ، ارتقا و چنگال شاه سرنوشت بسیاری از آخربازی‌ها را تعیین می‌کنند.",
           "مۆرەی کەمتر واتای فێڵی کەمتر نییە: شیش، بەرزبوونەوە و دووشاخەی شا چارەنووسی زۆرێک لە کۆتاییەکان دیاری دەکەن."), 4, 7),
    ]),
]

def demo_theory(p, lang_text):
    """A theory card showing the first puzzle's solution as an example."""
    b = chess.Board(p["fen"])
    first = p["moves"][0]
    return theory(lang_text, fen=p["fen"], arrows=[first])

EXAMPLE = T("Example: the arrow shows the winning idea. Now it's your turn.", "مثال: فلش ایده‌ی برنده را نشان می‌دهد. حالا نوبت توست.", "نموونە: تیرەکە بیرۆکە براوەکە پیشان دەدات. ئێستا نۆرەی تۆیە.")

def theme_lessons(theme, flt, title, intro, levels, per):
    pool = [p for p in cb.DATA["puzzles"] if flt(set(p["themes"])) and p["id"] not in cb.USED]
    pool.sort(key=lambda p: p["rating"])
    n_levels = min(levels, len(pool) // per)
    if n_levels == 0:
        return []
    out = []
    # split into equal-size difficulty bands
    step = len(pool) / n_levels
    for i in range(n_levels):
        band = pool[int(i * step): int((i + 1) * step)]
        # take puzzles spread over the band
        chosen = [band[int(j * len(band) / per)] for j in range(per)] if len(band) >= per else band
        for p in chosen:
            cb.USED.add(p["id"])
        steps = []
        if i == 0:
            steps.append(demo_theory(chosen[0], EXAMPLE))
            chosen = chosen[1:]
        steps += [puzzle(p, PROMPTS.get(theme, FIND)) for p in chosen]
        lvl = min(5, 1 + (sum(p["rating"] for p in chosen) // max(1, len(chosen)) - 400) // 400)
        out.append(lesson(f"tactics.{theme}.{i + 1}",
                          T(f"{title['en']} {i + 1}", f"{title['fa']} {fa_num(i + 1)}", f"{title['ckb']} {ck_num(i + 1)}"),
                          intro if i == 0 else T(
                              f"{title['en']}, level {i + 1}. Rating around {chosen[len(chosen)//2]['rating']}.",
                              f"{title['fa']}، سطح {fa_num(i + 1)}. سختی حدود {fa_num(chosen[len(chosen)//2]['rating'])}.",
                              f"{title['ckb']}، ئاستی {ck_num(i + 1)}. قورسی نزیکەی {ck_num(chosen[len(chosen)//2]['rating'])}."),
                          lvl, steps))
    return out

# Endgame puzzles are left for the Endgames course.
BANK_RESERVE = 0

def ladder_chapter(target_lessons):
    pool = [p for p in cb.DATA["puzzles"] if p["id"] not in cb.USED and "endgame" not in p["themes"]]
    pool.sort(key=lambda p: p["rating"])
    per = 7
    n = min(target_lessons, max(0, len(pool) - BANK_RESERVE) // per)
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
        lessons.append(lesson(f"tactics.ladder.{i + 1}",
            T(f"Tactics trainer {i + 1}", f"تمرین تاکتیک {fa_num(i + 1)}", f"ڕاهێنانی تاکتیک {ck_num(i + 1)}"),
            T(f"Mixed tactics around rating {avg}. No hints about the theme — find the idea yourself.",
              f"تاکتیک‌های مختلط با سختی حدود {fa_num(avg)}. بدون راهنمایی درباره‌ی نوع تاکتیک — ایده را خودت پیدا کن.",
              f"تاکتیکی تێکەڵ بە قورسی نزیکەی {ck_num(avg)}. بەبێ ئاماژە بە جۆری تاکتیک — بیرۆکەکە خۆت بدۆزەرەوە."),
            min(5, 1 + (avg - 400) // 400), [puzzle(p, FIND) for p in chosen]))
    return chapter("ladder", T("Tactics trainer", "تمرین تاکتیک", "ڕاهێنانی تاکتیک"), lessons)

def build(target_total=110):
    chapters = []
    count = 0
    for cid, ctitle, themes in THEMES:
        lessons = []
        for theme, flt, title, intro, levels, per in themes:
            lessons += theme_lessons(theme, flt, title, intro, levels, per)
        if lessons:
            chapters.append(chapter(cid, ctitle, lessons))
            count += len(lessons)
    ladder = ladder_chapter(max(10, target_total - count))
    if ladder:
        chapters.append(ladder)
    return course("tactics", T("Tactics", "تاکتیک", "تاکتیک"),
        T("Forks, pins, skewers, discovered attacks, sacrifices and more — real positions from games, graded by difficulty.",
          "چنگال، آچمز، سیخ، حمله‌ی کشف‌شده، قربانی و بیشتر — وضعیت‌های واقعی از بازی‌ها، مرتب‌شده بر اساس سختی.",
          "دووشاخە، بەستنەوە، شیش، هێرشی ئاشکراکراو، قوربانی و زیاتر — دۆخی ڕاستەقینە لە یارییەکانەوە، ڕیزکراو بەپێی قورسی."),
        chapters)
