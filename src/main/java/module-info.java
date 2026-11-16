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

/**
 * Conceptification, ANFication and semantic indexing of stamped documents.
 *
 * <p>This module begins with the description stage of conceptification: the knowledge base's
 * active descriptions compiled into Lucene synonym maps, a recognizer that finds the longest
 * description at each word of a text and offers every concept that description names, and the
 * concordance report that measures how much of a collection the knowledge base accounts for
 * (IKE-Network/ike-issues#1372), with product labels in the Structured Product Labeling form
 * converted to AsciiDoc so they can be measured too. A stamped document is read as the store holds
 * it, so each recognition anchors to its stored sentence, and a run writes its matches into a
 * match table at the end of the document, which the Reading view draws through the
 * {@code matches} structured block kind this module provides (IKE-Network/ike-issues#1373). The
 * design is the {@code text-to-knowledge} assembly in {@code ike-lab-documents}.
 */
module komet.text.knowledge {
    requires java.xml;
    requires komet.stamped.document;
    requires stamp.adoc.core;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.terms;
    requires org.apache.lucene.core;
    requires org.apache.lucene.analysis.common;
    requires org.eclipse.collections.api;
    requires org.slf4j;
    requires javafx.controls;
    requires dev.ikm.komet.framework;
    requires dev.ikm.komet.layout;

    exports network.ike.komet.text.recognize;
    exports network.ike.komet.text.concordance;
    exports network.ike.komet.text.label;
    exports network.ike.komet.text.matches;

    provides network.ike.komet.document.StructuredBlockKind
            with network.ike.komet.text.ui.MatchesBlockKind;
}
