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

import network.ike.komet.text.concordance.AsciiDocSentences;
import network.ike.komet.text.concordance.Sentence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conversion of a product label to AsciiDoc, on a small label written for the test: what it
 * keeps, what it leaves out, and that the sentence reader reads the result.
 */
class SplToAsciiDocTest {

    private static final String LABEL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document xmlns="urn:hl7-org:v3">
              <code code="34391-3" displayName="HUMAN PRESCRIPTION DRUG LABEL"/>
              <title>These highlights do not include all the information.</title>
              <effectiveTime value="20261101"/>
              <setId root="00000000-0000-0000-0000-000000001372"/>
              <versionNumber value="3"/>
              <component><structuredBody>
                <component><section>
                  <code code="48780-1" displayName="SPL PRODUCT DATA ELEMENTS SECTION"/>
                  <text><paragraph>Examplol 10 mg tablet product data</paragraph></text>
                  <subject><manufacturedProduct><manufacturedProduct>
                    <name>EXAMPLOL</name>
                    <asEntityWithGeneric><genericMedicine><name>EXAMPLOL HYDROCHLORIDE</name></genericMedicine></asEntityWithGeneric>
                  </manufacturedProduct></manufacturedProduct></subject>
                </section></component>
                <component><section>
                  <code code="34067-9" displayName="INDICATIONS &amp; USAGE SECTION"/>
                  <title>1 INDICATIONS AND USAGE</title>
                  <text>
                    <paragraph>EXAMPLOL is indicated for <content styleCode="bold">hypertension</content>
                      in adults.<footnote>A footnote the text leaves out.</footnote></paragraph>
                    <list>
                      <item>with chest pain<list><item>at rest</item></list></item>
                      <item>with C<sub>max</sub> above 2<br/>mg/L</item>
                    </list>
                  </text>
                  <excerpt><highlight><text><paragraph>A highlight that repeats the text.</paragraph></text></highlight></excerpt>
                  <component><section>
                    <code code="42229-5" displayName="SPL UNCLASSIFIED SECTION"/>
                    <text><paragraph>An untitled section's paragraph.</paragraph></text>
                  </section></component>
                </section></component>
                <component><section>
                  <code code="34084-4" displayName="ADVERSE REACTIONS SECTION"/>
                  <title>6 ADVERSE REACTIONS</title>
                  <component><section>
                    <code code="42229-5"/>
                    <title>6.1 Clinical Trials Experience</title>
                    <text>
                      <table>
                        <caption>Table 1: Adverse Reactions</caption>
                        <thead><tr><th>Reaction</th><th>Placebo | %</th></tr></thead>
                        <tbody><tr><td>Headache</td><td>4</td></tr></tbody>
                      </table>
                    </text>
                  </section></component>
                </section></component>
              </structuredBody></component>
            </document>
            """;

    private static String convert(String xml) throws IOException {
        return SplToAsciiDoc.convert(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                Optional.of("a test"));
    }

    @Test
    void theDocumentKeepsWhatTheLabelCodes() throws IOException {
        String adoc = convert(LABEL);
        assertTrue(adoc.startsWith("""
                = EXAMPLOL (examplol hydrochloride)
                :spl-set-id: 00000000-0000-0000-0000-000000001372
                :spl-version: 3
                :spl-effective-time: 20261101
                :spl-document-type: HUMAN PRESCRIPTION DRUG LABEL
                :spl-source: a test
                """), adoc);
        assertTrue(adoc.contains("[loinc=34067-9]\n== 1 INDICATIONS AND USAGE\n"), adoc);
        assertTrue(adoc.contains("[loinc=34084-4]\n== 6 ADVERSE REACTIONS\n"), adoc);
        assertTrue(adoc.contains("[loinc=42229-5]\n=== 6.1 Clinical Trials Experience\n"), adoc);
        assertTrue(adoc.contains("[loinc=42229-5]\nAn untitled section's paragraph.\n"), adoc);
    }

    @Test
    void textBecomesParagraphsListsAndTables() throws IOException {
        String adoc = convert(LABEL);
        assertTrue(adoc.contains("EXAMPLOL is indicated for hypertension in adults.\n"), adoc);
        assertTrue(adoc.contains("* with chest pain\n** at rest\n* with Cmax above 2 mg/L\n"), adoc);
        assertTrue(adoc.contains(".Table 1: Adverse Reactions\n|===\n| Reaction | Placebo / %\n| Headache | 4\n|===\n"), adoc);
    }

    @Test
    void productDataHighlightsAndFootnotesAreLeftOut() throws IOException {
        String adoc = convert(LABEL);
        assertFalse(adoc.contains("product data"), adoc);
        assertFalse(adoc.contains("48780-1"), adoc);
        assertFalse(adoc.contains("highlight"), adoc);
        assertFalse(adoc.contains("footnote"), adoc);
    }

    @Test
    void theSentenceReaderReadsTheResultBySection() throws IOException {
        List<Sentence> sentences = AsciiDocSentences.read(convert(LABEL).lines().toList(), "examplol.adoc");
        assertTrue(sentences.contains(new Sentence("examplol.adoc", 2, "1 INDICATIONS AND USAGE",
                "EXAMPLOL is indicated for hypertension in adults.")), sentences.toString());
        assertTrue(sentences.stream().anyMatch(s -> s.section().equals("6.1 Clinical Trials Experience")
                && s.text().equals("Headache")), sentences.toString());
    }

    @Test
    void aDocumentTypeDeclarationIsRefused() {
        String withDoctype = LABEL.replace("<document ", "<!DOCTYPE document [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>\n<document ");
        assertThrows(IOException.class, () -> convert(withDoctype));
    }

    @Test
    void aFolderOfLabelsIsConvertedFromTheCommandLine(@TempDir Path folder) throws IOException {
        Path labels = Files.createDirectories(folder.resolve("labels"));
        Files.writeString(labels.resolve("examplol.xml"), LABEL);
        Path out = folder.resolve("out");
        SplToAsciiDoc.main(new String[]{labels.toString(), out.toString(), "--source", "a test"});
        assertEquals(convert(LABEL), Files.readString(out.resolve("examplol.adoc")));
    }
}
