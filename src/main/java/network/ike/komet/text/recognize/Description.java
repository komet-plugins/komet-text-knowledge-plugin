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

import java.util.Objects;

/**
 * One active description of a concept, as the description stage matches it.
 *
 * @param conceptNid      the nid of the concept the description names
 * @param text            the description's text as the knowledge base holds it
 * @param kind            whether it is a fully qualified name or a regular name
 * @param caseSignificant whether the description's case must be matched as written; a description
 *                        whose case is not significant is matched with case folded
 */
public record Description(long conceptNid, String text, DescriptionKind kind, boolean caseSignificant) {

    /**
     * Validates the description.
     *
     * @throws NullPointerException if {@code text} or {@code kind} is null
     */
    public Description {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * The words the description stage matches: the text, without the semantic tag that ends a
     * fully qualified name, so "Pupil (body structure)" is matched as "Pupil".
     *
     * @return the words to match
     */
    public String matchText() {
        if (kind == DescriptionKind.FULLY_QUALIFIED_NAME) {
            return withoutSemanticTag(text);
        }
        return text;
    }

    /**
     * A fully qualified name without its semantic tag: the text before the last " (" when the
     * text ends with ")". A name with no tag is returned unchanged.
     *
     * @param fullyQualifiedName the fully qualified name
     * @return the name without its semantic tag
     */
    public static String withoutSemanticTag(String fullyQualifiedName) {
        String trimmed = fullyQualifiedName.strip();
        if (!trimmed.endsWith(")")) {
            return trimmed;
        }
        int open = trimmed.lastIndexOf(" (");
        if (open <= 0) {
            return trimmed;
        }
        return trimmed.substring(0, open).strip();
    }
}
