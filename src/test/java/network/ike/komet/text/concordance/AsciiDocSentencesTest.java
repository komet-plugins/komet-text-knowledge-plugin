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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading the sentences of an AsciiDoc document without a store: what is read and what is not,
 * where sentences end, and the markup left out.
 */
class AsciiDocSentencesTest {

    @Test
    void readsProseTitlesItemsAndCellsAndSkipsTheRest() {
        List<String> lines = List.of(
                "// a comment",
                ":topic-id: example",
                "= Contraindications",
                "",
                "[[anchor]]",
                "Metformin is contraindicated in severe hepatic impairment.",
                "It is also contraindicated in *metabolic acidosis*. See xref:other[the warnings].",
                "",
                "----",
                "code that is not prose",
                "----",
                "",
                ".Adverse reactions",
                "* Nausea in 12% of patients",
                "|===",
                "| Diarrhea | 9%",
                "|===",
                "include::other.adoc[]",
                "The k:uuid=0000[pupil] is round.");
        List<Sentence> sentences = AsciiDocSentences.read(lines, "label.adoc");
        assertEquals(List.of(
                        "Contraindications",
                        "Metformin is contraindicated in severe hepatic impairment.",
                        "It is also contraindicated in metabolic acidosis.",
                        "See the warnings.",
                        "Adverse reactions",
                        "Nausea in 12% of patients",
                        "Diarrhea",
                        "9%",
                        "The pupil is round."),
                sentences.stream().map(Sentence::text).toList());
        assertEquals("Contraindications", sentences.get(1).section());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), sentences.stream().map(Sentence::index).toList());
    }

    @Test
    void anAttributeEntryContinuedOverLinesIsSkippedWhole() {
        assertEquals(List.of("Title", "Prose follows."), texts(List.of(
                ":topic-id: example",
                ":topic-scope-note: A two-page introduction for a reader \\",
                "  outside clinical informatics. For the full treatment, \\",
                "  see the statement structure.",
                "",
                "= Title",
                "",
                "Prose follows.")));
    }

    @Test
    void sentencesEndWhereTheStoreEndsThem() {
        assertEquals(List.of(
                        "Reverse must reconstruct FamilyMemberHistory vs. Observation with focus extension.",
                        "\"Dr. Martinez did not answer my page.\"",
                        "No NP-hard satisfiability.",
                        "The determination is a value on a scale."),
                texts(List.of(
                        "Reverse must reconstruct FamilyMemberHistory vs. Observation with focus extension.",
                        "\"Dr. Martinez did not answer my page.\"",
                        "",
                        ". **No NP-hard satisfiability.** The determination is a value on a scale.")));
    }

    @Test
    void aCellHoldingTwoSentencesIsSplit() {
        assertEquals(List.of(
                        "subjectOfRecord",
                        "The patient, or more precisely the patient's clinical record.",
                        "This attribute is always the patient."),
                texts(List.of(
                        "|===",
                        "| subjectOfRecord | The patient, or more precisely the patient's clinical record. This attribute is always the patient.",
                        "|===")));
    }

    @Test
    void aBibliographyEntryIsReadWithoutItsAnchor() {
        assertEquals(List.of("Engineering a Safer World, MIT Press, 2012."),
                texts(List.of("* [[[leveson2012]]] _Engineering a Safer World_, MIT Press, 2012.")));
    }

    @Test
    void aMatchTableIsNotRead() {
        assertEquals(List.of("Prose."), texts(List.of(
                "Prose.",
                "",
                "[matches,run=\"2026-11-14T22:10:00Z\",matcher=\"development\",stages=\"description 1\",descriptions=\"sha256:00\"]",
                "----",
                "0f1c2d3e-4f50-4617-8899-aabbccddeeff[0,5] | Prose | k:uuid=6111dc03-0000-4000-8000-000000000001[Prose] | description",
                "----")));
    }

    @Test
    void cleanRemovesInlineMarkup() {
        assertEquals("Hepatic impairment", AsciiDocSentences.clean("indexterm:[liver]((Hepatic impairment))"));
        assertEquals("see the label", AsciiDocSentences.clean("see https://example.org/x[the label]"));
        assertEquals("a b", AsciiDocSentences.clean("a <<ref>> b"));
    }

    private static List<String> texts(List<String> lines) {
        return AsciiDocSentences.read(lines, "doc.adoc").stream().map(Sentence::text).toList();
    }
}
