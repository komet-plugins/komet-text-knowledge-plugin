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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import network.ike.komet.text.recognize.Candidate;
import network.ike.komet.text.recognize.Description;
import network.ike.komet.text.recognize.DescriptionKind;
import network.ike.komet.text.recognize.DescriptionMaps;
import network.ike.komet.text.recognize.Descriptions;
import network.ike.komet.text.recognize.Reading;
import network.ike.komet.text.recognize.Recognition;
import network.ike.komet.text.recognize.Recognizer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The description stage against a store holding the IKE starter set: the descriptions it reads,
 * a sentence naming a starter-set concept recognized as that concept, the same reading twice, and
 * the headless run over a small collection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DescriptionStageIT {

    private static final File PB_STARTER_DATA = new File("target/data/ike-starter-set-reasoned-pb.zip");

    private ViewCalculator view;
    private List<Description> descriptions;

    @TempDir
    Path folder;

    @BeforeAll
    void startStore() {
        assertTrue(PB_STARTER_DATA.exists(), "Starter data must be present at " + PB_STARTER_DATA.getAbsolutePath());
        PrimitiveData.selectControllerByName("Load Memory-Only 64-bit Store");
        PrimitiveData.start();
        long count = new LoadEntitiesFromProtobufFile(PB_STARTER_DATA).compute().getTotalCount();
        assertTrue(count > 0, "the starter set loads");
        view = Calculators.View.Default();
        descriptions = Descriptions.read(view);
    }

    @AfterAll
    void stopStore() {
        PrimitiveData.stop();
    }

    @Test
    void readsFullyQualifiedNamesAndRegularNames() {
        assertFalse(descriptions.isEmpty(), "the starter set has descriptions");
        assertTrue(descriptions.stream().anyMatch(d -> d.kind() == DescriptionKind.FULLY_QUALIFIED_NAME));
        assertTrue(descriptions.stream().anyMatch(d -> d.kind() == DescriptionKind.REGULAR_NAME));
    }

    @Test
    void recognizesAStarterSetConceptInASentence() {
        Description named = longestRegularName();
        String sentence = "The note records " + named.text() + " today.";
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(descriptions))) {
            Reading reading = recognizer.read(sentence);
            Optional<Recognition> found = reading.recognitions().stream()
                    .filter(r -> r.words().equalsIgnoreCase(named.text()))
                    .findFirst();
            assertTrue(found.isPresent(), "the words of " + named.text() + " are recognized in: " + reading);
            assertTrue(found.get().candidates().stream().map(Candidate::conceptNid).toList().contains(named.conceptNid()));
            assertEquals(reading, recognizer.read(sentence), "the same text gives the same reading");
        }
    }

    @Test
    void namesThePartOfTheHierarchy() {
        Description named = longestRegularName();
        String branch = new Branches(view, view::getPreferredDescriptionTextWithFallbackOrNid).apply(named.conceptNid());
        assertFalse(branch.isBlank());
    }

    @Test
    void theHeadlessRunWritesTheReportAndTheTable() throws Exception {
        Description named = longestRegularName();
        Path documents = Files.createDirectories(folder.resolve("documents"));
        Files.writeString(documents.resolve("note.adoc"), String.join("\n",
                "= A note",
                "",
                "The patient reports " + named.text() + ".",
                "Nothing else of note."));
        Path out = folder.resolve("out");
        Concordance.Report report = ConcordanceRun.measure(view, ConcordanceRun.domain(view, Optional.empty()),
                ConcordanceRun.Hierarchy.INFERRED, new ConcordanceRun.Source.Folder(documents), out);
        assertEquals(1, report.documents());
        assertEquals(3, report.sentences());
        assertTrue(report.sentencesWithReference() >= 1);
        assertTrue(Files.readString(out.resolve("concordance.adoc")).startsWith("= Concordance of documents"));
        List<String> rows = Files.readAllLines(out.resolve(ConcordanceRun.RECOGNITIONS_TABLE));
        assertTrue(rows.size() >= 2);
        assertTrue(rows.getFirst().endsWith("\tanchor"), rows.getFirst());
        assertTrue(rows.get(1).endsWith("\t"), "a file read as text gives no anchor: " + rows.get(1));
        assertTrue(Files.readString(out.resolve(ConcordanceRun.LEFT_OUT_TABLE)).startsWith("concept\tdescription\twhy\n"));
        assertEquals("file\twhy\n", Files.readString(out.resolve(ConcordanceRun.NOT_READ_TABLE)));
        assertTrue(report.notRead().isEmpty());
    }

    /** The regular name with the most words, so the test names a concept by more than one word. */
    private Description longestRegularName() {
        return descriptions.stream()
                .filter(d -> d.kind() == DescriptionKind.REGULAR_NAME && !d.caseSignificant())
                .filter(d -> d.text().chars().allMatch(c -> Character.isLetter(c) || c == ' '))
                .max(Comparator.comparingInt((Description d) -> d.text().split(" ").length)
                        .thenComparing(Description::text))
                .orElseThrow();
    }
}
