// Generated from the real coach (the app's logic + Stockfish 19) for the landing page's demo card.
export const COACH_SAMPLES = {
 "fa": {
  "blunder": {
   "san": "Qxf7+",
   "quality": "blunder",
   "message": "سارا، این یک اشتباه فاحش بود. وزیر شما در ⁦f7⁩ بی‌دفاع می‌ماند و بعد از ⁦Kxf7⁩ از دست می‌رود. حرکت بهتر ⁦Bc4⁩ بود. این حرکت سرباز حریف سیاه در ⁦f7⁩ را آچمز می‌کند؛ اگر این مهره حرکت کند، اسب حریف سیاه در ⁦g8⁩ از دست می‌رود. نکته: قبل از حرکت، همه‌ی کیش‌ها و زدن‌های هر دو طرف را بررسی کن.",
   "best": "Bc4"
  },
  "good": {
   "san": "Bb5",
   "quality": "book",
   "message": "سارا، این حرکت طبق تئوری گشایش است. این گشایش «گشایش روی لوپز (اسپانیایی)» است. این حرکت فیل را وارد بازی می‌کند. پیش‌بینی من این است که حرکت بعدی حریف سیاه ⁦Nf6⁩ باشد."
  }
 },
 "ckb": {
  "blunder": {
   "san": "Qxf7+",
   "quality": "blunder",
   "message": "ئۆی سارا، هەڵەیەکی گەورە! وەزیرەکەت لە ⁦f7⁩ بێ پارێزەر دەمێنێت و دوای ⁦Kxf7⁩ لەدەست دەچێت. باشتر بوو ⁦Bc4⁩ یاری بکەیت: سەربازی ڕکابەری ڕەش لە ⁦f7⁩ دەبەستێتەوە: ئەگەر بجوڵێت، ئەسپی ڕکابەری ڕەش لە ⁦g8⁩ لە پشتییەوە لەدەست دەچێت. ئامۆژگاری: پێش جوڵە، هەموو کش و گرتنەکانی هەردوو لا بپشکنە.",
   "best": "Bc4"
  },
  "good": {
   "san": "Bb5",
   "quality": "book",
   "message": "سارا، ئەمە تیۆری دەستپێکە. ئەمە دەستپێکی «ڕوی لۆپێز (یاریی ئیسپانی)»ە. فیل دەهێنێتە ناو یارییەکە (گەشەپێدانی مۆرەکان). Zorix پێشبینی دەکات ڕکابەری ڕەش ⁦a6⁩ یاری بکات."
  }
 },
 "en": {
  "blunder": {
   "san": "Qxf7+",
   "quality": "blunder",
   "message": "Oh no, Sara — a blunder. It leaves your queen on f7 unprotected: after Kxf7 it is lost. Better was Bc4: it pins the black pawn on f7: if it moves, the black knight on g8 behind it is lost. Tip: check every capture and every check for both sides before you move.",
   "best": "Bc4"
  },
  "good": {
   "san": "Bb5",
   "quality": "book",
   "message": "Opening theory, Sara. This is the Ruy Lopez (Spanish Game). It develops the bishop and brings it into play. Zorix expects Nf6 next."
  }
 }
} as const;
