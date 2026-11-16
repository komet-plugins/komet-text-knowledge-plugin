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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The matches of one phrase to the same concepts, as a match table is read: a table holds a row
 * for each match, which a reader cannot judge one at a time, and one entry gathers the rows a
 * single judgment of the phrase would settle. The rows stay as they are stored; only the reading
 * groups them.
 *
 * <p>A phrase is its words with case and runs of white space set aside, so {@code Concept} at a
 * sentence's head and {@code concept} within one are one phrase. Its concepts are the candidates
 * the matcher proposed, or, for a match the matcher missed, the concept the rater named. A judged
 * row keeps its candidates, so judging a match never moves it to another entry.
 *
 * <p>Entries are numbered from 1, the phrase matched most often first and phrases matched as
 * often in the order the document first has them, so that a reader can name one, and one of its
 * matches as {@code 5.2}. The numbers are the reading's, not the table's: a rerun may number the
 * same phrase otherwise.
 *
 * @param number   the entry's number, from 1
 * @param phrase   the phrase as its first match writes it
 * @param concepts the concepts the phrase was matched to, the first the one proposed
 * @param rows     the phrase's matches, in document order
 */
public record PhraseEntry(int number, String phrase, List<KonceptRef> concepts, List<MatchRow> rows) {

    public PhraseEntry {
        if (number < 1) {
            throw new IllegalArgumentException("an entry is numbered from 1: " + number);
        }
        Objects.requireNonNull(phrase, "phrase");
        concepts = List.copyOf(concepts);
        rows = List.copyOf(rows);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("an entry holds at least one match: " + phrase);
        }
    }

    /**
     * Groups a table's rows by phrase and concepts, numbered.
     *
     * @param rows the table's rows, in document order
     * @return the entries, numbered from 1
     */
    public static List<PhraseEntry> of(List<MatchRow> rows) {
        Map<Key, List<MatchRow>> grouped = new LinkedHashMap<>();
        for (MatchRow row : rows) {
            grouped.computeIfAbsent(Key.of(row), unused -> new ArrayList<>()).add(row);
        }
        // a stable sort keeps phrases matched as often in the order the document first has them
        List<List<MatchRow>> ordered = new ArrayList<>(grouped.values());
        ordered.sort(Comparator.comparingInt((List<MatchRow> group) -> group.size()).reversed());
        List<PhraseEntry> entries = new ArrayList<>(ordered.size());
        for (List<MatchRow> group : ordered) {
            MatchRow first = group.getFirst();
            entries.add(new PhraseEntry(entries.size() + 1, first.words(), matchedTo(first), group));
        }
        return List.copyOf(entries);
    }

    /**
     * How many of the entry's matches a rater has judged.
     *
     * @return the count
     */
    public int judged() {
        return (int) rows.stream().filter(row -> row.judgment().isPresent()).count();
    }

    /**
     * The number a match of this entry is named by.
     *
     * @param index the match's index among the entry's rows, from 0
     * @return {@code 5.2} for the second match of entry 5
     */
    public String numberOf(int index) {
        Objects.checkIndex(index, rows.size());
        return number + "." + (index + 1);
    }

    /**
     * The phrase a row's words make: case and runs of white space set aside.
     *
     * @param words the words as written
     * @return the phrase
     */
    public static String phraseOf(String words) {
        return words.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** The concepts a row's phrase was matched to: its candidates, or what the rater named for a missed match. */
    private static List<KonceptRef> matchedTo(MatchRow row) {
        return row.candidates().isEmpty() ? row.named().stream().toList() : row.candidates();
    }

    /** What makes two rows one entry: the phrase and the concepts, by identifier. */
    private record Key(String phrase, List<UUID> concepts) {
        static Key of(MatchRow row) {
            return new Key(phraseOf(row.words()), matchedTo(row).stream().map(KonceptRef::uuid).toList());
        }
    }
}
