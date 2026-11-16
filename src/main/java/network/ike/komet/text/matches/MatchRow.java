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
package network.ike.komet.text.matches;

import design.knowledge.stamp.adoc.model.KonceptRef;
import network.ike.komet.text.recognize.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One row of a match table: a stretch of a stored sentence's words, the candidates a stage
 * proposed for it, and, once a rater has judged it, the judgment (ike-lab-documents topic
 * {@code dev-conceptification-match-table}). A row a rater added for words no stage proposed is a
 * {@link Judgment#MISSED missed} row, with no candidates and no stage.
 *
 * <p>Its text form is one line of columns separated by {@code |}:</p>
 * <pre>
 * sentence[from,to] | words | candidates | stage | score | judgment | concept named | rater
 * </pre>
 * <p>The candidates are {@code k:} tokens in ranked order, separated by spaces. Empty columns at
 * the end of a row are left off. A {@code |}, {@code [}, {@code ]} or {@code \} in the words or the
 * rater is written after a {@code \}.</p>
 *
 * @param sentence   the stored sentence's id
 * @param from       the first column of the words in the sentence's stored text
 * @param to         the column just past the words' last character
 * @param words      the words matched, as the matcher read them, without the sentence's inline markup
 * @param candidates the concepts proposed for the words, the first shown; empty for a missed row
 * @param stage      the stage that proposed them; empty for a missed row
 * @param score      the stage's score for the first candidate, when it gives one
 * @param judgment   the rater's judgment, empty until one is made
 * @param named      the concept the judgment names, when it names one
 * @param rater      the rater, when the rater is not the author of the line's commit
 */
public record MatchRow(String sentence, int from, int to, String words, List<KonceptRef> candidates,
                       Optional<Stage> stage, OptionalDouble score, Optional<Judgment> judgment,
                       Optional<KonceptRef> named, Optional<String> rater) {

    private static final Pattern KONCEPT = Pattern.compile(KonceptRef.REGEX);
    private static final Pattern ANCHOR = Pattern.compile("([^\\s\\[\\]|]+)\\[(\\d+),(\\d+)]");
    private static final String SEPARATOR = " | ";
    private static final int COLUMNS = 8;

    /**
     * Where a row's words stand: a sentence and its columns, which a new run's row for the same
     * words shares.
     *
     * @param sentence the stored sentence's id
     * @param from     the first column of the words
     * @param to       the column just past them
     */
    public record Place(String sentence, int from, int to) {
    }

    /**
     * Validates the row.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if the sentence id cannot be written in a row, the columns
     *                                  are not a stretch, the words are blank, a missed row has
     *                                  candidates or a stage, a proposed row lacks them, or the
     *                                  judgment names a concept it must not or lacks one it must
     */
    public MatchRow {
        Objects.requireNonNull(sentence, "sentence");
        Objects.requireNonNull(words, "words");
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(judgment, "judgment");
        Objects.requireNonNull(named, "named");
        Objects.requireNonNull(rater, "rater");
        if (sentence.isBlank() || !ANCHOR.matcher(sentence + "[0,1]").matches()) {
            throw new IllegalArgumentException("a sentence id with no spaces, brackets or bars, not '" + sentence + "'");
        }
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("columns " + from + " to " + to + " are not a stretch of words");
        }
        if (words.isBlank()) {
            throw new IllegalArgumentException("a row names some words");
        }
        boolean missed = judgment.filter(j -> j == Judgment.MISSED).isPresent();
        if (missed != candidates.isEmpty() || missed == stage.isPresent()) {
            throw new IllegalArgumentException(missed
                    ? "a missed row has no candidates and no stage"
                    : "a proposed row has candidates and a stage");
        }
        if (judgment.isEmpty() && (named.isPresent() || rater.isPresent())) {
            throw new IllegalArgumentException("a row no one has judged names no concept and no rater");
        }
        if (judgment.isPresent()) {
            Judgment.Named rule = judgment.get().named();
            if (rule == Judgment.Named.REQUIRED && named.isEmpty()) {
                throw new IllegalArgumentException(judgment.get().word() + " names the concept the words name");
            }
            if (rule == Judgment.Named.NONE && named.isPresent()) {
                throw new IllegalArgumentException(judgment.get().word() + " names no concept of its own");
            }
        }
    }

    /**
     * A row a stage proposed and no one has judged.
     *
     * @param sentence   the stored sentence's id
     * @param from       the first column of the words
     * @param to         the column just past them
     * @param words      the words
     * @param candidates the concepts proposed, in ranked order
     * @param stage      the stage that proposed them
     * @param score      the stage's score for the first candidate, when it gives one
     * @return the row
     */
    public static MatchRow proposed(String sentence, int from, int to, String words, List<KonceptRef> candidates,
                                    Stage stage, OptionalDouble score) {
        return new MatchRow(sentence, from, to, words, candidates, Optional.of(stage), score,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A row a rater added for words no stage proposed.
     *
     * @param sentence the stored sentence's id
     * @param from     the first column of the words
     * @param to       the column just past them
     * @param words    the words
     * @param named    the concept the words name
     * @param rater    the rater, when not the author of the line's commit
     * @return the row
     */
    public static MatchRow missed(String sentence, int from, int to, String words, KonceptRef named,
                                  Optional<String> rater) {
        return new MatchRow(sentence, from, to, words, List.of(), Optional.empty(), OptionalDouble.empty(),
                Optional.of(Judgment.MISSED), Optional.of(named), rater);
    }

    /**
     * This row judged.
     *
     * @param made  the judgment
     * @param names the concept it names, when it names one
     * @param by    the rater, when not the author of the line's commit
     * @return the judged row
     * @throws IllegalArgumentException if the judgment and the concept named do not agree, or
     *                                  the judgment is {@link Judgment#MISSED}, which only a missed
     *                                  row carries
     */
    public MatchRow judged(Judgment made, Optional<KonceptRef> names, Optional<String> by) {
        if (made == Judgment.MISSED) {
            throw new IllegalArgumentException("only a missed row is judged missed");
        }
        return new MatchRow(sentence, from, to, words, candidates, stage, score, Optional.of(made), names, by);
    }

    /**
     * Where this row's words stand.
     *
     * @return the sentence and columns
     */
    public Place place() {
        return new Place(sentence, from, to);
    }

    /**
     * The first candidate, the one a badge shows and a judgment is made on.
     *
     * @return the first candidate; empty for a missed row
     */
    public Optional<KonceptRef> first() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    /**
     * The row as a match table writes it.
     *
     * @return one line, with no line break
     */
    public String text() {
        List<String> columns = new ArrayList<>(COLUMNS);
        columns.add(sentence + "[" + from + "," + to + "]");
        columns.add(escape(words));
        columns.add(String.join(" ", candidates.stream().map(KonceptRef::toString).toList()));
        columns.add(stage.map(Stage::word).orElse(""));
        columns.add(score.isPresent() ? String.format(Locale.ROOT, "%.2f", score.getAsDouble()) : "");
        columns.add(judgment.map(Judgment::word).orElse(""));
        columns.add(named.map(KonceptRef::toString).orElse(""));
        columns.add(rater.map(MatchRow::escape).orElse(""));
        int last = columns.size();
        while (last > 2 && columns.get(last - 1).isEmpty()) {
            last--;
        }
        return String.join(SEPARATOR, columns.subList(0, last)).stripTrailing();
    }

    /**
     * Reads a row a match table holds.
     *
     * @param line the row's line
     * @return the row
     * @throws IllegalArgumentException if the line is not a row
     */
    public static MatchRow parse(String line) {
        List<String> columns = split(line);
        if (columns.size() > COLUMNS) {
            throw new IllegalArgumentException("a row has at most " + COLUMNS + " columns: " + line);
        }
        while (columns.size() < COLUMNS) {
            columns.add("");
        }
        Matcher anchor = ANCHOR.matcher(columns.get(0));
        if (!anchor.matches()) {
            throw new IllegalArgumentException("a row begins with sentence[from,to]: " + line);
        }
        List<KonceptRef> candidates = new ArrayList<>();
        Matcher token = KONCEPT.matcher(columns.get(2));
        int at = 0;
        while (token.find()) {
            if (!columns.get(2).substring(at, token.start()).isBlank()) {
                throw new IllegalArgumentException("the candidates are k: tokens: " + line);
            }
            candidates.add(KonceptRef.parse(token.group()));
            at = token.end();
        }
        if (!columns.get(2).substring(at).isBlank()) {
            throw new IllegalArgumentException("the candidates are k: tokens: " + line);
        }
        String stage = columns.get(3);
        String score = columns.get(4);
        String judgment = columns.get(5);
        String named = columns.get(6);
        String rater = columns.get(7);
        try {
            return new MatchRow(anchor.group(1), Integer.parseInt(anchor.group(2)), Integer.parseInt(anchor.group(3)),
                    unescape(columns.get(1)), candidates,
                    stage.isEmpty() ? Optional.empty() : Optional.of(Stage.of(stage)),
                    score.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(score)),
                    judgment.isEmpty() ? Optional.empty() : Optional.of(Judgment.of(judgment)),
                    named.isEmpty() ? Optional.empty() : Optional.of(KonceptRef.parse(named)),
                    rater.isEmpty() ? Optional.empty() : Optional.of(unescape(rater)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number in the row: " + line, e);
        }
    }

    /** Splits a row at the bars that separate its columns: not an escaped bar, not one inside a k: token. */
    private static List<String> split(String line) {
        List<String> columns = new ArrayList<>(COLUMNS);
        StringBuilder column = new StringBuilder();
        Matcher token = KONCEPT.matcher(line);
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                column.append(c).append(line.charAt(i + 1));
                i += 2;
            } else if (c == 'k' && token.find(i) && token.start() == i) {
                column.append(token.group());
                i = token.end();
            } else if (c == '|') {
                columns.add(column.toString().strip());
                column.setLength(0);
                i++;
            } else {
                column.append(c);
                i++;
            }
        }
        columns.add(column.toString().strip());
        return columns;
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '|' || c == '[' || c == ']') {
                out.append('\\');
            }
            out.append(c == '\n' ? ' ' : c);
        }
        return out.toString();
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i++;
                c = text.charAt(i);
            }
            out.append(c);
        }
        return out.toString();
    }
}
