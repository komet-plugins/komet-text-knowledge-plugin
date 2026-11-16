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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The description stage: reads a text through the same tokenizer the descriptions passed through,
 * then through Lucene's {@code SynonymGraphFilter}, which keeps the longest description that
 * matches at each word and gives the concepts it names with the character offsets of the words
 * matched. The text is read twice, once with case folded against the map of descriptions whose
 * case is not significant, and once as written against the map of descriptions whose case is.
 *
 * <p>The same maps and the same text always give the same reading. A recognizer may be shared
 * between threads: Lucene's analyzers keep their token streams per thread.
 */
public final class Recognizer implements AutoCloseable {

    private final Analyzer folded;
    private final Analyzer caseSignificant;

    /**
     * A recognizer over the given maps.
     *
     * @param maps the description maps to match
     */
    public Recognizer(DescriptionMaps maps) {
        this.folded = readingAnalyzer(maps.folded(), true);
        this.caseSignificant = readingAnalyzer(maps.caseSignificant(), false);
    }

    /**
     * Reads a text.
     *
     * @param text the text, typically one sentence
     * @return the stretches that may name a concept and the words none covers
     */
    public Reading read(String text) {
        Map<Span, Set<Long>> found = new LinkedHashMap<>();
        List<Reading.Word> words = new ArrayList<>();
        try {
            pass(folded, text, found, words);
            pass(caseSignificant, text, found, null);
        } catch (IOException e) {
            throw new UncheckedIOException("reading a text", e);
        }
        List<Recognition> recognitions = new ArrayList<>();
        for (Map.Entry<Span, Set<Long>> entry : found.entrySet()) {
            Span span = entry.getKey();
            List<Candidate> candidates = new ArrayList<>();
            for (Long conceptNid : entry.getValue()) {
                candidates.add(new Candidate(conceptNid, Stage.DESCRIPTION));
            }
            recognitions.add(new Recognition(span.start(), span.end(), text.substring(span.start(), span.end()),
                    candidates));
        }
        recognitions.sort(Comparator.comparingInt(Recognition::start)
                .thenComparing(Comparator.comparingInt(Recognition::end).reversed()));
        List<Reading.Word> uncovered = new ArrayList<>();
        for (Reading.Word word : words) {
            if (!covered(word, recognitions)) {
                uncovered.add(word);
            }
        }
        return new Reading(text, recognitions, uncovered);
    }

    /**
     * Releases the analyzers' resources.
     */
    @Override
    public void close() {
        folded.close();
        caseSignificant.close();
    }

    private static void pass(Analyzer analyzer, String text, Map<Span, Set<Long>> found, List<Reading.Word> words)
            throws IOException {
        try (TokenStream stream = analyzer.tokenStream("text", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
            TypeAttribute type = stream.addAttribute(TypeAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                if (SynonymGraphFilter.TYPE_SYNONYM.equals(type.type())) {
                    Span span = new Span(offset.startOffset(), offset.endOffset());
                    found.computeIfAbsent(span, ignored -> new LinkedHashSet<>())
                            .add(DescriptionMaps.conceptNid(term.toString()));
                } else if (words != null) {
                    words.add(new Reading.Word(offset.startOffset(), offset.endOffset(), term.toString()));
                }
            }
            stream.end();
        }
    }

    private static boolean covered(Reading.Word word, List<Recognition> recognitions) {
        for (Recognition recognition : recognitions) {
            if (word.start() >= recognition.start() && word.end() <= recognition.end()) {
                return true;
            }
        }
        return false;
    }

    private static Analyzer readingAnalyzer(SynonymMap map, boolean fold) {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new StandardTokenizer();
                TokenStream stream = fold ? new LowerCaseFilter(source) : source;
                if (map.fst != null) {
                    stream = new SynonymGraphFilter(stream, map, false);
                }
                return new TokenStreamComponents(source, stream);
            }
        };
    }

    /** A stretch of the text, end exclusive. */
    private record Span(int start, int end) {
    }
}
