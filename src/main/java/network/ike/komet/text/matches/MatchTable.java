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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The block one run of conceptification writes at the end of the document it matched: the run's
 * identity as the attribute line, then one row per match between listing fences
 * (ike-lab-documents topic {@code dev-conceptification-match-table}).
 * <pre>
 * [matches,run="…",matcher="…",stages="description 1",…]
 * ----
 * 4f1c…[112,130] | hepatic impairment | k:uuid=…[Hepatic impairment] | description | 1.00
 * ----
 * </pre>
 * <p>A listing keeps its content verbatim, so the rows are plain lines of the document, committed
 * and STAMPed as its prose is, and never read as prose.</p>
 *
 * @param run  what the run was made by and against
 * @param rows the rows, in document order
 */
public record MatchTable(RunIdentity run, List<MatchRow> rows) {

    /** The block style that names a match table. */
    public static final String STYLE = "matches";

    /** The fence a match table's rows stand between. */
    public static final String FENCE = "----";

    /**
     * Copies the rows.
     *
     * @throws NullPointerException if the run or the rows are null
     */
    public MatchTable {
        Objects.requireNonNull(run, "run");
        rows = List.copyOf(rows);
    }

    /**
     * The table as its document holds it.
     *
     * @return the attribute line, the opening fence, one line per row, and the closing fence
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>(rows.size() + 3);
        lines.add(run.attributeLine());
        lines.add(FENCE);
        for (MatchRow row : rows) {
            lines.add(row.text());
        }
        lines.add(FENCE);
        return lines;
    }

    /**
     * This table with only the rows a rater has judged, as a new run leaves an older table.
     *
     * @return the table with its judged rows
     */
    public MatchTable judgedOnly() {
        return new MatchTable(run, rows.stream().filter(row -> row.judgment().isPresent()).toList());
    }

    /**
     * Reads a match table as its document holds it.
     *
     * @param lines the attribute line, the opening fence, the rows and the closing fence
     * @return the table
     * @throws IllegalArgumentException if the lines are not a match table
     */
    public static MatchTable parse(List<String> lines) {
        if (lines.size() < 3 || !lines.get(1).strip().equals(FENCE) || !lines.getLast().strip().equals(FENCE)) {
            throw new IllegalArgumentException("a match table is an attribute line and rows between two " + FENCE + " fences");
        }
        RunIdentity run = RunIdentity.parse(lines.getFirst());
        List<MatchRow> rows = new ArrayList<>();
        for (String line : lines.subList(2, lines.size() - 1)) {
            if (!line.isBlank()) {
                rows.add(MatchRow.parse(line));
            }
        }
        return new MatchTable(run, rows);
    }

    /**
     * Whether a line is the attribute line of a match table: a block attribute line whose style is
     * {@value #STYLE}.
     *
     * @param line a line of a document
     * @return true for {@code [matches]} or {@code [matches,…]}
     */
    public static boolean isAttributeLine(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("[") || !stripped.endsWith("]") || stripped.startsWith("[[")) {
            return false;
        }
        String body = stripped.substring(1, stripped.length() - 1);
        int comma = body.indexOf(',');
        String style = (comma < 0 ? body : body.substring(0, comma)).strip();
        return style.equalsIgnoreCase(STYLE);
    }
}
