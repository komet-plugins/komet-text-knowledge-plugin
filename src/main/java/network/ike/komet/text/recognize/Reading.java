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
 * What the recognizer found in one text: the stretches that may name a concept, and the words no
 * recognition covers, which the concordance report gathers into its gap list.
 *
 * @param text         the text that was read
 * @param recognitions the stretches that may name a concept, by start, the longest first at a start
 * @param uncovered    the words no recognition covers, in order, case folded
 */
public record Reading(String text, List<Recognition> recognitions, List<Word> uncovered) {

    /**
     * A word of the text, with its character offsets, end exclusive.
     *
     * @param start the offset of its first character
     * @param end   the offset just past its last character
     * @param text  the word, case folded
     */
    public record Word(int start, int end, String text) {
    }

    /**
     * Validates the reading and copies its lists.
     *
     * @throws NullPointerException if any component is null
     */
    public Reading {
        Objects.requireNonNull(text, "text");
        recognitions = List.copyOf(recognitions);
        uncovered = List.copyOf(uncovered);
    }

    /**
     * Whether the text holds at least one recognition.
     *
     * @return true when anything was recognized
     */
    public boolean hasReference() {
        return !recognitions.isEmpty();
    }
}
