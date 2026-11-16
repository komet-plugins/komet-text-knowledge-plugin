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

/**
 * A match table's rows: their text form read back as written, and the rules a judgment keeps.
 */
class MatchRowTest {

    static final KonceptRef HEPATIC = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000001"), "Hepatic impairment");
    static final KonceptRef SEVERE = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000002"), "Severe hepatic impairment");
    static final KonceptRef ODD_LABEL = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000003"), "Impairment | liver, hepatic");
    static final String SENTENCE = "0f1c2d3e-4f50-4617-8899-aabbccddeeff";

    @Test
    void aProposedRowIsReadBackAsWritten() {
        MatchRow row = MatchRow.proposed(SENTENCE, 112, 130, "hepatic impairment", List.of(HEPATIC, ODD_LABEL),
                Stage.DESCRIPTION, OptionalDouble.of(1));
        assertEquals(SENTENCE + "[112,130] | hepatic impairment | " + HEPATIC + " " + ODD_LABEL + " | description | 1.00",
                row.text());
        assertEquals(row, MatchRow.parse(row.text()));
    }

    @Test
    void aJudgedRowCarriesItsJudgmentConceptAndRater() {
        MatchRow row = MatchRow.proposed(SENTENCE, 0, 7, "a | [b]\\", List.of(HEPATIC), Stage.DESCRIPTION, OptionalDouble.empty())
                .judged(Judgment.REFINE, Optional.of(SEVERE), Optional.of("assistant | v1"));
        assertEquals(row, MatchRow.parse(row.text()));
        assertEquals(Judgment.REFINE, MatchRow.parse(row.text()).judgment().orElseThrow());
        assertEquals("a | [b]\\", MatchRow.parse(row.text()).words());
    }

    @Test
    void aMissedRowHasNoCandidatesAndNoStage() {
        MatchRow row = MatchRow.missed(SENTENCE, 4, 22, "liver insufficiency", HEPATIC, Optional.empty());
        assertEquals(SENTENCE + "[4,22] | liver insufficiency |  |  |  | missed | " + HEPATIC, row.text());
        assertEquals(row, MatchRow.parse(row.text()));
        assertThrows(IllegalArgumentException.class, () -> new MatchRow(SENTENCE, 4, 22, "x", List.of(HEPATIC),
                Optional.of(Stage.DESCRIPTION), OptionalDouble.empty(), Optional.of(Judgment.MISSED), Optional.of(HEPATIC), Optional.empty()));
    }

    @Test
    void aJudgmentNamesAConceptExactlyWhenItMust() {
        MatchRow row = MatchRow.proposed(SENTENCE, 0, 7, "hepatic", List.of(HEPATIC), Stage.DESCRIPTION, OptionalDouble.empty());
        assertThrows(IllegalArgumentException.class, () -> row.judged(Judgment.REFINE, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> row.judged(Judgment.AFFIRM, Optional.of(SEVERE), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> row.judged(Judgment.MISSED, Optional.of(SEVERE), Optional.empty()));
        assertEquals(Optional.empty(), row.judged(Judgment.REJECT, Optional.empty(), Optional.empty()).named());
        assertEquals(Optional.of(SEVERE), row.judged(Judgment.REJECT, Optional.of(SEVERE), Optional.empty()).named());
    }

    @Test
    void whatIsNotARowIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> MatchRow.parse("no anchor | words"));
        assertThrows(IllegalArgumentException.class, () -> MatchRow.parse(SENTENCE + "[3,1] | words | " + HEPATIC + " | description"));
        assertThrows(IllegalArgumentException.class, () -> MatchRow.parse(SENTENCE + "[1,3] | words | not a token | description"));
        assertThrows(IllegalArgumentException.class, () -> MatchRow.parse(SENTENCE + "[1,3] | words | " + HEPATIC + " | stemming"));
    }
}
