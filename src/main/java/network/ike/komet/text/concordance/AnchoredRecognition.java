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
package network.ike.komet.text.concordance;

import network.ike.komet.text.recognize.Recognition;

import java.util.Objects;

/**
 * A recognition in a stored sentence, with the segment anchor that says where it lies:
 * {@code segment:<sentence id>[from,to]=<words>}, where the columns count in the sentence's own
 * text.
 *
 * @param sentence    the sentence the words are in
 * @param anchor      the segment anchor of the words
 * @param recognition the words and the concepts they may name, with offsets into the sentence
 */
public record AnchoredRecognition(Sentence sentence, String anchor, Recognition recognition) {

    /**
     * Validates the anchored recognition.
     *
     * @throws NullPointerException if any component is null
     */
    public AnchoredRecognition {
        Objects.requireNonNull(sentence, "sentence");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(recognition, "recognition");
    }
}
