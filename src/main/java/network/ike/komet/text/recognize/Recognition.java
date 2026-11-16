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

import java.util.List;
import java.util.Objects;

/**
 * A stretch of a text that may name a concept, with the candidates for it. Offsets are character
 * offsets into the text that was read, end exclusive, so {@code text.substring(start, end)} gives
 * the words.
 *
 * @param start      the offset of the first character of the words
 * @param end        the offset just past the last character of the words
 * @param words      the words as they appear in the text
 * @param candidates the concepts the words may name, in rank order; never empty
 */
public record Recognition(int start, int end, String words, List<Candidate> candidates) {

    /**
     * Validates the recognition and copies its candidates.
     *
     * @throws NullPointerException     if {@code words} or {@code candidates} is null
     * @throws IllegalArgumentException if the offsets are out of order or there is no candidate
     */
    public Recognition {
        Objects.requireNonNull(words, "words");
        candidates = List.copyOf(candidates);
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("offsets out of order: " + start + ".." + end);
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("a recognition needs at least one candidate");
        }
    }

    /**
     * Whether the words name more than one concept, so a rater must choose.
     *
     * @return true when there are two candidates or more
     */
    public boolean ambiguous() {
        return candidates.size() > 1;
    }
}
