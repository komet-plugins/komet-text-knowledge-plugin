/*
 * Copyright © 2026 Knowledge Graphlet / IKE Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.text.concordance;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AsciiDoc text with its inline markup removed, knowing for each character of the plain text the
 * column of the source it came from, so that words found in the plain text can be named by the
 * columns of the source. An authored {@code k:} token becomes its label, a link or
 * cross-reference its text, index terms are dropped or reduced to their visible words, anchors
 * (a bibliography entry's {@code [[[id]]]} among them) and formatting marks are removed, and runs
 * of white space become one space. A file read as text and a sentence
 * read from the store are both read this way, so the two are read alike.
 */
public final class PlainText {

    private static final Pattern KONCEPT_TOKEN = Pattern.compile("k:[A-Za-z0-9_-]*(?:=[^\\[\\s]*)?\\[([^\\]]*)]");
    private static final Pattern INDEX_HIDDEN = Pattern.compile("\\(\\(\\(.*?\\)\\)\\)|indexterm2?:\\[[^\\]]*]");
    private static final Pattern INDEX_VISIBLE = Pattern.compile("\\(\\((.*?)\\)\\)");
    private static final Pattern XREF = Pattern.compile("xref:[^\\[\\s]*\\[([^\\]]*)]");
    private static final Pattern CROSS_REFERENCE = Pattern.compile("<<[^>]*>>");
    private static final Pattern LINK = Pattern.compile("(?:https?|link|mailto):[^\\[\\s]*\\[([^\\]]*)]");
    private static final Pattern BARE_URL = Pattern.compile("https?://\\S+");
    private static final Pattern BIBLIOGRAPHY_ANCHOR = Pattern.compile("\\[\\[\\[[^\\]]*]]]");
    private static final Pattern ANCHOR = Pattern.compile("\\[\\[[^\\]]*]]|\\[#[^\\]]*]");
    private static final Pattern HARD_BREAK = Pattern.compile("( )\\+");
    private static final Pattern FORMATTING = Pattern.compile("[*_`#^~]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** Text and the source column of each of its characters. */
    private record Mapped(String text, int[] columns) {
    }

    private final String source;
    private final String text;
    private final int[] columns;

    private PlainText(String source, Mapped plain) {
        this.source = source;
        this.text = plain.text();
        this.columns = plain.columns();
    }

    /**
     * Removes the inline markup of AsciiDoc text.
     *
     * @param source the text as written
     * @return the plain text, mapped to the source
     */
    public static PlainText of(String source) {
        Objects.requireNonNull(source, "source");
        int[] identity = new int[source.length()];
        Arrays.setAll(identity, i -> i);
        Mapped plain = new Mapped(source, identity);
        plain = keep(plain, KONCEPT_TOKEN, 1);
        plain = keep(plain, INDEX_HIDDEN, 0);
        plain = keep(plain, INDEX_VISIBLE, 1);
        plain = keep(plain, XREF, 1);
        plain = keep(plain, CROSS_REFERENCE, 0);
        plain = keep(plain, LINK, 1);
        plain = keep(plain, BARE_URL, 0);
        plain = keep(plain, BIBLIOGRAPHY_ANCHOR, 0);
        plain = keep(plain, ANCHOR, 0);
        plain = keep(plain, HARD_BREAK, 1);
        plain = keep(plain, FORMATTING, 0);
        return new PlainText(source, strip(oneSpace(plain)));
    }

    /**
     * The text as written.
     *
     * @return the source
     */
    public String source() {
        return source;
    }

    /**
     * The text with its inline markup removed.
     *
     * @return the plain text
     */
    public String text() {
        return text;
    }

    /**
     * The column of the source where a stretch of the plain text begins.
     *
     * @param start the offset of the stretch's first character in the plain text
     * @return the column of that character in the source
     * @throws IndexOutOfBoundsException if the offset is not a character of the plain text
     */
    public int sourceStart(int start) {
        Objects.checkIndex(start, text.length());
        return columns[start];
    }

    /**
     * The column of the source just past where a stretch of the plain text ends.
     *
     * @param end the offset just past the stretch's last character in the plain text
     * @return the column just past that character in the source
     * @throws IndexOutOfBoundsException if the stretch would end before the first character or
     *                                   after the last
     */
    public int sourceEnd(int end) {
        Objects.checkIndex(end - 1, text.length());
        return columns[end - 1] + 1;
    }

    /** Replaces each match by its group, or removes it when the group is zero, keeping each kept character's column. */
    private static Mapped keep(Mapped in, Pattern pattern, int group) {
        Matcher match = pattern.matcher(in.text());
        StringBuilder text = new StringBuilder(in.text().length());
        int[] columns = new int[in.text().length()];
        int last = 0;
        while (match.find()) {
            copy(in, last, match.start(), text, columns);
            if (group > 0 && match.start(group) >= 0) {
                copy(in, match.start(group), match.end(group), text, columns);
            }
            last = match.end();
        }
        copy(in, last, in.text().length(), text, columns);
        return new Mapped(text.toString(), Arrays.copyOf(columns, text.length()));
    }

    /** Makes each run of white space one space, at the column where the run began. */
    private static Mapped oneSpace(Mapped in) {
        Matcher match = SPACES.matcher(in.text());
        StringBuilder text = new StringBuilder(in.text().length());
        int[] columns = new int[in.text().length()];
        int last = 0;
        while (match.find()) {
            copy(in, last, match.start(), text, columns);
            columns[text.length()] = in.columns()[match.start()];
            text.append(' ');
            last = match.end();
        }
        copy(in, last, in.text().length(), text, columns);
        return new Mapped(text.toString(), Arrays.copyOf(columns, text.length()));
    }

    /** Removes white space at either end, as {@link String#strip()} does. */
    private static Mapped strip(Mapped in) {
        String text = in.text();
        int from = 0;
        int to = text.length();
        while (from < to && Character.isWhitespace(text.charAt(from))) {
            from++;
        }
        while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
            to--;
        }
        return new Mapped(text.substring(from, to), Arrays.copyOfRange(in.columns(), from, to));
    }

    private static void copy(Mapped in, int from, int to, StringBuilder text, int[] columns) {
        for (int i = from; i < to; i++) {
            columns[text.length()] = in.columns()[i];
            text.append(in.text().charAt(i));
        }
    }
}
