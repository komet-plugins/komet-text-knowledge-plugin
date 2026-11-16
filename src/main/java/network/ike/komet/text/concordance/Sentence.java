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

import java.util.Objects;

/**
 * One sentence of a document, the unit the concordance report counts.
 *
 * @param document the document it belongs to, as its path relative to the collection
 * @param index    its position among the document's sentences, from zero
 * @param section  the title of the section it is in, empty before the first section
 * @param text     the sentence's text, with AsciiDoc markup removed
 */
public record Sentence(String document, int index, String section, String text) {

    /**
     * Validates the sentence.
     *
     * @throws NullPointerException if any text component is null
     */
    public Sentence {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(text, "text");
    }
}
