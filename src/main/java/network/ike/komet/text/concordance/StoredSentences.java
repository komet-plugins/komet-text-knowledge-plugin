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

import dev.ikm.tinkar.common.id.PublicId;
import network.ike.komet.document.DocumentNarratives;
import network.ike.komet.document.SegmentNarrative;
import network.ike.komet.document.Span;
import network.ike.komet.text.matches.MatchRow;
import network.ike.komet.text.recognize.Reading;
import network.ike.komet.text.recognize.Recognition;
import network.ike.komet.text.recognize.Recognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sentences of a stamped document as the store holds them, so that what is recognized in them
 * anchors to them. They are the sentences the ANF lift reads
 * ({@link DocumentNarratives#narrative(PublicId)}): each block's stored sentences in document order,
 * each named by its sentence vertex's identifier.
 *
 * <p>A stored sentence holds its text as written, inline markup and all. It is read as plain text,
 * as a file read as text is ({@link PlainText}), so that the terms of an index entry or the
 * address of a link are not read as prose, and a sentence of nothing but markup is left out. Words
 * recognized in the plain text are written as a segment anchor,
 * {@code segment:<sentence id>[from,to]=<words>}, whose columns count in the stored text, so the
 * anchor names the words where the store holds them, survives an edit to any other sentence, and
 * follows its own sentence through a revision that keeps the words ({@link SegmentNarrative}).
 * The anchor's words are the stored text between those columns, markup inside the stretch
 * included.
 *
 * <p>Only a document stored as vertices has sentences. The narrative names no sections, so each
 * sentence's section is empty here; counting judged rates by the kind of section
 * (IKE-Network/ike-issues#1374) will need the titles of the stored blocks.
 */
public final class StoredSentences {

    /** A sentence as read, the identifier of its stored sentence, and its plain text mapped to the stored text. */
    private record Entry(Sentence sentence, String id, PlainText plain) {
    }

    private final SegmentNarrative narrative;
    private final List<Entry> entries;
    private final List<Sentence> sentences;

    private StoredSentences(SegmentNarrative narrative, List<Entry> entries) {
        this.narrative = narrative;
        this.entries = entries;
        this.sentences = entries.stream().map(Entry::sentence).toList();
    }

    /**
     * Reads a stored document's sentences, in the version pending in the store, which is the one
     * the document card shows.
     *
     * @param document the document's anchor
     * @param name     the name its sentences are reported under, such as the file's path
     * @return the sentences; empty when the store holds no sentences for the document
     */
    public static Optional<StoredSentences> read(PublicId document, String name) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(name, "name");
        return DocumentNarratives.narrative(document).map(narrative -> of(narrative, name));
    }

    /**
     * The sentences of a narrative already read from the store.
     *
     * @param narrative the document's narrative
     * @param name      the name its sentences are reported under, such as the file's path
     * @return the sentences, in narrative order, less those of nothing but markup
     */
    public static StoredSentences of(SegmentNarrative narrative, String name) {
        Objects.requireNonNull(narrative, "narrative");
        Objects.requireNonNull(name, "name");
        List<Entry> entries = new ArrayList<>();
        for (SegmentNarrative.Sentence stored : narrative.sentences()) {
            PlainText plain = PlainText.of(stored.text());
            if (!plain.text().isEmpty()) {
                entries.add(new Entry(new Sentence(name, entries.size(), "", plain.text()), stored.id(), plain));
            }
        }
        return new StoredSentences(narrative, List.copyOf(entries));
    }

    /**
     * The narrative the sentences were read from, which resolves their anchors.
     *
     * @return the narrative
     */
    public SegmentNarrative narrative() {
        return narrative;
    }

    /**
     * The sentences, in document order, each as plain text.
     *
     * @return the sentences
     */
    public List<Sentence> sentences() {
        return sentences;
    }

    /**
     * The identifier of a sentence's vertex in the store.
     *
     * @param sentence one of these sentences
     * @return the identifier the store derives for it
     * @throws IllegalArgumentException if the sentence is not one of these
     */
    public String sentenceId(Sentence sentence) {
        return entry(sentence).id();
    }

    /**
     * A sentence's text as the store holds it, markup and all.
     *
     * @param sentence one of these sentences
     * @return the stored text
     * @throws IllegalArgumentException if the sentence is not one of these
     */
    public String storedText(Sentence sentence) {
        return entry(sentence).plain().source();
    }

    /**
     * The segment anchor of words recognized in one of these sentences.
     *
     * @param sentence    the sentence that was read
     * @param recognition a recognition from reading its plain text
     * @return the anchor, {@code segment:<sentence id>[from,to]=<words>}, in columns of the stored text
     * @throws IllegalArgumentException if the sentence is not one of these, or the recognition's
     *                                  offsets do not lie within its plain text
     */
    public String anchor(Sentence sentence, Recognition recognition) {
        MatchRow.Place place = place(sentence, recognition);
        Span span = narrative.span(place.sentence(), place.from(), place.to())
                .orElseThrow(() -> new IllegalStateException("columns " + place.from() + ".." + place.to()
                        + " do not lie within stored sentence " + place.sentence()));
        return narrative.anchor(span);
    }

    /**
     * Where a recognition's words stand in the store: the stored sentence and the columns of its
     * stored text, the place a match table's row names.
     *
     * @param sentence    one of this document's sentences
     * @param recognition a recognition in that sentence's plain text
     * @return the sentence's id and the columns the words span in its stored text
     * @throws IllegalArgumentException if the sentence is not one of this document's, or the
     *                                  recognition's offsets lie outside its plain text
     */
    public MatchRow.Place place(Sentence sentence, Recognition recognition) {
        Objects.requireNonNull(recognition, "recognition");
        Entry entry = entry(sentence);
        if (recognition.end() > entry.plain().text().length()) {
            throw new IllegalArgumentException("offsets " + recognition.start() + ".." + recognition.end()
                    + " do not lie within sentence " + sentence.index());
        }
        return new MatchRow.Place(entry.id(), entry.plain().sourceStart(recognition.start()),
                entry.plain().sourceEnd(recognition.end()));
    }

    /**
     * Reads every sentence and anchors every recognition.
     *
     * @param recognizer the recognizer to read with
     * @return the recognitions, in document order, each with its anchor
     */
    public List<AnchoredRecognition> recognize(Recognizer recognizer) {
        Objects.requireNonNull(recognizer, "recognizer");
        List<AnchoredRecognition> anchored = new ArrayList<>();
        for (Sentence sentence : sentences) {
            Reading reading = recognizer.read(sentence.text());
            for (Recognition recognition : reading.recognitions()) {
                anchored.add(new AnchoredRecognition(sentence, anchor(sentence, recognition), recognition));
            }
        }
        return anchored;
    }

    private Entry entry(Sentence sentence) {
        Objects.requireNonNull(sentence, "sentence");
        int index = sentence.index();
        if (index < 0 || index >= entries.size() || !entries.get(index).sentence().equals(sentence)) {
            throw new IllegalArgumentException("not a sentence of this document: " + sentence);
        }
        return entries.get(index);
    }
}
