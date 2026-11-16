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

import dev.ikm.tinkar.terms.KernelConcept;

import java.util.Optional;

/**
 * The kinds of description the description stage matches. A definition is never matched: it
 * describes a concept rather than naming it.
 */
public enum DescriptionKind {
    /** A fully qualified name, matched without its semantic tag. */
    FULLY_QUALIFIED_NAME,
    /** A regular name; the knowledge base keeps synonyms as regular names. */
    REGULAR_NAME;

    /**
     * The kind of a description type, if the description stage matches descriptions of that type.
     * The kernel concepts are resolved when this is called, so the store must be running.
     *
     * @param descriptionTypeNid the nid of a description type concept
     * @return the kind, or empty for a definition or any other type
     */
    public static Optional<DescriptionKind> of(long descriptionTypeNid) {
        if (descriptionTypeNid == KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()) {
            return Optional.of(FULLY_QUALIFIED_NAME);
        }
        if (descriptionTypeNid == KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid()) {
            return Optional.of(REGULAR_NAME);
        }
        return Optional.empty();
    }
}
