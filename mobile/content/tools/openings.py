#!/usr/bin/env python3
"""Opening book: names in English / Persian / Kurdish Sorani, verified with python-chess.
Generates mobile/shared/src/commonMain/kotlin/com/zorix/chess/learn/OpeningBook.kt"""
import chess, sys, os

# (id, eco, english, persian, kurdish, SAN moves)
OPENINGS = [
    ("kings-pawn", "C20", "King's Pawn Game", "بازی سرباز شاه", "یاریی سەربازی شا", "e4 e5"),
    ("italian", "C50", "Italian Game", "گشایش ایتالیایی", "دەستپێکی ئیتاڵی", "e4 e5 Nf3 Nc6 Bc4"),
    ("giuoco-piano", "C53", "Italian Game: Giuoco Piano", "گشایش ایتالیایی: جوکو پیانو", "دەستپێکی ئیتاڵی: جۆکۆ پیانۆ", "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4"),
    ("giuoco-pianissimo", "C54", "Italian Game: Giuoco Pianissimo", "گشایش ایتالیایی: جوکو پیانیسیمو", "دەستپێکی ئیتاڵی: جۆکۆ پیانیسیمۆ", "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3"),
    ("evans-gambit", "C51", "Evans Gambit", "گامبی ایوانز", "گامبیتی ئیڤانس", "e4 e5 Nf3 Nc6 Bc4 Bc5 b4"),
    ("two-knights", "C55", "Two Knights Defense", "دفاع دو اسب", "بەرگریی دوو ئەسپ", "e4 e5 Nf3 Nc6 Bc4 Nf6"),
    ("fried-liver", "C57", "Two Knights: Fried Liver Attack", "دفاع دو اسب: حمله‌ی جگر سرخ‌کرده", "دوو ئەسپ: هێرشی جگەری سوورکراو", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7"),
    ("two-knights-na5", "C58", "Two Knights: Main Line", "دفاع دو اسب: خط اصلی", "دوو ئەسپ: هێڵی سەرەکی", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5"),
    ("ruy-lopez", "C60", "Ruy Lopez (Spanish Game)", "گشایش روی لوپز (اسپانیایی)", "ڕوی لۆپێز (یاریی ئیسپانی)", "e4 e5 Nf3 Nc6 Bb5"),
    ("ruy-morphy", "C70", "Ruy Lopez: Morphy Defense", "روی لوپز: دفاع مورفی", "ڕوی لۆپێز: بەرگریی مۆرفی", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4"),
    ("ruy-closed", "C84", "Ruy Lopez: Closed", "روی لوپز: بسته", "ڕوی لۆپێز: داخراو", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O"),
    ("ruy-exchange", "C68", "Ruy Lopez: Exchange Variation", "روی لوپز: واریانت تعویض", "ڕوی لۆپێز: گۆڕینەوە", "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6 dxc6"),
    ("berlin", "C65", "Ruy Lopez: Berlin Defense", "روی لوپز: دفاع برلین", "ڕوی لۆپێز: بەرگریی بەرلین", "e4 e5 Nf3 Nc6 Bb5 Nf6"),
    ("scotch", "C45", "Scotch Game", "گشایش اسکاتلندی", "یاریی سکۆتلەندی", "e4 e5 Nf3 Nc6 d4 exd4 Nxd4"),
    ("scotch-gambit", "C44", "Scotch Gambit", "گامبی اسکاتلندی", "گامبیتی سکۆتلەندی", "e4 e5 Nf3 Nc6 d4 exd4 Bc4"),
    ("four-knights", "C47", "Four Knights Game", "بازی چهار اسب", "یاریی چوار ئەسپ", "e4 e5 Nf3 Nc6 Nc3 Nf6"),
    ("petrov", "C42", "Petrov's Defense", "دفاع پتروف", "بەرگریی پێترۆف", "e4 e5 Nf3 Nf6"),
    ("philidor", "C41", "Philidor Defense", "دفاع فیلیدور", "بەرگریی فیلیدۆر", "e4 e5 Nf3 d6"),
    ("kings-gambit", "C30", "King's Gambit", "گامبی شاه", "گامبیتی شا", "e4 e5 f4"),
    ("kings-gambit-accepted", "C33", "King's Gambit Accepted", "گامبی شاه پذیرفته‌شده", "گامبیتی شای وەرگیراو", "e4 e5 f4 exf4"),
    ("vienna", "C25", "Vienna Game", "گشایش وین", "یاریی ڤیەننا", "e4 e5 Nc3"),
    ("center-game", "C22", "Center Game", "بازی مرکزی", "یاریی ناوەند", "e4 e5 d4 exd4 Qxd4"),
    ("latvian", "C40", "Latvian Gambit", "گامبی لتونیایی", "گامبیتی لاتڤی", "e4 e5 Nf3 f5"),
    ("sicilian", "B20", "Sicilian Defense", "دفاع سیسیلی", "بەرگریی سیسیلی", "e4 c5"),
    ("sicilian-open", "B32", "Sicilian Defense: Open", "دفاع سیسیلی: باز", "سیسیلی: کراوە", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4"),
    ("najdorf", "B90", "Sicilian: Najdorf Variation", "دفاع سیسیلی: واریانت نایدورف", "سیسیلی: نایدۆرف", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6"),
    ("dragon", "B70", "Sicilian: Dragon Variation", "دفاع سیسیلی: واریانت اژدها", "سیسیلی: ئەژدیها", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6"),
    ("sicilian-classical", "B56", "Sicilian: Classical Variation", "دفاع سیسیلی: کلاسیک", "سیسیلی: کلاسیک", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 Nc6"),
    ("scheveningen", "B80", "Sicilian: Scheveningen Variation", "دفاع سیسیلی: شوننینگن", "سیسیلی: شێڤێنینگن", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6"),
    ("sveshnikov", "B33", "Sicilian: Sveshnikov Variation", "دفاع سیسیلی: سوشنیکوف", "سیسیلی: سڤێشنیکۆڤ", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5"),
    ("taimanov", "B46", "Sicilian: Taimanov Variation", "دفاع سیسیلی: تایمانوف", "سیسیلی: تایمانۆڤ", "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6"),
    ("alapin", "B22", "Sicilian: Alapin Variation", "دفاع سیسیلی: آلاپین", "سیسیلی: ئالاپین", "e4 c5 c3"),
    ("closed-sicilian", "B23", "Sicilian: Closed", "دفاع سیسیلی: بسته", "سیسیلی: داخراو", "e4 c5 Nc3"),
    ("smith-morra", "B21", "Sicilian: Smith-Morra Gambit", "سیسیلی: گامبی اسمیت-مورا", "سیسیلی: گامبیتی سمیس-مۆڕا", "e4 c5 d4 cxd4 c3"),
    ("rossolimo", "B31", "Sicilian: Rossolimo Variation", "سیسیلی: روسولیمو", "سیسیلی: ڕۆسۆلیمۆ", "e4 c5 Nf3 Nc6 Bb5"),
    ("french", "C00", "French Defense", "دفاع فرانسوی", "بەرگریی فەرەنسی", "e4 e6"),
    ("french-main", "C10", "French Defense: Main Line", "دفاع فرانسوی: خط اصلی", "بەرگریی فەرەنسی: هێڵی سەرەکی", "e4 e6 d4 d5"),
    ("french-advance", "C02", "French: Advance Variation", "دفاع فرانسوی: پیشروی", "فەرەنسی: پێشڕەوی", "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3"),
    ("french-winawer", "C15", "French: Winawer Variation", "دفاع فرانسوی: وینآور", "فەرەنسی: ڤینەوەر", "e4 e6 d4 d5 Nc3 Bb4"),
    ("french-classical", "C11", "French: Classical Variation", "دفاع فرانسوی: کلاسیک", "فەرەنسی: کلاسیک", "e4 e6 d4 d5 Nc3 Nf6"),
    ("french-tarrasch", "C03", "French: Tarrasch Variation", "دفاع فرانسوی: تاراش", "فەرەنسی: تاراش", "e4 e6 d4 d5 Nd2"),
    ("french-exchange", "C01", "French: Exchange Variation", "دفاع فرانسوی: تعویض", "فەرەنسی: گۆڕینەوە", "e4 e6 d4 d5 exd5 exd5"),
    ("caro-kann", "B10", "Caro-Kann Defense", "دفاع کاروکان", "بەرگریی کارۆ-کان", "e4 c6"),
    ("caro-classical", "B18", "Caro-Kann: Classical Variation", "کاروکان: کلاسیک", "کارۆ-کان: کلاسیک", "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5"),
    ("caro-advance", "B12", "Caro-Kann: Advance Variation", "کاروکان: پیشروی", "کارۆ-کان: پێشڕەوی", "e4 c6 d4 d5 e5 Bf5"),
    ("caro-exchange", "B13", "Caro-Kann: Exchange Variation", "کاروکان: تعویض", "کارۆ-کان: گۆڕینەوە", "e4 c6 d4 d5 exd5 cxd5"),
    ("scandinavian", "B01", "Scandinavian Defense", "دفاع اسکاندیناوی", "بەرگریی سکاندیناڤی", "e4 d5"),
    ("scandinavian-main", "B01", "Scandinavian: Main Line", "دفاع اسکاندیناوی: خط اصلی", "سکاندیناڤی: هێڵی سەرەکی", "e4 d5 exd5 Qxd5 Nc3 Qa5"),
    ("pirc", "B07", "Pirc Defense", "دفاع پیرتس", "بەرگریی پیرتس", "e4 d6 d4 Nf6 Nc3 g6"),
    ("modern", "B06", "Modern Defense", "دفاع مدرن", "بەرگریی مۆدێرن", "e4 g6"),
    ("alekhine", "B02", "Alekhine's Defense", "دفاع آلخین", "بەرگریی ئەلێخین", "e4 Nf6"),
    ("queens-pawn", "D00", "Queen's Pawn Game", "بازی سرباز وزیر", "یاریی سەربازی وەزیر", "d4 d5"),
    ("queens-gambit", "D06", "Queen's Gambit", "گامبی وزیر", "گامبیتی وەزیر", "d4 d5 c4"),
    ("qgd", "D30", "Queen's Gambit Declined", "گامبی وزیر پذیرفته‌نشده", "گامبیتی وەزیری ڕەتکراو", "d4 d5 c4 e6"),
    ("qgd-main", "D37", "Queen's Gambit Declined: Main Line", "گامبی وزیر پذیرفته‌نشده: خط اصلی", "گامبیتی وەزیری ڕەتکراو: هێڵی سەرەکی", "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7"),
    ("qga", "D20", "Queen's Gambit Accepted", "گامبی وزیر پذیرفته‌شده", "گامبیتی وەزیری وەرگیراو", "d4 d5 c4 dxc4"),
    ("slav", "D10", "Slav Defense", "دفاع اسلاو", "بەرگریی سلاڤ", "d4 d5 c4 c6"),
    ("semi-slav", "D43", "Semi-Slav Defense", "دفاع نیمه‌اسلاو", "بەرگریی نیوە-سلاڤ", "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6"),
    ("albin", "D08", "Albin Countergambit", "ضدگامبی آلبین", "دژەگامبیتی ئەلبین", "d4 d5 c4 e5"),
    ("london", "D02", "London System", "سیستم لندن", "سیستەمی لەندەن", "d4 d5 Bf4"),
    ("london-nf6", "A46", "London System (1...Nf6)", "سیستم لندن (با اسب f6)", "سیستەمی لەندەن (ئەسپ f6)", "d4 Nf6 Bf4"),
    ("london-main", "D02", "London System: Main Setup", "سیستم لندن: آرایش اصلی", "سیستەمی لەندەن: ڕێکخستنی سەرەکی", "d4 d5 Bf4 Nf6 e3 e6 Nf3 c5 c3"),
    ("indian", "A45", "Indian Defense", "دفاع هندی", "بەرگریی هیندی", "d4 Nf6"),
    ("kings-indian", "E60", "King's Indian Defense", "دفاع هندی شاه", "بەرگریی هیندیی شا", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6"),
    ("kings-indian-classical", "E92", "King's Indian: Classical", "دفاع هندی شاه: کلاسیک", "هیندیی شا: کلاسیک", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5"),
    ("nimzo-indian", "E20", "Nimzo-Indian Defense", "دفاع نیمزو-هندی", "بەرگریی نیمزۆ-هیندی", "d4 Nf6 c4 e6 Nc3 Bb4"),
    ("queens-indian", "E12", "Queen's Indian Defense", "دفاع هندی وزیر", "بەرگریی هیندیی وەزیر", "d4 Nf6 c4 e6 Nf3 b6"),
    ("grunfeld", "D80", "Grünfeld Defense", "دفاع گرونفلد", "بەرگریی گرونفێلد", "d4 Nf6 c4 g6 Nc3 d5"),
    ("catalan", "E01", "Catalan Opening", "گشایش کاتالان", "دەستپێکی کاتالان", "d4 Nf6 c4 e6 g3"),
    ("benoni", "A60", "Modern Benoni", "دفاع بنونی مدرن", "بێنۆنیی مۆدێرن", "d4 Nf6 c4 c5 d5 e6"),
    ("benko", "A57", "Benko Gambit", "گامبی بنکو", "گامبیتی بێنکۆ", "d4 Nf6 c4 c5 d5 b5"),
    ("dutch", "A80", "Dutch Defense", "دفاع هلندی", "بەرگریی هۆڵەندی", "d4 f5"),
    ("trompowsky", "A45", "Trompowsky Attack", "حمله‌ی ترومپوفسکی", "هێرشی ترۆمپۆڤسکی", "d4 Nf6 Bg5"),
    ("englund", "A40", "Englund Gambit", "گامبی انگلوند", "گامبیتی ئینگلوند", "d4 e5"),
    ("english", "A10", "English Opening", "گشایش انگلیسی", "دەستپێکی ئینگلیزی", "c4"),
    ("english-reversed-sicilian", "A20", "English: Reversed Sicilian", "گشایش انگلیسی: سیسیلی معکوس", "ئینگلیزی: سیسیلیی پێچەوانە", "c4 e5"),
    ("english-symmetrical", "A30", "English: Symmetrical", "گشایش انگلیسی: قرینه", "ئینگلیزی: هاوتەریب", "c4 c5"),
    ("reti", "A04", "Réti Opening", "گشایش رتی", "دەستپێکی ڕێتی", "Nf3"),
    ("reti-main", "A09", "Réti Opening: Main Line", "گشایش رتی: خط اصلی", "ڕێتی: هێڵی سەرەکی", "Nf3 d5 c4"),
    ("kings-indian-attack", "A07", "King's Indian Attack", "حمله‌ی هندی شاه", "هێرشی هیندیی شا", "Nf3 d5 g3 Nf6 Bg2 e6 O-O"),
    ("larsen", "A01", "Nimzo-Larsen Attack", "حمله‌ی نیمزو-لارسن", "هێرشی نیمزۆ-لارسن", "b3"),
    ("birds", "A02", "Bird's Opening", "گشایش برد", "دەستپێکی بێرد", "f4"),
]

def main():
    out_lines = []
    for oid, eco, en, fa, ckb, san in OPENINGS:
        b = chess.Board()
        uci = []
        for tok in san.split():
            mv = b.parse_san(tok)
            uci.append(mv.uci())
            b.push(mv)
        out_lines.append((oid, eco, en, fa, ckb, " ".join(uci), san))
    ids = [o[0] for o in out_lines]
    assert len(ids) == len(set(ids)), "duplicate ids"
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "../../shared/src/commonMain/kotlin/com/zorix/chess/learn/OpeningBook.kt")
    with open(path, "w") as f:
        f.write("""package com.zorix.chess.learn

import com.zorix.chess.core.Move

// Generated by mobile/content/tools/openings.py - do not edit by hand.

/** A named opening line. [moves] are UCI moves from the initial position. */
class Opening(val id: String, val eco: String, private val names: Map<String, String>, val moves: List<String>, val san: String) {
    fun name(lang: String): String = names[lang] ?: names.getValue("en")
}

/** Opening names for the coach ("Book move: Sicilian Defense") and the opening lessons. */
object OpeningBook {
    val all: List<Opening> = listOf(
""")
        for oid, eco, en, fa, ckb, uci, san in out_lines:
            f.write(f'        Opening("{oid}", "{eco}", mapOf("en" to "{en}", "fa" to "{fa}", "ckb" to "{ckb}"), "{uci}".split(\' \'), "{san}"),\n')
        f.write("""    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Opening? = byId[id]

    /** True when the game from the initial position is still inside some opening line. */
    fun isBook(moves: List<Move>): Boolean {
        if (moves.isEmpty()) return true
        val uci = moves.map { it.uci }
        return all.any { o -> o.moves.size >= uci.size && o.moves.subList(0, uci.size) == uci }
    }

    /** The most specific opening whose whole line has been played, if any. */
    fun name(moves: List<Move>): Opening? {
        val uci = moves.map { it.uci }
        return all.filter { o -> o.moves.size <= uci.size && uci.subList(0, o.moves.size) == o.moves }
            .maxByOrNull { it.moves.size }
    }
}
""")
    print(f"wrote {len(out_lines)} openings to {path}")

if __name__ == "__main__":
    main()
