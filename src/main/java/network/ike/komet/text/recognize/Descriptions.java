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

import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;

/**
 * Reads the descriptions the description stage matches from the store, under a view: the latest
 * active version of every description semantic whose referenced component is a concept the filter
 * admits, whose language is the one asked for, and whose type is a fully qualified name or a
 * regular name. Definitions are never read.
 */
public final class Descriptions {

    private Descriptions() {
    }

    /**
     * The descriptions of every concept, in any language.
     *
     * @param view the view whose position and allowed states decide each description's latest version
     * @return the descriptions
     */
    public static List<Description> read(ViewCalculator view) {
        return read(view, conceptNid -> true, KernelConcept.LANGUAGE.nid());
    }

    /**
     * The descriptions of the concepts a filter admits, in one language.
     *
     * @param view        the view whose position and allowed states decide each description's latest version
     * @param concepts    which concepts' descriptions to read, by nid
     * @param languageNid the language concept's nid, or {@code KernelConcept.LANGUAGE}'s for any language
     * @return the descriptions
     */
    public static List<Description> read(ViewCalculator view, LongPredicate concepts, long languageNid) {
        long anyLanguage = KernelConcept.LANGUAGE.nid();
        long caseSensitive = KernelConcept.DESCRIPTION_CASE_SENSITIVE.nid();
        List<Description> found = new ArrayList<>();
        view.forEachSemanticVersionOfPattern(KernelPattern.DESCRIPTION_PATTERN.nid(), (version, pattern) -> {
            if (!State.ACTIVE.equals(version.state())) {
                return;
            }
            long conceptNid = version.referencedComponentNid();
            if (!concepts.test(conceptNid) || !EntityHandle.get(conceptNid).isConcept()) {
                return;
            }
            Optional<Long> type = nidField(version, pattern, KernelConcept.DESCRIPTION_TYPE.nid());
            if (type.isEmpty()) {
                return;
            }
            Optional<DescriptionKind> kind = DescriptionKind.of(type.get());
            if (kind.isEmpty()) {
                return;
            }
            if (languageNid != anyLanguage) {
                Optional<Long> language = nidField(version, pattern, KernelConcept.LANGUAGE_CONCEPT_NID_FOR_DESCRIPTION.nid());
                if (language.isEmpty() || language.get() != languageNid) {
                    return;
                }
            }
            Optional<String> text = textField(version, pattern);
            if (text.isEmpty() || text.get().isBlank()) {
                return;
            }
            Optional<Long> caseSignificance = nidField(version, pattern, KernelConcept.DESCRIPTION_CASE_SIGNIFICANCE.nid());
            boolean significant = caseSignificance.isPresent() && caseSignificance.get() == caseSensitive;
            found.add(new Description(conceptNid, text.get(), kind.get(), significant));
        });
        return found;
    }

    private static Optional<Long> nidField(SemanticEntityVersion version, PatternEntityVersion pattern, long meaningNid) {
        int index = pattern.indexForMeaning(meaningNid);
        if (index < 0 || index >= version.fieldValues().size()) {
            return Optional.empty();
        }
        Object value = version.fieldValues().get(index);
        if (value instanceof EntityFacade facade) {
            return Optional.of(facade.nid());
        }
        return Optional.empty();
    }

    private static Optional<String> textField(SemanticEntityVersion version, PatternEntityVersion pattern) {
        int index = pattern.indexForMeaning(KernelConcept.TEXT_FOR_DESCRIPTION.nid());
        if (index < 0 || index >= version.fieldValues().size()) {
            return Optional.empty();
        }
        Object value = version.fieldValues().get(index);
        if (value instanceof String text) {
            return Optional.of(text);
        }
        return Optional.empty();
    }
}
