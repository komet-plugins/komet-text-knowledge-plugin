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
package network.ike.komet.text.recognize;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The knowledge base's descriptions compiled into two Lucene synonym maps, from a description's
 * sequence of words to the concept it names. Descriptions whose case is not significant go in a
 * map matched with case folded; descriptions whose case is significant go in a second map matched
 * as written. Descriptions and text pass through the same tokenizer, so they are normalized alike.
 *
 * <p>Two kinds of description are left out, since they would match ordinary text wherever it uses
 * their words ({@link Omission}). A description made only of function words, such as "and", "in"
 * or "is a", is left out unless its case is significant and it is written with a capital, as the
 * operator "AND" is, which prose does not write; the function words are Lucene's English stop
 * words. A description made only of numbers, such as "1" or "+1", is left out always, since the
 * tokenizer drops the sign and it would match every such number; numbers are read with their units
 * by the typed spans. The descriptions left out are kept, with the reason, for the report.
 *
 * <p>Each map's output for a sequence of words is a token naming a concept's nid; when the
 * descriptions of several concepts share their words, the map holds every one of them, and the
 * recognizer offers them all as candidates. The descriptions are sorted before they are added, so
 * the same descriptions always build the same maps and the candidates come out in the same order.
 */
public final class DescriptionMaps {

    /** The prefix of the token that names a concept in a map's output. */
    static final String CONCEPT_TOKEN_PREFIX = "concept:";

    /** The function words: a description made only of these is left out unless written with a capital and case significant. */
    static final CharArraySet FUNCTION_WORDS = EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

    /** The type the standard tokenizer gives a word made of digits, with any decimal point or separator. */
    static final String NUMBER_TYPE = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.NUM];

    private final SynonymMap folded;
    private final SynonymMap caseSignificant;
    private final Statistics statistics;
    private final List<Omitted> omitted;
    private final List<Description> included;

    /**
     * What building the maps took and gave.
     *
     * @param descriptions    descriptions offered to the maps
     * @param folded          descriptions added to the map matched with case folded
     * @param caseSignificant descriptions added to the map matched as written
     * @param skipped         descriptions whose words analyze to nothing, such as punctuation alone
     * @param functionWords   descriptions left out because they are made only of function words
     * @param numbers         descriptions left out because they are made only of numbers
     * @param ramBytes        bytes held by the two maps' finite-state transducers
     * @param buildMillis     milliseconds taken to build both maps
     */
    public record Statistics(int descriptions, int folded, int caseSignificant, int skipped, int functionWords,
                             int numbers, long ramBytes, long buildMillis) {
    }

    private DescriptionMaps(SynonymMap folded, SynonymMap caseSignificant, Statistics statistics,
                            List<Omitted> omitted, List<Description> included) {
        this.folded = folded;
        this.caseSignificant = caseSignificant;
        this.statistics = statistics;
        this.omitted = List.copyOf(omitted);
        this.included = List.copyOf(included);
    }

    /**
     * Builds the maps from descriptions.
     *
     * @param descriptions the descriptions to match; their order does not matter
     * @return the maps
     */
    public static DescriptionMaps build(List<Description> descriptions) {
        long began = System.nanoTime();
        List<Description> sorted = new ArrayList<>(descriptions);
        sorted.sort(Comparator.comparing(Description::matchText)
                .thenComparingLong(Description::conceptNid)
                .thenComparing(Description::kind));
        SynonymMap.Builder foldedBuilder = new SynonymMap.Builder(true);
        SynonymMap.Builder caseBuilder = new SynonymMap.Builder(true);
        CharsRefBuilder scratch = new CharsRefBuilder();
        int foldedCount = 0;
        int caseCount = 0;
        int skipped = 0;
        int functionWordCount = 0;
        int numberCount = 0;
        List<Omitted> omitted = new ArrayList<>();
        List<Description> included = new ArrayList<>();
        try (Analyzer foldedInput = inputAnalyzer(true); Analyzer caseInput = inputAnalyzer(false)) {
            for (Description description : sorted) {
                boolean significant = description.caseSignificant();
                List<String> words = words(significant ? caseInput : foldedInput, description.matchText());
                if (words.isEmpty()) {
                    skipped++;
                    continue;
                }
                if (hasDigit(description.matchText()) && onlyNumbers(foldedInput, description.matchText())) {
                    omitted.add(new Omitted(description, Omission.NUMBERS));
                    numberCount++;
                    continue;
                }
                if (onlyFunctionWords(foldedWords(foldedInput, words, significant))
                        && !(significant && hasCapital(description.matchText()))) {
                    omitted.add(new Omitted(description, Omission.FUNCTION_WORDS));
                    functionWordCount++;
                    continue;
                }
                CharsRef input = SynonymMap.Builder.join(words.toArray(new String[0]), scratch);
                CharsRef output = new CharsRef(conceptToken(description.conceptNid()));
                if (significant) {
                    caseBuilder.add(input, output, false);
                    caseCount++;
                } else {
                    foldedBuilder.add(input, output, false);
                    foldedCount++;
                }
                included.add(description);
            }
            SynonymMap foldedMap = foldedBuilder.build();
            SynonymMap caseMap = caseBuilder.build();
            long ramBytes = ramBytes(foldedMap) + ramBytes(caseMap);
            long buildMillis = (System.nanoTime() - began) / 1_000_000L;
            return new DescriptionMaps(foldedMap, caseMap,
                    new Statistics(sorted.size(), foldedCount, caseCount, skipped, functionWordCount, numberCount,
                            ramBytes, buildMillis), omitted, included);
        } catch (IOException e) {
            throw new UncheckedIOException("building the description maps", e);
        }
    }

    /**
     * The map matched with case folded.
     *
     * @return the map; its transducer is null when no description was added to it
     */
    SynonymMap folded() {
        return folded;
    }

    /**
     * The map matched as written.
     *
     * @return the map; its transducer is null when no description was added to it
     */
    SynonymMap caseSignificant() {
        return caseSignificant;
    }

    /**
     * What building the maps took and gave.
     *
     * @return the statistics
     */
    public Statistics statistics() {
        return statistics;
    }

    /**
     * The descriptions the maps hold: every description offered that was neither skipped nor left
     * out, which is what a fingerprint of the descriptions a run matched against is computed over.
     *
     * @return the descriptions held, in the order they were added
     */
    public List<Description> included() {
        return included;
    }

    /**
     * The descriptions left out of the maps, each with the reason.
     *
     * @return the descriptions left out, in the order they were considered
     */
    public List<Omitted> omitted() {
        return omitted;
    }

    private static List<String> foldedWords(Analyzer foldedInput, List<String> words, boolean significant)
            throws IOException {
        if (!significant) {
            return words;
        }
        return words(foldedInput, String.join(" ", words));
    }

    private static boolean hasCapital(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isUpperCase(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every word the standard tokenizer makes of a text is a number. A sign, a percent mark
     * or other punctuation around the digits makes no word of its own, so "+1" and "10%" are numbers.
     *
     * @param analyzer an analyzer whose tokenizer is the standard tokenizer
     * @param text     the text
     * @return true when the text has at least one word and every word is a number
     * @throws IOException if the analyzer fails
     */
    static boolean onlyNumbers(Analyzer analyzer, String text) throws IOException {
        boolean any = false;
        try (TokenStream stream = analyzer.tokenStream("description", text)) {
            TypeAttribute type = stream.addAttribute(TypeAttribute.class);
            stream.reset();
            boolean all = true;
            while (stream.incrementToken()) {
                any = true;
                if (!NUMBER_TYPE.equals(type.type())) {
                    all = false;
                }
            }
            stream.end();
            return any && all;
        }
    }

    /**
     * Whether every word is a function word.
     *
     * @param words the words of a description, case folded
     * @return true when the list is not empty and holds only function words
     */
    static boolean onlyFunctionWords(List<String> words) {
        for (String word : words) {
            if (!FUNCTION_WORDS.contains(word)) {
                return false;
            }
        }
        return !words.isEmpty();
    }

    /**
     * The analyzer a description's words pass through before they are added to a map: the
     * standard tokenizer, then lower case unless the description's case is significant. The
     * recognizer reads text through the same tokenizer and the same folding.
     *
     * @param fold whether to fold case
     * @return the analyzer; the caller closes it
     */
    static Analyzer inputAnalyzer(boolean fold) {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new StandardTokenizer();
                TokenStream stream = fold ? new LowerCaseFilter(source) : source;
                return new TokenStreamComponents(source, stream);
            }
        };
    }

    /**
     * The words an analyzer makes of a text.
     *
     * @param analyzer the analyzer
     * @param text     the text
     * @return the words, in order
     * @throws IOException if the analyzer fails
     */
    static List<String> words(Analyzer analyzer, String text) throws IOException {
        List<String> words = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("description", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                if (term.length() > 0) {
                    words.add(term.toString());
                }
            }
            stream.end();
        }
        return words;
    }

    /**
     * The token naming a concept in a map's output.
     *
     * @param conceptNid the concept's nid
     * @return the token
     */
    static String conceptToken(long conceptNid) {
        return CONCEPT_TOKEN_PREFIX + conceptNid;
    }

    /**
     * The concept a map's output token names.
     *
     * @param token a token made by {@link #conceptToken(long)}
     * @return the concept's nid
     * @throws IllegalArgumentException if the token does not name a concept
     */
    static long conceptNid(String token) {
        if (!token.startsWith(CONCEPT_TOKEN_PREFIX)) {
            throw new IllegalArgumentException("not a concept token: " + token);
        }
        return Long.parseLong(token.substring(CONCEPT_TOKEN_PREFIX.length()));
    }

    private static long ramBytes(SynonymMap map) {
        return map.fst == null ? 0L : map.fst.ramBytesUsed();
    }
}
