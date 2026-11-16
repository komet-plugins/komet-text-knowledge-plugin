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

import java.util.Objects;
import java.util.Optional;

/**
 * Matched words in the sentence they were matched in, read as plain text: what stands before them,
 * the words, and what follows them. A reader judges a match by the words around it, which a row
 * of the match table names only by its sentence and columns: {@code case} matched to a
 * conditional reads wrong at once in {@code each new clinical use case generates}.
 *
 * @param before what stands before the words, with the space between
 * @param words  the words
 * @param after  what follows the words, with the space between
 */
public record InContext(String before, String words, String after) {

    /** What stands for the words a clipped side leaves out. */
    public static final String ELLIPSIS = "…";

    public InContext {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(words, "words");
        Objects.requireNonNull(after, "after");
    }

    /**
     * Reads matched words in their stored sentence. The columns count in the stored text, inline
     * markup and all, as a match table's row names them; the sentence is read as plain text, as
     * the recognizer read it.
     *
     * @param stored the sentence's text as the store holds it
     * @param from   the column of the words' first character in the stored text
     * @param to     the column just past their last character
     * @return the words in their sentence; empty when the columns are not in the sentence or hold
     *         no plain text, as when the sentence was revised after the match
     */
    public static Optional<InContext> of(String stored, int from, int to) {
        Objects.requireNonNull(stored, "stored");
        if (from < 0 || to <= from || to > stored.length()) {
            return Optional.empty();
        }
        PlainText plain = PlainText.of(stored);
        String text = plain.text();
        int start = -1;
        int end = -1;
        for (int i = 0; i < text.length(); i++) {
            int column = plain.sourceStart(i);
            if (column >= from && column < to) {
                if (start < 0) {
                    start = i;
                }
                end = i + 1;
            }
        }
        if (start < 0) {
            return Optional.empty();
        }
        return Optional.of(new InContext(text.substring(0, start), text.substring(start, end), text.substring(end)));
    }

    /**
     * The words with at most a number of words on each side, a side cut short beginning or ending
     * with {@link #ELLIPSIS}.
     *
     * @param each how many words to keep on each side; zero keeps none
     * @return the clipped context; this one when neither side is longer
     * @throws IllegalArgumentException if {@code each} is negative
     */
    public InContext clipped(int each) {
        if (each < 0) {
            throw new IllegalArgumentException("a side keeps no fewer than no words: " + each);
        }
        return new InContext(tail(before, each), words, head(after, each));
    }

    /**
     * The context as one line.
     *
     * @return before, the words and after, run together
     */
    public String text() {
        return before + words + after;
    }

    /** The last words of a text and the space after them, the rest an ellipsis. */
    private static String tail(String text, int keep) {
        int end = text.length();
        int at = end;
        while (at > 0 && text.charAt(at - 1) == ' ') {
            at--;
        }
        int kept = 0;
        while (at > 0 && kept < keep) {
            while (at > 0 && text.charAt(at - 1) != ' ') {
                at--;
            }
            kept++;
            if (kept < keep) {
                while (at > 0 && text.charAt(at - 1) == ' ') {
                    at--;
                }
            }
        }
        if (at == 0) {
            return text;
        }
        String rest = text.substring(at);
        return rest.isBlank() ? ELLIPSIS + rest : ELLIPSIS + " " + rest.stripLeading();
    }

    /** The first words of a text and the space before them, the rest an ellipsis. */
    private static String head(String text, int keep) {
        int at = 0;
        while (at < text.length() && text.charAt(at) == ' ') {
            at++;
        }
        int kept = 0;
        while (at < text.length() && kept < keep) {
            while (at < text.length() && text.charAt(at) != ' ') {
                at++;
            }
            kept++;
            if (kept < keep) {
                while (at < text.length() && text.charAt(at) == ' ') {
                    at++;
                }
            }
        }
        if (at == text.length()) {
            return text;
        }
        String rest = text.substring(0, at);
        return rest.isBlank() ? rest + ELLIPSIS : rest.stripTrailing() + " " + ELLIPSIS;
    }
}
