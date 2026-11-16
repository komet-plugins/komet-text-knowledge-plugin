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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The description stage on descriptions written for the test, with no store: longest match,
 * shared words, case significance, semantic tags, offsets and determinism.
 */
class RecognizerTest {

    private static final long CHEST_PAIN = 101L;
    private static final long CHEST_PAIN_ON_EXERTION = 102L;
    private static final long COMMON_COLD = 103L;
    private static final long COLD_SENSATION = 104L;
    private static final long AIDS = 105L;
    private static final long HEPATIC_IMPAIRMENT = 106L;
    private static final long PUPIL = 107L;
    private static final long METFORMIN = 108L;
    private static final long SET_AND = 109L;
    private static final long EL_AND = 110L;
    private static final long MEASURE_WITHIN = 111L;
    private static final long PLUS_ONE = 112L;
    private static final long ONE = 113L;
    private static final long GRADE_ONE = 114L;
    private static final long TEN_PERCENT = 115L;

    private static List<Description> descriptions() {
        return List.of(
                new Description(CHEST_PAIN, "Chest pain", DescriptionKind.REGULAR_NAME, false),
                new Description(CHEST_PAIN, "Chest pain (finding)", DescriptionKind.FULLY_QUALIFIED_NAME, false),
                new Description(CHEST_PAIN_ON_EXERTION, "Chest pain on exertion", DescriptionKind.REGULAR_NAME, false),
                new Description(COMMON_COLD, "Cold", DescriptionKind.REGULAR_NAME, false),
                new Description(COLD_SENSATION, "Cold", DescriptionKind.REGULAR_NAME, false),
                new Description(AIDS, "AIDS", DescriptionKind.REGULAR_NAME, true),
                new Description(HEPATIC_IMPAIRMENT, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false),
                new Description(PUPIL, "Pupil (body structure)", DescriptionKind.FULLY_QUALIFIED_NAME, false),
                new Description(METFORMIN, "Metformin", DescriptionKind.REGULAR_NAME, false),
                new Description(SET_AND, "and", DescriptionKind.REGULAR_NAME, false),
                new Description(EL_AND, "AND", DescriptionKind.REGULAR_NAME, true),
                new Description(MEASURE_WITHIN, "in", DescriptionKind.REGULAR_NAME, true),
                new Description(PLUS_ONE, "+1", DescriptionKind.REGULAR_NAME, false),
                new Description(ONE, "1", DescriptionKind.REGULAR_NAME, true),
                new Description(GRADE_ONE, "Grade 1", DescriptionKind.REGULAR_NAME, false),
                new Description(TEN_PERCENT, "10%", DescriptionKind.REGULAR_NAME, false));
    }

    private static Reading read(String text) {
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(descriptions()))) {
            return recognizer.read(text);
        }
    }

    private static List<Long> concepts(Recognition recognition) {
        return recognition.candidates().stream().map(Candidate::conceptNid).toList();
    }

    @Test
    void theLongestDescriptionWins() {
        Reading reading = read("Denies chest pain on exertion.");
        assertEquals(1, reading.recognitions().size());
        Recognition recognition = reading.recognitions().getFirst();
        assertEquals("chest pain on exertion", recognition.words());
        assertEquals(List.of(CHEST_PAIN_ON_EXERTION), concepts(recognition));
    }

    @Test
    void aShorterDescriptionMatchesWhereTheLongerDoesNot() {
        Reading reading = read("Chest pain at rest.");
        assertEquals(1, reading.recognitions().size());
        assertEquals("Chest pain", reading.recognitions().getFirst().words());
        assertEquals(List.of(CHEST_PAIN), concepts(reading.recognitions().getFirst()));
    }

    @Test
    void wordsSeveralConceptsShareGiveEveryCandidate() {
        Recognition recognition = read("She has a cold.").recognitions().getFirst();
        assertEquals("cold", recognition.words());
        assertEquals(List.of(COMMON_COLD, COLD_SENSATION), concepts(recognition));
        assertTrue(recognition.ambiguous());
    }

    @Test
    void aCaseSignificantDescriptionMatchesOnlyAsWritten() {
        assertTrue(read("Uses aids to daily living.").recognitions().isEmpty());
        assertEquals(List.of(AIDS), concepts(read("History of AIDS.").recognitions().getFirst()));
    }

    @Test
    void caseIsFoldedWhereItIsNotSignificant() {
        assertEquals(List.of(HEPATIC_IMPAIRMENT), concepts(read("HEPATIC IMPAIRMENT").recognitions().getFirst()));
        assertEquals(List.of(HEPATIC_IMPAIRMENT),
                concepts(read("Contraindicated in severe hepatic impairment.").recognitions().getFirst()));
    }

    @Test
    void aFullyQualifiedNameMatchesWithoutItsSemanticTag() {
        assertEquals(List.of(PUPIL), concepts(read("The left pupil is round.").recognitions().getFirst()));
        assertEquals("Pupil", new Description(PUPIL, "Pupil (body structure)", DescriptionKind.FULLY_QUALIFIED_NAME,
                false).matchText());
        assertEquals("Pupil", Description.withoutSemanticTag("Pupil"));
    }

    @Test
    void offsetsGiveTheWordsAndUncoveredWordsAreTheRest() {
        String text = "Metformin 500 mg, contraindicated in hepatic impairment.";
        Reading reading = read(text);
        for (Recognition recognition : reading.recognitions()) {
            assertEquals(recognition.words(), text.substring(recognition.start(), recognition.end()));
        }
        assertEquals(List.of(METFORMIN, HEPATIC_IMPAIRMENT),
                reading.recognitions().stream().map(r -> r.candidates().getFirst().conceptNid()).toList());
        assertEquals(List.of("500", "mg", "contraindicated", "in"),
                reading.uncovered().stream().map(Reading.Word::text).toList());
    }

    @Test
    void theSameDescriptionsInAnyOrderGiveTheSameReading() {
        String text = "Cold, chest pain on exertion and AIDS; hepatic impairment noted.";
        Reading expected = read(text);
        List<Description> shuffled = new ArrayList<>(descriptions());
        Collections.shuffle(shuffled, new Random(1372L));
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(shuffled))) {
            assertEquals(expected, recognizer.read(text));
            assertEquals(expected, recognizer.read(text));
        }
    }

    @Test
    void aDescriptionOfFunctionWordsAloneMatchesOnlyWhereItsCaseIsSignificant() {
        assertTrue(read("Nausea and vomiting").recognitions().isEmpty());
        assertTrue(read("Contraindicated in pregnancy").recognitions().isEmpty());
        assertEquals(List.of(EL_AND), concepts(read("A AND B").recognitions().getFirst()));
        assertEquals(List.of(SET_AND, MEASURE_WITHIN), omitted(Omission.FUNCTION_WORDS));
    }

    @Test
    void aDescriptionOfNumbersAloneIsLeftOutWhateverItsCase() {
        Reading reading = read("1 of 10 patients took 1 tablet, 10% of the dose; +1 edema.");
        assertTrue(reading.recognitions().isEmpty(), "no number alone is recognized: " + reading);
        assertEquals(List.of(PLUS_ONE, ONE, TEN_PERCENT), omitted(Omission.NUMBERS));
    }

    @Test
    void aDescriptionWithWordsBesideItsNumberStaysIn() {
        Recognition recognition = read("A grade 1 tumor.").recognitions().getFirst();
        assertEquals("grade 1", recognition.words());
        assertEquals(List.of(GRADE_ONE), concepts(recognition));
    }

    @Test
    void theMapsReportWhatTheyHold() {
        DescriptionMaps.Statistics statistics = DescriptionMaps.build(descriptions()).statistics();
        assertEquals(16, statistics.descriptions());
        assertEquals(9, statistics.folded());
        assertEquals(2, statistics.caseSignificant());
        assertEquals(0, statistics.skipped());
        assertEquals(2, statistics.functionWords());
        assertEquals(3, statistics.numbers());
        assertTrue(statistics.ramBytes() > 0);
    }

    /** The concepts whose descriptions the maps leave out for a reason, in the order they were considered. */
    private static List<Long> omitted(Omission why) {
        return DescriptionMaps.build(descriptions()).omitted().stream()
                .filter(omitted -> omitted.why() == why)
                .map(omitted -> omitted.description().conceptNid())
                .toList();
    }
}
