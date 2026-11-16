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

import network.ike.komet.text.recognize.Stage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static network.ike.komet.text.matches.MatchRowTest.HEPATIC;
import static network.ike.komet.text.matches.MatchRowTest.SENTENCE;
import static network.ike.komet.text.matches.MatchRowTest.SEVERE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run's identity on its table's attribute line, and a document's tables found and rewritten when
 * a new run is made.
 */
class MatchTablesTest {

    static final RunIdentity FIRST = new RunIdentity(Instant.parse("2026-11-14T22:10:00.123Z"),
            "checkpoint/main-20261113-151714", RunIdentity.stages(List.of(Stage.DESCRIPTION)),
            "stamp-adoc-core 1.3.0-SNAPSHOT", "ike-starter-set 1-SNAPSHOT, \"snomed\" stated",
            "Latest on Development path", "sha256:00ff");
    static final RunIdentity SECOND = new RunIdentity(Instant.parse("2026-11-15T08:00:00Z"),
            RunIdentity.DEVELOPMENT, "description 1", "", "", "", "sha256:0100");

    @Test
    void theAttributeLineIsReadBackAsWritten() {
        assertEquals("[matches,run=\"2026-11-14T22:10:00Z\",matcher=\"checkpoint/main-20261113-151714\","
                        + "stages=\"description 1\",splitter=\"stamp-adoc-core 1.3.0-SNAPSHOT\","
                        + "data=\"ike-starter-set 1-SNAPSHOT, \\\"snomed\\\" stated\",position=\"Latest on Development path\","
                        + "descriptions=\"sha256:00ff\"]",
                FIRST.attributeLine());
        assertEquals(FIRST, RunIdentity.parse(FIRST.attributeLine()));
        assertTrue(SECOND.development());
        assertFalse(FIRST.development());
        assertThrows(IllegalArgumentException.class, () -> RunIdentity.parse("[source,java]"));
        assertThrows(IllegalArgumentException.class, () -> RunIdentity.parse("[matches,matcher=x,stages=y,descriptions=z]"));
    }

    @Test
    void aTableIsFoundAmongItsDocumentsLinesAndReadBack() {
        MatchTable table = new MatchTable(FIRST, List.of(proposed(0, 7, "hepatic"), proposed(20, 38, "hepatic impairment")));
        List<String> lines = new ArrayList<>(List.of("= Title", "", "Prose.", "", "[source]", "----", "code", "----", ""));
        lines.addAll(table.lines());
        List<MatchTables.Placed> found = MatchTables.find(lines);
        assertEquals(1, found.size());
        assertEquals(9, found.getFirst().first());
        assertEquals(lines.size() - 1, found.getFirst().last());
        assertEquals(table, found.getFirst().table());
        assertTrue(MatchTable.isAttributeLine("  [Matches] "));
        assertFalse(MatchTable.isAttributeLine("[[matches]]"));
        assertFalse(MatchTable.isAttributeLine("[matches-like]"));
    }

    @Test
    void aNewRunKeepsJudgedRowsAndReplacesTheRest() {
        MatchRow judged = proposed(0, 7, "hepatic").judged(Judgment.REFINE, Optional.of(SEVERE), Optional.empty());
        MatchTable older = new MatchTable(FIRST, List.of(judged, proposed(20, 38, "hepatic impairment")));
        List<String> lines = new ArrayList<>(List.of("= Title", "", "Prose.", ""));
        lines.addAll(older.lines());

        MatchTable fresh = new MatchTable(SECOND, List.of(proposed(0, 7, "hepatic"), proposed(20, 38, "hepatic impairment"),
                proposed(40, 45, "liver")));
        List<String> rerun = MatchTables.rerun(lines, fresh);

        List<MatchTables.Placed> found = MatchTables.find(rerun);
        assertEquals(2, found.size());
        assertEquals(List.of(judged), found.get(0).table().rows());
        assertEquals(FIRST, found.get(0).table().run());
        assertEquals(List.of(proposed(20, 38, "hepatic impairment"), proposed(40, 45, "liver")), found.get(1).table().rows());
        assertEquals(SECOND, found.get(1).table().run());
        assertEquals("", rerun.get(found.get(1).first() - 1), "a blank line before the new table");
        assertEquals(List.of("= Title", "", "Prose.", ""), rerun.subList(0, 4));
    }

    @Test
    void aTableWithNoJudgedRowsIsRemovedWithTheBlankLineBeforeIt() {
        List<String> lines = new ArrayList<>(List.of("= Title", "", "Prose.", ""));
        lines.addAll(new MatchTable(FIRST, List.of(proposed(0, 7, "hepatic"))).lines());
        lines.add("");
        List<String> rerun = MatchTables.rerun(lines, new MatchTable(SECOND, List.of(proposed(0, 7, "hepatic"))));
        List<String> expected = new ArrayList<>(List.of("= Title", "", "Prose.", ""));
        expected.addAll(new MatchTable(SECOND, List.of(proposed(0, 7, "hepatic"))).lines());
        assertEquals(expected, rerun);
        assertEquals(List.of("= Title", "", "Prose."), MatchTables.rerun(lines, new MatchTable(SECOND, List.of())));
    }

    @Test
    void aTableThatIsNotClosedIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> MatchTables.find(List.of(FIRST.attributeLine(), "----", "row")));
        assertThrows(IllegalArgumentException.class, () -> MatchTables.find(List.of(FIRST.attributeLine(), "prose")));
    }

    private static MatchRow proposed(int from, int to, String words) {
        return MatchRow.proposed(SENTENCE, from, to, words, List.of(HEPATIC), Stage.DESCRIPTION, OptionalDouble.of(1));
    }
}
