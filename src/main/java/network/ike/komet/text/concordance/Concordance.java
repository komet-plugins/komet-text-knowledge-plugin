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

import network.ike.komet.text.recognize.Candidate;
import network.ike.komet.text.recognize.DescriptionMaps;
import network.ike.komet.text.recognize.Reading;
import network.ike.komet.text.recognize.Recognition;
import network.ike.komet.text.recognize.Stage;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Gathers the readings of a collection's sentences into a concordance report: how much of the
 * collection the knowledge base accounts for, which stages and which parts of the hierarchy the
 * references come from, and the gap list, the runs of words no description covers, ranked by how
 * often each occurs.
 *
 * <p>Precision and recall need judgments, which this stage does not yet have; the report gives
 * the counts they will be computed from.
 */
public final class Concordance {

    private static final CharArraySet STOP_WORDS = EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

    private final LongFunction<String> branchOf;
    private final Set<String> documents = new LinkedHashSet<>();
    private final Map<NotRead, Integer> notRead = new EnumMap<>(NotRead.class);
    private final Map<Stage, Integer> byStage = new EnumMap<>(Stage.class);
    private final Map<String, Integer> byBranch = new HashMap<>();
    private final Map<String, Integer> gaps = new HashMap<>();
    private int sentences;
    private int sentencesWithReference;
    private int recognitions;
    private int ambiguous;

    /**
     * A count with what it counts.
     *
     * @param label what is counted
     * @param count how many
     */
    public record Count(String label, int count) {
    }

    /**
     * The concordance of a collection.
     *
     * @param documents              documents read
     * @param notRead                files of a repository not read from the store, by why
     * @param sentences              sentences read
     * @param sentencesWithReference sentences with at least one recognized reference
     * @param recognitions           stretches of words that may name a concept
     * @param ambiguous              recognitions with more than one candidate
     * @param byStage                recognitions by the stage of their first candidate
     * @param byBranch               recognitions by the part of the hierarchy of their first candidate, most first
     * @param gapList                runs of words no description covers, most frequent first
     * @param maps                   what building the description maps took and gave
     * @param readMillis             milliseconds spent reading the sentences
     */
    public record Report(int documents, Map<NotRead, Integer> notRead, int sentences, int sentencesWithReference,
                         int recognitions, int ambiguous, Map<Stage, Integer> byStage, List<Count> byBranch,
                         List<Count> gapList, DescriptionMaps.Statistics maps, long readMillis) {

        /**
         * Copies the report's collections.
         */
        public Report {
            notRead = Map.copyOf(notRead);
            byStage = Map.copyOf(byStage);
            byBranch = List.copyOf(byBranch);
            gapList = List.copyOf(gapList);
        }

        /**
         * The share of sentences with at least one recognized reference.
         *
         * @return sentences with a reference over sentences read, or zero when none was read
         */
        public double coverage() {
            return sentences == 0 ? 0.0 : (double) sentencesWithReference / sentences;
        }

        /**
         * Sentences read per second.
         *
         * @return the rate, or zero when no time was measured
         */
        public double sentencesPerSecond() {
            return readMillis == 0 ? 0.0 : sentences * 1000.0 / readMillis;
        }
    }

    /**
     * A concordance that names each recognition's part of the hierarchy with a function.
     *
     * @param branchOf the part of the hierarchy a concept is under, by the concept's nid
     */
    public Concordance(LongFunction<String> branchOf) {
        this.branchOf = Objects.requireNonNull(branchOf, "branchOf");
    }

    /**
     * Adds the reading of one sentence.
     *
     * @param sentence the sentence
     * @param reading  what the recognizer found in it
     */
    public void add(Sentence sentence, Reading reading) {
        documents.add(sentence.document());
        sentences++;
        if (reading.hasReference()) {
            sentencesWithReference++;
        }
        for (Recognition recognition : reading.recognitions()) {
            recognitions++;
            if (recognition.ambiguous()) {
                ambiguous++;
            }
            Candidate first = recognition.candidates().getFirst();
            byStage.merge(first.stage(), 1, Integer::sum);
            byBranch.merge(branchOf.apply(first.conceptNid()), 1, Integer::sum);
        }
        for (String gap : gapRuns(reading)) {
            gaps.merge(gap, 1, Integer::sum);
        }
    }

    /**
     * Counts a file of a repository that was not read from the store.
     *
     * @param why why it was not read
     */
    public void notRead(NotRead why) {
        notRead.merge(Objects.requireNonNull(why, "why"), 1, Integer::sum);
    }

    /**
     * The report so far.
     *
     * @param maps       what building the description maps took and gave
     * @param readMillis milliseconds spent reading the sentences
     * @param gapLimit   how many entries of the gap list to keep
     * @return the report
     */
    public Report report(DescriptionMaps.Statistics maps, long readMillis, int gapLimit) {
        return new Report(documents.size(), notRead, sentences, sentencesWithReference, recognitions, ambiguous,
                byStage, ranked(byBranch, Integer.MAX_VALUE), ranked(gaps, gapLimit), maps, readMillis);
    }

    /**
     * The runs of words in a reading that no recognition covers: consecutive uncovered words
     * separated only by spaces or hyphens, broken at a stop word, a number, or any other character.
     *
     * @param reading the reading
     * @return the runs, case folded, in order
     */
    static List<String> gapRuns(Reading reading) {
        List<String> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int previousEnd = -1;
        for (Reading.Word word : reading.uncovered()) {
            boolean breaks = STOP_WORDS.contains(word.text()) || isNumber(word.text());
            boolean adjacent = previousEnd >= 0 && onlySpacesOrHyphens(reading.text(), previousEnd, word.start());
            if (breaks || !adjacent) {
                end(run, runs);
            }
            if (!breaks) {
                if (!run.isEmpty()) {
                    run.append(' ');
                }
                run.append(word.text().toLowerCase(Locale.ROOT));
            }
            previousEnd = word.end();
        }
        end(run, runs);
        return runs;
    }

    /**
     * The report as AsciiDoc.
     *
     * @param report the report
     * @param title  the report's title
     * @return the AsciiDoc text
     */
    public static String asciiDoc(Report report, String title) {
        StringBuilder out = new StringBuilder();
        out.append("= ").append(title).append("\n\n");
        out.append("[cols=\"3,1\"]\n|===\n");
        row(out, "Documents", Integer.toString(report.documents()));
        if (!report.notRead().isEmpty()) {
            row(out, "Files not read (assemblies, not committed, no stored sentences)",
                    report.notRead().getOrDefault(NotRead.ASSEMBLY, 0) + ", "
                            + report.notRead().getOrDefault(NotRead.NOT_COMMITTED, 0) + ", "
                            + report.notRead().getOrDefault(NotRead.NO_STORED_SENTENCES, 0));
        }
        row(out, "Sentences", Integer.toString(report.sentences()));
        row(out, "Sentences with a reference", report.sentencesWithReference() + " ("
                + String.format(Locale.ROOT, "%.1f", 100.0 * report.coverage()) + "%)");
        row(out, "Recognitions", Integer.toString(report.recognitions()));
        row(out, "Recognitions with more than one candidate", Integer.toString(report.ambiguous()));
        row(out, "Descriptions in the maps (folded, case significant)", report.maps().folded() + ", "
                + report.maps().caseSignificant());
        row(out, "Descriptions left out (only function words, only numbers, nothing to match)",
                report.maps().functionWords() + ", " + report.maps().numbers() + ", " + report.maps().skipped());
        row(out, "Time to build the maps", report.maps().buildMillis() + " ms");
        row(out, "Memory held by the maps", String.format(Locale.ROOT, "%.1f MiB", report.maps().ramBytes() / 1048576.0));
        row(out, "Sentences read per second", String.format(Locale.ROOT, "%.0f", report.sentencesPerSecond()));
        out.append("|===\n\n== By stage\n\n[cols=\"3,1\"]\n|===\n");
        for (Stage stage : Stage.values()) {
            row(out, stage.name(), Integer.toString(report.byStage().getOrDefault(stage, 0)));
        }
        out.append("|===\n\n== By part of the hierarchy\n\n[cols=\"3,1\"]\n|===\n");
        for (Count count : report.byBranch()) {
            row(out, count.label(), Integer.toString(count.count()));
        }
        out.append("|===\n\n== Gap list\n\nRuns of words no description covers, most frequent first.\n\n[cols=\"3,1\"]\n|===\n");
        for (Count count : report.gapList()) {
            row(out, count.label(), Integer.toString(count.count()));
        }
        out.append("|===\n");
        return out.toString();
    }

    private static void row(StringBuilder out, String label, String value) {
        out.append("| ").append(label.replace("|", "\\|")).append(" | ").append(value).append('\n');
    }

    private static List<Count> ranked(Map<String, Integer> counts, int limit) {
        List<Count> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ranked.add(new Count(entry.getKey(), entry.getValue()));
        }
        ranked.sort(Comparator.comparingInt(Count::count).reversed().thenComparing(Count::label));
        return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
    }

    private static void end(StringBuilder run, List<String> runs) {
        if (!run.isEmpty()) {
            runs.add(run.toString());
            run.setLength(0);
        }
    }

    private static boolean onlySpacesOrHyphens(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != ',') {
                return false;
            }
        }
        return !word.isEmpty();
    }
}
