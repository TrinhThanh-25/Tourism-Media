package com.example.tourismmedia.ui.chat;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

/** Small, dependency-free renderer for the Markdown subset used by travel answers. */
final class ChatMarkdown {
    private static final int CODE_BACKGROUND = 0xFFE9EEEB;

    private ChatMarkdown() {
    }

    static CharSequence render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        SpannableStringBuilder output = new SpannableStringBuilder();

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int headingLevel = headingLevel(line);
            if (headingLevel > 0) line = line.substring(headingLevel + 1);

            String leadingTrimmed = trimLeading(line);
            if (leadingTrimmed.startsWith("- ") || leadingTrimmed.startsWith("* ")) {
                line = "• " + leadingTrimmed.substring(2);
            } else if (leadingTrimmed.startsWith("> ")) {
                line = "› " + leadingTrimmed.substring(2);
            }

            int lineStart = output.length();
            appendInline(output, line);
            if (headingLevel > 0 && output.length() > lineStart) {
                output.setSpan(new StyleSpan(Typeface.BOLD), lineStart, output.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                output.setSpan(new RelativeSizeSpan(headingLevel == 1 ? 1.2f : 1.1f),
                        lineStart, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (index < lines.length - 1) output.append('\n');
        }
        return output;
    }

    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && level < 3 && line.charAt(level) == '#') level++;
        return level > 0 && level < line.length() && line.charAt(level) == ' ' ? level : 0;
    }

    private static String trimLeading(String value) {
        int start = 0;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        return value.substring(start);
    }

    private static void appendInline(SpannableStringBuilder output, String text) {
        int cursor = 0;
        while (cursor < text.length()) {
            Marker marker = nextMarker(text, cursor);
            if (marker == null) {
                output.append(text, cursor, text.length());
                return;
            }
            output.append(text, cursor, marker.start);
            int contentStart = marker.start + marker.token.length();
            int close = text.indexOf(marker.token, contentStart);
            if (close < 0 || close == contentStart) {
                output.append(marker.token);
                cursor = contentStart;
                continue;
            }

            int spanStart = output.length();
            output.append(text, contentStart, close);
            int flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
            if ("`".equals(marker.token)) {
                output.setSpan(new TypefaceSpan("monospace"), spanStart, output.length(), flags);
                output.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), spanStart, output.length(), flags);
            } else {
                output.setSpan(new StyleSpan(Typeface.BOLD), spanStart, output.length(), flags);
            }
            cursor = close + marker.token.length();
        }
    }

    private static Marker nextMarker(String text, int from) {
        Marker nearest = null;
        for (String token : new String[]{"**", "__", "`"}) {
            int start = text.indexOf(token, from);
            if (start >= 0 && (nearest == null || start < nearest.start)) {
                nearest = new Marker(start, token);
            }
        }
        return nearest;
    }

    private static final class Marker {
        final int start;
        final String token;

        Marker(int start, String token) {
            this.start = start;
            this.token = token;
        }
    }
}
