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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What a run of conceptification was made by and against, as the attribute line of its match
 * table names it (ike-lab-documents topic {@code dev-conceptification-match-provenance}):
 * <pre>
 * [matches,run="2026-11-14T22:10:00Z",matcher="checkpoint/main-20261113-151714",stages="description 1",
 *  splitter="stamp-adoc-core 1.3.0-SNAPSHOT",data="…",position="…",descriptions="sha256:…"]
 * </pre>
 *
 * @param run          when the run was made, to the second
 * @param matcher      the matcher's released version, the workspace checkpoint it was built
 *                     from, or {@link #DEVELOPMENT} when neither names it
 * @param stages       each stage the run used with the version of its behavior, {@code description 1}
 * @param splitter     the sentence splitter, which lies outside the working set, by its version
 * @param data         the data the knowledge base was loaded from, each by its name and version
 * @param position     the view's position the descriptions were read at
 * @param descriptions the fingerprint of the descriptions the matcher read
 */
public record RunIdentity(Instant run, String matcher, String stages, String splitter, String data,
                          String position, String descriptions) {

    /** What a matcher no release and no checkpoint names is called. */
    public static final String DEVELOPMENT = "development";

    /**
     * Validates the identity and keeps the run's time to the second.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if the matcher, the stages or the fingerprint is blank
     */
    public RunIdentity {
        Objects.requireNonNull(run, "run");
        run = run.truncatedTo(ChronoUnit.SECONDS);
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(position, "position");
        if (Objects.requireNonNull(matcher, "matcher").isBlank()
                || Objects.requireNonNull(stages, "stages").isBlank()
                || Objects.requireNonNull(descriptions, "descriptions").isBlank()) {
            throw new IllegalArgumentException("a run names its matcher, its stages and its descriptions");
        }
    }

    /**
     * The stages a run used, each with the version of its behavior.
     *
     * @param used the stages
     * @return {@code description 1}, stages separated by commas
     */
    public static String stages(Collection<Stage> used) {
        return used.stream().map(stage -> stage.word() + " " + stage.version()).collect(Collectors.joining(", "));
    }

    /**
     * Whether the matcher is a development build, whose judgments count as evidence about phrases
     * but not toward the rates of a stage's version.
     *
     * @return true when no release and no checkpoint names the matcher
     */
    public boolean development() {
        return DEVELOPMENT.equals(matcher);
    }

    /**
     * The attribute line of the run's match table.
     *
     * @return {@code [matches,run="…",…]}
     */
    public String attributeLine() {
        Map<String, String> attributes = attributes();
        StringBuilder line = new StringBuilder("[").append(MatchTable.STYLE);
        attributes.forEach((name, value) -> line.append(',').append(name).append("=\"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"'));
        return line.append(']').toString();
    }

    /**
     * Reads the attribute line of a match table.
     *
     * @param line the attribute line
     * @return the run's identity
     * @throws IllegalArgumentException if the line is not the attribute line of a match table, or
     *                                  lacks the run, matcher, stages or descriptions
     */
    public static RunIdentity parse(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("[") || !stripped.endsWith("]")) {
            throw new IllegalArgumentException("an attribute line is in brackets: " + line);
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        String body = stripped.substring(1, stripped.length() - 1);
        int i = 0;
        int field = 0;
        while (i <= body.length()) {
            int start = i;
            StringBuilder value = new StringBuilder();
            String name = null;
            boolean quoted = false;
            while (i < body.length() && (quoted || body.charAt(i) != ',')) {
                char c = body.charAt(i);
                if (quoted && c == '\\' && i + 1 < body.length()) {
                    value.append(body.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    quoted = !quoted;
                } else if (c == '=' && name == null && !quoted) {
                    name = body.substring(start, i).strip();
                    value.setLength(0);
                } else {
                    value.append(c);
                }
                i++;
            }
            if (field == 0) {
                if (!value.toString().strip().equalsIgnoreCase(MatchTable.STYLE)) {
                    throw new IllegalArgumentException("not a match table's attribute line: " + line);
                }
            } else if (name != null) {
                attributes.put(name.toLowerCase(Locale.ROOT), value.toString());
            }
            field++;
            i++;
        }
        String run = attributes.getOrDefault("run", "");
        try {
            return new RunIdentity(Instant.parse(run), attributes.getOrDefault("matcher", ""),
                    attributes.getOrDefault("stages", ""), attributes.getOrDefault("splitter", ""),
                    attributes.getOrDefault("data", ""), attributes.getOrDefault("position", ""),
                    attributes.getOrDefault("descriptions", ""));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("a run's time is an instant, not '" + run + "'", e);
        }
    }

    private Map<String, String> attributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("run", run.toString());
        attributes.put("matcher", matcher);
        attributes.put("stages", stages);
        attributes.put("splitter", splitter);
        attributes.put("data", data);
        attributes.put("position", position);
        attributes.put("descriptions", descriptions);
        return attributes;
    }
}
