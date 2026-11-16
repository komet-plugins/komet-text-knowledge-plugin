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
package network.ike.komet.text.label;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts a product label in the Structured Product Labeling form (HL7 SPL, XML) to AsciiDoc, so
 * that its text can be read like any stamped document (IKE-Network/ike-issues#1372).
 *
 * <p>It keeps what the label already codes, as the concordance design proposes: the set identifier,
 * version and effective time as document attributes, and each section's LOINC section code as a
 * {@code [loinc=CODE]} attribute on the section's heading, or on the first block of a section that
 * has no title. Paragraphs become one line each, lists become {@code *} items, and tables become
 * {@code |===} tables with one row to a line. Left out: the highlights excerpts, which repeat the
 * full text; footnotes and images; and the product data elements section ({@value #PRODUCT_DATA_SECTION}),
 * which is structured data rather than prose.
 *
 * <p>The parser refuses document type declarations and external entities, since a label is read
 * from outside the build.
 */
public final class SplToAsciiDoc {

    /** The namespace of SPL documents, HL7 version 3. */
    public static final String HL7_V3 = "urn:hl7-org:v3";

    /** The LOINC code of the product data elements section, which is structured data and left out. */
    public static final String PRODUCT_DATA_SECTION = "48780-1";

    private static final Set<String> SKIPPED_SECTIONS = Set.of(PRODUCT_DATA_SECTION);
    private static final Set<String> DROPPED_INLINE = Set.of("footnote", "footnoteRef", "renderMultiMedia",
            "observationMedia");
    private static final Set<String> BLOCKS_IN_TEXT = Set.of("list", "table");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int DEEPEST_HEADING = 6;

    private SplToAsciiDoc() {
    }

    /**
     * Converts a label file, or every {@code .xml} file in a folder, from the command line.
     *
     * <p>Arguments: {@code <label.xml> <out.adoc>} or {@code <folder> <out folder>}, then
     * optionally {@code --source <text>}, which names where the labels came from in a
     * {@code :spl-source:} attribute.
     *
     * @param args the arguments described above
     * @throws IOException if a label cannot be read or a document cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2 && !(args.length == 4 && args[2].equals("--source"))) {
            throw new IllegalArgumentException(
                    "usage: <label.xml> <out.adoc> | <folder> <out folder> [--source <text>]");
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Optional<String> source = args.length == 4 ? Optional.of(args[3]) : Optional.empty();
        if (Files.isDirectory(in)) {
            Files.createDirectories(out);
            for (Path label : xmlFiles(in)) {
                String name = label.getFileName().toString();
                Files.writeString(out.resolve(name.substring(0, name.length() - ".xml".length()) + ".adoc"),
                        convert(label, source));
            }
        } else {
            Files.writeString(out, convert(in, source));
        }
    }

    /**
     * Converts a label file.
     *
     * @param label  the SPL file
     * @param source where the label came from, written as {@code :spl-source:} when present
     * @return the AsciiDoc text
     * @throws IOException if the file cannot be read or is not a well-formed SPL document
     */
    public static String convert(Path label, Optional<String> source) throws IOException {
        try (InputStream in = Files.newInputStream(label)) {
            return convert(in, source);
        }
    }

    /**
     * Converts a label read from a stream.
     *
     * @param label  the SPL document
     * @param source where the label came from, written as {@code :spl-source:} when present
     * @return the AsciiDoc text
     * @throws IOException if the stream cannot be read or is not a well-formed SPL document
     */
    public static String convert(InputStream label, Optional<String> source) throws IOException {
        Element root = parse(label).getDocumentElement();
        List<String> lines = new ArrayList<>();
        lines.add("= " + title(root));
        lines.add(":spl-set-id: " + attribute(child(root, "setId"), "root"));
        lines.add(":spl-version: " + attribute(child(root, "versionNumber"), "value"));
        lines.add(":spl-effective-time: " + attribute(child(root, "effectiveTime"), "value"));
        lines.add(":spl-document-type: " + attribute(child(root, "code"), "displayName"));
        source.ifPresent(text -> lines.add(":spl-source: " + text));
        lines.add("");
        Writer writer = new Writer(lines);
        Optional<Element> body = child(root, "component").flatMap(component -> child(component, "structuredBody"));
        if (body.isPresent()) {
            for (Element section : sections(body.get())) {
                section(writer, section, 1);
            }
        }
        return String.join("\n", lines).stripTrailing() + "\n";
    }

    /** The lines being written, and the LOINC code waiting for the next block of its section. */
    private static final class Writer {

        private final List<String> lines;
        private Optional<String> pendingCode = Optional.empty();

        private Writer(List<String> lines) {
            this.lines = lines;
        }

        private void attribute() {
            pendingCode.ifPresent(code -> lines.add("[loinc=" + code + "]"));
            pendingCode = Optional.empty();
        }

        private void block(List<String> block) {
            if (block.isEmpty()) {
                return;
            }
            attribute();
            lines.addAll(block);
            lines.add("");
        }
    }

    private static void section(Writer writer, Element section, int depth) {
        Optional<String> code = child(section, "code").map(element -> element.getAttribute("code"))
                .filter(value -> !value.isEmpty());
        if (code.isPresent() && SKIPPED_SECTIONS.contains(code.get())) {
            return;
        }
        String title = child(section, "title").map(SplToAsciiDoc::inline).map(SplToAsciiDoc::normalize).orElse("");
        writer.pendingCode = code;
        if (!title.isEmpty()) {
            writer.attribute();
            writer.lines.add("=".repeat(Math.min(depth + 1, DEEPEST_HEADING)) + " " + title);
            writer.lines.add("");
        }
        child(section, "text").ifPresent(text -> textBlocks(writer, text));
        for (Element nested : sections(section)) {
            section(writer, nested, title.isEmpty() ? depth : depth + 1);
        }
        writer.pendingCode = Optional.empty();
    }

    private static void textBlocks(Writer writer, Element text) {
        for (Element child : elements(text)) {
            String name = child.getLocalName();
            switch (name) {
                case "paragraph" -> writer.block(paragraph(normalize(inline(child))));
                case "list" -> writer.block(listLines(child, 1));
                case "table" -> writer.block(tableLines(child));
                default -> {
                    if (!DROPPED_INLINE.contains(name)) {
                        writer.block(paragraph(normalize(inline(child))));
                    }
                }
            }
        }
    }

    private static List<String> paragraph(String text) {
        return text.isEmpty() ? List.of() : List.of(text);
    }

    private static List<String> listLines(Element list, int depth) {
        List<String> lines = new ArrayList<>();
        for (Element item : children(list, "item")) {
            String text = normalize(inline(item));
            if (!text.isEmpty()) {
                lines.add("*".repeat(depth) + " " + text);
            }
            for (Element nested : children(item, "list")) {
                lines.addAll(listLines(nested, depth + 1));
            }
        }
        return lines;
    }

    private static List<String> tableLines(Element table) {
        List<String> lines = new ArrayList<>();
        child(table, "caption").map(SplToAsciiDoc::inline).map(SplToAsciiDoc::normalize)
                .filter(caption -> !caption.isEmpty())
                .ifPresent(caption -> lines.add("." + caption));
        lines.add("|===");
        for (String part : List.of("thead", "tbody", "tfoot")) {
            for (Element group : children(table, part)) {
                for (Element row : children(group, "tr")) {
                    row(row).ifPresent(lines::add);
                }
            }
        }
        for (Element row : children(table, "tr")) {
            row(row).ifPresent(lines::add);
        }
        lines.add("|===");
        return lines;
    }

    private static Optional<String> row(Element row) {
        List<String> cells = new ArrayList<>();
        boolean any = false;
        for (Element cell : elements(row)) {
            if (cell.getLocalName().equals("td") || cell.getLocalName().equals("th")) {
                String text = normalize(inline(cell)).replace('|', '/');
                any |= !text.isEmpty();
                cells.add(text);
            }
        }
        return any ? Optional.of("| " + String.join(" | ", cells)) : Optional.empty();
    }

    /**
     * The text of an element with its inline markup flattened: a line break becomes a space, and
     * footnotes, images, and lists or tables nested in it are dropped.
     */
    private static String inline(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            switch (node.getNodeType()) {
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(node.getNodeValue());
                case Node.ELEMENT_NODE -> {
                    String name = node.getLocalName();
                    if (name.equals("br")) {
                        text.append(' ');
                    } else if (!DROPPED_INLINE.contains(name) && !BLOCKS_IN_TEXT.contains(name)) {
                        text.append(inline((Element) node));
                    }
                }
                default -> {
                }
            }
        }
        return text.toString();
    }

    private static String normalize(String text) {
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    /** The document's title: the first product's name, with its generic name in lower case. */
    private static String title(Element root) {
        NodeList products = root.getElementsByTagNameNS(HL7_V3, "manufacturedProduct");
        for (int i = 0; i < products.getLength(); i++) {
            Element product = (Element) products.item(i);
            if (product.getParentNode() instanceof Element parent && "manufacturedProduct".equals(parent.getLocalName())) {
                String name = child(product, "name").map(SplToAsciiDoc::inline).map(SplToAsciiDoc::normalize).orElse("");
                NodeList generics = product.getElementsByTagNameNS(HL7_V3, "genericMedicine");
                if (generics.getLength() > 0) {
                    Optional<String> generic = child((Element) generics.item(0), "name").map(SplToAsciiDoc::inline)
                            .map(SplToAsciiDoc::normalize);
                    if (generic.isPresent()) {
                        return name + " (" + generic.get().toLowerCase(Locale.ROOT) + ")";
                    }
                }
                return name;
            }
        }
        return child(root, "title").map(SplToAsciiDoc::inline).map(SplToAsciiDoc::normalize).orElse("Product label");
    }

    private static String attribute(Optional<Element> element, String name) {
        return element.map(found -> found.getAttribute(name)).orElse("");
    }

    private static List<Element> sections(Element parent) {
        List<Element> sections = new ArrayList<>();
        for (Element component : children(parent, "component")) {
            sections.addAll(children(component, "section"));
        }
        return sections;
    }

    private static Optional<Element> child(Element parent, String localName) {
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        for (Element element : elements(parent)) {
            if (localName.equals(element.getLocalName()) && HL7_V3.equals(element.getNamespaceURI())) {
                found.add(element);
            }
        }
        return found;
    }

    private static List<Element> elements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Document parse(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("not a well-formed SPL document: " + e.getMessage(), e);
        }
    }

    private static List<Path> xmlFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
    }
}
