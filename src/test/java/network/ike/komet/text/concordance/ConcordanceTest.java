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

import network.ike.komet.text.recognize.Description;
import network.ike.komet.text.recognize.DescriptionKind;
import network.ike.komet.text.recognize.DescriptionMaps;
import network.ike.komet.text.recognize.Recognizer;
import network.ike.komet.text.recognize.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The concordance report from readings of a small collection, with no store.
 */
class ConcordanceTest {

    private static final List<Description> DESCRIPTIONS = List.of(
            new Description(1L, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false),
            new Description(2L, "Metformin", DescriptionKind.REGULAR_NAME, false),
            new Description(3L, "Cold", DescriptionKind.REGULAR_NAME, false),
            new Description(4L, "Cold", DescriptionKind.REGULAR_NAME, false));

    @Test
    void countsCoverageStagesBranchesAndGaps() {
        DescriptionMaps maps = DescriptionMaps.build(DESCRIPTIONS);
        Concordance concordance = new Concordance(conceptNid -> conceptNid == 2L ? "Substance" : "Finding");
        List<Sentence> sentences = List.of(
                new Sentence("a.adoc", 0, "", "Metformin is contraindicated in hepatic impairment."),
                new Sentence("a.adoc", 1, "", "Lactic acidosis is rare."),
                new Sentence("b.adoc", 0, "", "A cold with lactic acidosis."));
        try (Recognizer recognizer = new Recognizer(maps)) {
            for (Sentence sentence : sentences) {
                concordance.add(sentence, recognizer.read(sentence.text()));
            }
        }
        Concordance.Report report = concordance.report(maps.statistics(), 10L, 10);
        assertEquals(2, report.documents());
        assertEquals(3, report.sentences());
        assertEquals(2, report.sentencesWithReference());
        assertEquals(3, report.recognitions());
        assertEquals(1, report.ambiguous());
        assertEquals(3, report.byStage().get(Stage.DESCRIPTION));
        assertEquals(List.of(new Concordance.Count("Finding", 2), new Concordance.Count("Substance", 1)),
                report.byBranch());
        assertEquals(new Concordance.Count("lactic acidosis", 2), report.gapList().getFirst());
        assertEquals(2.0 / 3.0, report.coverage(), 1e-9);
        assertTrue(Concordance.asciiDoc(report, "Test").contains("| lactic acidosis | 2"));
    }

    @Test
    void anEmptyCollectionGivesAReport() {
        Concordance.Report report = new Concordance(conceptNid -> "Finding")
                .report(DescriptionMaps.build(List.of()).statistics(), 0L, 10);
        assertEquals(0, report.sentences());
        assertEquals(0.0, report.coverage());
        assertTrue(Concordance.asciiDoc(report, "Empty").contains("| DESCRIPTION | 0"));
    }

    @Test
    void gapRunsBreakAtStopWordsNumbersAndPunctuation() {
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(DESCRIPTIONS))) {
            assertEquals(List.of("severe renal failure", "dose adjustment"),
                    Concordance.gapRuns(recognizer.read("Severe renal failure, the 500 dose-adjustment")));
        }
    }
}
