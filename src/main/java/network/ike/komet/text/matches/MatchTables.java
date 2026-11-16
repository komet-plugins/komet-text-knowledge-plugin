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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The match tables of a document, found among its lines and written back when a new run is made
 * (ike-lab-documents topic {@code dev-conceptification-match-table}). A new run writes a new table
 * at the end of the document and removes from older tables the rows no one has judged; judged rows
 * stay in the table of the run that proposed them, and a table with no rows left is removed.
 */
public final class MatchTables {

    private MatchTables() {
    }

    /**
     * A match table where its document holds it.
     *
     * @param first the index of its attribute line
     * @param last  the index of its closing fence
     * @param table the table
     */
    public record Placed(int first, int last, MatchTable table) {
    }

    /**
     * Finds the match tables of a document.
     *
     * @param lines the document's lines
     * @return its tables in document order
     * @throws IllegalArgumentException if a match table's attribute line is not followed by a
     *                                  fenced table, or a row cannot be read
     */
    public static List<Placed> find(List<String> lines) {
        List<Placed> found = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            if (MatchTable.isAttributeLine(lines.get(i))) {
                int open = i + 1;
                if (open >= lines.size() || !lines.get(open).strip().equals(MatchTable.FENCE)) {
                    throw new IllegalArgumentException("line " + (i + 1) + ": a match table's rows stand between "
                            + MatchTable.FENCE + " fences");
                }
                int close = open + 1;
                while (close < lines.size() && !lines.get(close).strip().equals(MatchTable.FENCE)) {
                    close++;
                }
                if (close >= lines.size()) {
                    throw new IllegalArgumentException("line " + (i + 1) + ": a match table is not closed");
                }
                found.add(new Placed(i, close, MatchTable.parse(lines.subList(i, close + 1))));
                i = close + 1;
            } else {
                i++;
            }
        }
        return found;
    }

    /**
     * The document's lines after a new run: each older table keeps only its judged rows, or is
     * removed with the blank line before it, and any blank lines that end the document after it,
     * when none is left; the new table is written at the end, after one blank line, without the
     * rows whose words a rater has already judged.
     *
     * @param lines the document's lines
     * @param fresh the new run's table
     * @return the document's new lines
     */
    public static List<String> rerun(List<String> lines, MatchTable fresh) {
        List<Placed> older = find(lines);
        Set<MatchRow.Place> judged = new HashSet<>();
        List<String> out = new ArrayList<>(lines.size());
        int next = 0;
        for (Placed placed : older) {
            out.addAll(lines.subList(next, placed.first()));
            MatchTable kept = placed.table().judgedOnly();
            next = placed.last() + 1;
            if (kept.rows().isEmpty()) {
                if (!out.isEmpty() && out.getLast().isBlank()) {
                    out.removeLast();
                }
                if (lines.subList(next, lines.size()).stream().allMatch(String::isBlank)) {
                    next = lines.size();                 // nothing but blank lines followed it: they go with it
                }
            } else {
                out.addAll(kept.lines());
                kept.rows().forEach(row -> judged.add(row.place()));
            }
        }
        out.addAll(lines.subList(next, lines.size()));
        List<MatchRow> rows = fresh.rows().stream().filter(row -> !judged.contains(row.place())).toList();
        if (!rows.isEmpty()) {
            while (!out.isEmpty() && out.getLast().isBlank()) {
                out.removeLast();
            }
            out.add("");
            out.addAll(new MatchTable(fresh.run(), rows).lines());
        }
        return out;
    }
}
