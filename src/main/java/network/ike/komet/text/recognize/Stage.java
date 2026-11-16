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

import java.util.Locale;

/**
 * The stage of conceptification that proposed a candidate. A candidate's rate depends on how it
 * was found, so every candidate records its stage. Later stages, stemming, fuzzy matching and the
 * assistant, are added with the work that builds them.
 *
 * <p>Each stage has a version of its behavior, raised only when what it matches changes. A run's
 * match table names it, and a stage's statistics are counted by it, since a checkpoint that
 * leaves the stage's behavior as it was must not scatter its rates (ike-lab-documents topic
 * {@code dev-conceptification-match-provenance}).</p>
 */
public enum Stage {
    /** An exact match of a description's words, case folded unless the description's case is significant. */
    DESCRIPTION(1);

    private final int version;

    Stage(int version) {
        this.version = version;
    }

    /**
     * The version of this stage's behavior.
     *
     * @return the version, raised only when what the stage matches changes
     */
    public int version() {
        return version;
    }

    /**
     * The stage's name as a match table writes it.
     *
     * @return the name in lower case, {@code description}
     */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The stage a match table names.
     *
     * @param word the stage's name as written, in any case
     * @return the stage
     * @throws IllegalArgumentException if no stage has that name
     */
    public static Stage of(String word) {
        for (Stage stage : values()) {
            if (stage.word().equalsIgnoreCase(word.strip())) {
                return stage;
            }
        }
        throw new IllegalArgumentException("no stage is named '" + word + "'");
    }
}
