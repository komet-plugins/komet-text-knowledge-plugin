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

import design.knowledge.stamp.adoc.dialect.Segmenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sentences of an AsciiDoc file, read without a store, for a headless run over a folder of
 * documents. Prose paragraphs, section titles, block titles, list items and table cells are read;
 * comments, attribute entries (with any lines a trailing backslash continues them onto), block
 * attribute lines, directives and the content of listing, literal and passthrough blocks are not.
 * Inline markup is removed ({@link PlainText}) and an authored {@code k:} token is read as its
 * label. The plain text of a paragraph, title, label or cell is split into sentences by the rules
 * the store splits its sentences with, stamp-adoc-core's {@link Segmenter}, so an abbreviation such
 * as "vs." or "Dr." ends no sentence, a sentence whose period stood inside markup still ends there,
 * and the same file always gives the same sentences.
 *
 * <p>A stored document gives its own sentences with their anchors ({@link StoredSentences}); this
 * reader serves documents that have not been materialized, and the headless concordance run.
 */
public final class AsciiDocSentences {

    private static final Pattern HEADING = Pattern.compile("^(=+)\\s+(.*)$");
    private static final Pattern ATTRIBUTE_ENTRY = Pattern.compile("^:[!]?[A-Za-z0-9_][A-Za-z0-9_-]*[!]?:.*$");
    private static final Pattern BLOCK_ATTRIBUTES = Pattern.compile("^\\[.*]$");
    private static final Pattern BLOCK_TITLE = Pattern.compile("^\\.([^.\\s].*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[*\\-]+|\\.+|\\d+\\.)\\s+(.*)$");
    private static final Pattern LABELED_ITEM = Pattern.compile("^([^:]{1,80}?):{2,4}(?:\\s+(.*))?$");
    private static final Pattern DIRECTIVE = Pattern.compile(
            "^(include|image|toc|ifdef|ifndef|ifeval|endif|video|audio)::.*$|^'''$|^<<<$");
    private static final Pattern VERBATIM_DELIMITER = Pattern.compile("^(-{4,}|\\.{4,}|\\+{4,}|/{4,})$");
    private static final Pattern OTHER_DELIMITER = Pattern.compile("^(={4,}|\\*{4,}|_{4,}|--)$");
    private static final Pattern TABLE_DELIMITER = Pattern.compile("^[|,:!]===$");

    private AsciiDocSentences() {
    }

    /**
     * Reads the sentences of a file.
     *
     * @param file     the AsciiDoc file
     * @param document the name the sentences carry, typically the path relative to the collection
     * @return the sentences, in order
     * @throws IOException if the file cannot be read
     */
    public static List<Sentence> read(Path file, String document) throws IOException {
        return read(Files.readAllLines(file), document);
    }

    /**
     * Reads the sentences of a document's lines.
     *
     * @param lines    the document's lines
     * @param document the name the sentences carry
     * @return the sentences, in order
     */
    public static List<Sentence> read(List<String> lines, String document) {
        List<Sentence> sentences = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String section = "";
        String verbatim = null;
        boolean inTable = false;
        boolean continued = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (continued) {
                // the rest of an attribute entry's value, which a trailing backslash carried onto this line
                continued = line.endsWith("\\");
                continue;
            }
            if (verbatim != null) {
                if (line.equals(verbatim)) {
                    verbatim = null;
                }
                continue;
            }
            if (VERBATIM_DELIMITER.matcher(line).matches()) {
                section = flush(paragraph, document, section, sentences);
                verbatim = line;
                continue;
            }
            if (TABLE_DELIMITER.matcher(line).matches()) {
                section = flush(paragraph, document, section, sentences);
                inTable = !inTable;
                continue;
            }
            if (ATTRIBUTE_ENTRY.matcher(line).matches()) {
                section = flush(paragraph, document, section, sentences);
                continued = line.endsWith("\\");
                continue;
            }
            if (line.isEmpty() || line.startsWith("//") || OTHER_DELIMITER.matcher(line).matches()
                    || BLOCK_ATTRIBUTES.matcher(line).matches()
                    || DIRECTIVE.matcher(line).matches() || line.equals("+")) {
                section = flush(paragraph, document, section, sentences);
                continue;
            }
            if (inTable) {
                section = flush(paragraph, document, section, sentences);
                for (String cell : line.split("\\|")) {
                    add(cell, document, section, sentences);
                }
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                flush(paragraph, document, section, sentences);
                section = clean(heading.group(2));
                add(heading.group(2), document, section, sentences);
                continue;
            }
            Matcher title = BLOCK_TITLE.matcher(line);
            if (title.matches()) {
                section = flush(paragraph, document, section, sentences);
                add(title.group(1), document, section, sentences);
                continue;
            }
            Matcher item = LIST_ITEM.matcher(line);
            if (item.matches()) {
                section = flush(paragraph, document, section, sentences);
                paragraph.append(item.group(1));
                continue;
            }
            Matcher labeled = LABELED_ITEM.matcher(line);
            if (labeled.matches()) {
                section = flush(paragraph, document, section, sentences);
                add(labeled.group(1), document, section, sentences);
                if (labeled.group(2) != null) {
                    paragraph.append(labeled.group(2));
                }
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        flush(paragraph, document, section, sentences);
        return renumber(sentences);
    }

    /**
     * A line of AsciiDoc with its inline markup removed, as {@link PlainText} removes it: an
     * authored {@code k:} token becomes its label, a link or cross-reference its text, index terms
     * are dropped or reduced to their visible words, and formatting marks are removed.
     *
     * @param text the line
     * @return the plain text, with runs of white space made one space
     */
    public static String clean(String text) {
        return PlainText.of(text).text();
    }

    private static String flush(StringBuilder paragraph, String document, String section, List<Sentence> sentences) {
        if (paragraph.isEmpty()) {
            return section;
        }
        String text = clean(paragraph.toString());
        paragraph.setLength(0);
        for (String sentence : split(text)) {
            sentences.add(new Sentence(document, 0, section, sentence));
        }
        return section;
    }

    /** Adds the sentences of a title, a label or a cell, which may hold more than one. */
    private static void add(String raw, String document, String section, List<Sentence> sentences) {
        for (String sentence : split(clean(raw))) {
            sentences.add(new Sentence(document, 0, section, sentence));
        }
    }

    /** The sentences of plain text, as the store's segmenter ends them, without empty ones. */
    private static List<String> split(String text) {
        List<String> split = new ArrayList<>();
        if (text.isEmpty()) {
            return split;
        }
        for (int[] range : Segmenter.sentences(text)) {
            String sentence = text.substring(range[0], range[1]).strip();
            if (!sentence.isEmpty()) {
                split.add(sentence);
            }
        }
        return split;
    }

    private static List<Sentence> renumber(List<Sentence> sentences) {
        List<Sentence> numbered = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            Sentence s = sentences.get(i);
            numbered.add(new Sentence(s.document(), i, s.section(), s.text()));
        }
        return numbered;
    }
}
