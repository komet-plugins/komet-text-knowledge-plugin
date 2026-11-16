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

import java.util.Locale;

/**
 * A rater's judgment of a match, made on its first candidate (ike-lab-documents topic
 * {@code dev-conceptification-judgments}).
 */
public enum Judgment {

    /** The words name the first candidate. */
    AFFIRM(Named.NONE),

    /** The words name a concept narrower than the first candidate, which the judgment names. */
    REFINE(Named.REQUIRED),

    /** The words name a concept broader than the first candidate, which the judgment names. */
    GENERALIZE(Named.REQUIRED),

    /** The words do not name the first candidate; the judgment may name the concept they do name. */
    REJECT(Named.OPTIONAL),

    /** Words no stage proposed name a concept, which the judgment names. */
    MISSED(Named.REQUIRED);

    /** Whether a judgment names a concept of its own. */
    enum Named {
        /** It names none. */
        NONE,
        /** It always names one. */
        REQUIRED,
        /** It may name one. */
        OPTIONAL
    }

    private final Named named;

    Judgment(Named named) {
        this.named = named;
    }

    /**
     * Whether this judgment names a concept of its own.
     *
     * @return {@link Named#NONE}, {@link Named#REQUIRED} or {@link Named#OPTIONAL}
     */
    Named named() {
        return named;
    }

    /**
     * The judgment as a match table writes it.
     *
     * @return the name in lower case, {@code refine}
     */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The judgment a match table names.
     *
     * @param word the judgment as written, in any case
     * @return the judgment
     * @throws IllegalArgumentException if no judgment has that name
     */
    public static Judgment of(String word) {
        for (Judgment judgment : values()) {
            if (judgment.word().equalsIgnoreCase(word.strip())) {
                return judgment;
            }
        }
        throw new IllegalArgumentException("no judgment is named '" + word + "'");
    }
}
