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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A match table's rows read by phrase: grouped, ordered and numbered. */
class PhraseEntryTest {

    private static final KonceptRef CONDITIONAL = new KonceptRef(UUID.fromString("65b11a15-ff49-5770-8915-b6d6914ad4d7"), "Conditional");
    private static final KonceptRef CONCEPT_KIND = new KonceptRef(UUID.fromString("517dda53-28b5-5ef0-a99c-2c607ecff3fc"), "Concept kind");
    private static final KonceptRef TEXT_A = new KonceptRef(UUID.fromString("8bdcbe5d-e92e-5c10-845e-b585e6061672"), "Text");
    private static final KonceptRef TEXT_B = new KonceptRef(UUID.fromString("515eab8a-335a-5a91-96df-7c29af71c507"), "Text");
    private static final KonceptRef TIME = new KonceptRef(UUID.fromString("13557c25-add3-50fe-aeb1-edb52befcd40"), "Time");

    private static MatchRow match(int sentence, String words, KonceptRef... candidates) {
        return MatchRow.proposed(new UUID(0, sentence).toString(), 0, words.length(), words, List.of(candidates),
                Stage.DESCRIPTION, OptionalDouble.empty());
    }

    @Test
    void aPhraseMatchedToTheSameConceptsIsOneEntryAndTheMostMatchedComesFirst() {
        MatchRow time = match(1, "time", TIME);
        MatchRow case1 = match(2, "case", CONDITIONAL);
        MatchRow concept1 = match(3, "Concept", CONCEPT_KIND);
        MatchRow case2 = match(4, "case", CONDITIONAL);
        MatchRow concept2 = match(5, "concept", CONCEPT_KIND);
        MatchRow case3 = match(6, "case", CONDITIONAL);
        MatchRow text = match(7, "text", TEXT_A, TEXT_B);

        List<PhraseEntry> entries = PhraseEntry.of(List.of(time, case1, concept1, case2, concept2, case3, text));

        assertEquals(List.of(1, 2, 3, 4), entries.stream().map(PhraseEntry::number).toList());
        assertEquals(new PhraseEntry(1, "case", List.of(CONDITIONAL), List.of(case1, case2, case3)), entries.get(0));
        assertEquals(new PhraseEntry(2, "Concept", List.of(CONCEPT_KIND), List.of(concept1, concept2)), entries.get(1),
                "case set aside; the phrase as its first match writes it");
        assertEquals(new PhraseEntry(3, "time", List.of(TIME), List.of(time)), entries.get(2),
                "matched as often as text, and first in the document");
        assertEquals(List.of(TEXT_A, TEXT_B), entries.get(3).concepts());
        assertEquals("1.3", entries.get(0).numberOf(2));
        assertThrows(IndexOutOfBoundsException.class, () -> entries.get(0).numberOf(3));
    }

    @Test
    void judgingAMatchKeepsItInItsEntry() {
        MatchRow first = match(1, "case", CONDITIONAL);
        MatchRow rejected = match(2, "case", CONDITIONAL).judged(Judgment.REJECT, Optional.empty(), Optional.of("KEC"));
        MatchRow refined = match(3, "case", CONDITIONAL).judged(Judgment.REFINE, Optional.of(TIME), Optional.of("KEC"));

        List<PhraseEntry> entries = PhraseEntry.of(List.of(first, rejected, refined));

        assertEquals(1, entries.size());
        assertEquals(2, entries.getFirst().judged());
        assertEquals(List.of(CONDITIONAL), entries.getFirst().concepts(), "the concepts proposed, not those named");
    }

    @Test
    void theSamePhraseMatchedToOtherConceptsIsAnotherEntry() {
        List<PhraseEntry> entries = PhraseEntry.of(List.of(match(1, "text", TEXT_A), match(2, "text", TEXT_A, TEXT_B),
                MatchRow.missed(new UUID(0, 3).toString(), 0, 4, "text", TEXT_B, Optional.of("KEC"))));

        assertEquals(3, entries.size());
        assertEquals(List.of(TEXT_B), entries.get(2).concepts(), "a missed match's concept is the one its rater named");
    }

    @Test
    void aPhraseSetsAsideCaseAndRunsOfSpace() {
        assertEquals("difference between", PhraseEntry.phraseOf(" Difference \n between "));
        MatchRow one = match(1, "case", CONDITIONAL);
        assertThrows(IllegalArgumentException.class, () -> new PhraseEntry(0, "case", List.of(CONDITIONAL), List.of(one)));
        assertThrows(IllegalArgumentException.class, () -> new PhraseEntry(1, "case", List.of(), List.of()));
    }
}
