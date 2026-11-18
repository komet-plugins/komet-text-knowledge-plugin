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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import network.ike.komet.document.DecisionFigure;
import network.ike.komet.document.GitDocumentMaterializer;
import network.ike.komet.document.SegmentNarrative;
import network.ike.komet.document.Span;
import network.ike.komet.document.StructuredBlockKind;
import network.ike.komet.text.matches.DescriptionFingerprint;
import network.ike.komet.text.matches.Judgment;
import network.ike.komet.text.matches.MatchRow;
import network.ike.komet.text.matches.MatchTable;
import network.ike.komet.text.matches.MatchTables;
import network.ike.komet.text.matches.RunIdentity;
import network.ike.komet.text.recognize.Candidate;
import network.ike.komet.text.recognize.Description;
import network.ike.komet.text.recognize.DescriptionKind;
import network.ike.komet.text.recognize.DescriptionMaps;
import network.ike.komet.text.recognize.Descriptions;
import network.ike.komet.text.recognize.Recognition;
import network.ike.komet.text.recognize.Recognizer;
import network.ike.komet.text.ui.MatchesBlockKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recognition in a stamped document as the store holds it: a note written to git and materialized
 * into a store holding the IKE starter set, its sentences read back with their identifiers, a
 * recognition anchored to the column range of its own sentence, the anchor still naming the same
 * words after another sentence of the note is revised, and the headless run over a repository,
 * with the files it does not read listed by why, and the match table it writes into a document,
 * whose judged rows a new run keeps.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoredSentencesIT {

    private static final File PB_STARTER_DATA = new File("target/data/ike-starter-set-reasoned-pb.zip");
    private static final String FILE = "visit.adoc";

    /** A note of two paragraphs; the second names a concept after a sentence of its own. */
    private static final String NOTE = """
            :topic-id: dev-recognized-%s

            = Visit

            %s

            Her daughter came along. The patient reports %s today.
            """;

    private List<Description> descriptions;
    private Description named;

    @TempDir
    Path root;

    @BeforeAll
    void startStore() {
        assertTrue(PB_STARTER_DATA.exists(), "Starter data must be present at " + PB_STARTER_DATA.getAbsolutePath());
        PrimitiveData.selectControllerByName("Load Memory-Only 64-bit Store");
        PrimitiveData.start();
        assertTrue(new LoadEntitiesFromProtobufFile(PB_STARTER_DATA).compute().getTotalCount() > 0);
        descriptions = Descriptions.read(Calculators.View.Default());
        named = longestRegularName();
    }

    @AfterAll
    void stopStore() {
        ModuleKinds.clear();
        EntityBoundaries.clear();
        PrimitiveData.stop();
    }

    @Test
    void theSentencesAreTheOnesTheStoreHolds() throws Exception {
        PublicId document = store("sentences", "The visit was brief.");
        StoredSentences stored = StoredSentences.read(document, FILE).orElseThrow();
        List<String> texts = stored.sentences().stream().map(Sentence::text).toList();
        assertEquals(stored.narrative().sentences().stream().map(s -> PlainText.of(s.text()).text())
                .filter(text -> !text.isEmpty()).toList(), texts);
        assertTrue(texts.contains("Her daughter came along."), texts.toString());
        assertTrue(texts.contains("The patient reports " + named.text() + " today."), texts.toString());
        for (Sentence sentence : stored.sentences()) {
            assertEquals(FILE, sentence.document());
            String id = stored.sentenceId(sentence);
            SegmentNarrative.Sentence held = stored.narrative().sentences().stream()
                    .filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            assertEquals(held.text(), stored.storedText(sentence));
            assertEquals(PlainText.of(held.text()).text(), sentence.text());
        }
    }

    @Test
    void markupIsNotReadButTheAnchorCountsColumnsOfTheStoredText() throws Exception {
        Path dir = repositoryWith("markup", """
                :topic-id: dev-recognized-markup

                = Visit

                indexterm:[%1$s]
                Her daughter came along. The patient reports ((%1$s)) today.
                """.formatted(named.text()));
        StoredSentences stored = StoredSentences.read(GitDocumentMaterializer.materialize(dir, dir.resolve(FILE)).anchor(),
                FILE).orElseThrow();
        List<String> texts = stored.sentences().stream().map(Sentence::text).toList();
        assertTrue(texts.contains("Her daughter came along."), texts.toString());
        assertTrue(texts.contains("The patient reports " + named.text() + " today."), texts.toString());
        assertTrue(texts.stream().noneMatch(text -> text.contains("indexterm") || text.contains("((")), texts.toString());

        List<AnchoredRecognition> recognized;
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(descriptions))) {
            recognized = stored.recognize(recognizer).stream()
                    .filter(r -> r.recognition().words().equals(named.text()))
                    .toList();
        }
        assertEquals(1, recognized.size(), "the index entry's term is not read: " + recognized);
        AnchoredRecognition found = recognized.getFirst();
        String storedText = stored.storedText(found.sentence());
        assertTrue(storedText.contains("((" + named.text() + "))"), storedText);
        int column = storedText.indexOf("((" + named.text() + "))") + 2;
        assertEquals("segment:" + stored.sentenceId(found.sentence()) + "[" + column + ","
                + (column + named.text().length()) + "]=" + named.text(), found.anchor());
        Span span = stored.narrative().resolve(found.anchor()).orElseThrow();
        assertEquals(named.text(), stored.narrative().narrative().substring(span.start(), span.end()));
    }

    @Test
    void aRecognitionAnchorsToTheColumnsOfItsOwnSentence() throws Exception {
        PublicId document = store("anchors", "The visit was brief.");
        StoredSentences stored = StoredSentences.read(document, FILE).orElseThrow();
        AnchoredRecognition found = recognitionOfNamed(stored);

        Sentence sentence = found.sentence();
        int column = sentence.text().indexOf(named.text());
        assertTrue(column > 0, "the words are not at the start of their sentence");
        assertEquals("segment:" + stored.sentenceId(sentence) + "[" + column + "," + (column + named.text().length())
                + "]=" + named.text(), found.anchor());
        Span span = stored.narrative().resolve(found.anchor()).orElseThrow();
        assertEquals(named.text(), stored.narrative().narrative().substring(span.start(), span.end()));
        assertTrue(found.recognition().candidates().stream().map(Candidate::conceptNid).toList()
                .contains(named.conceptNid()));
    }

    @Test
    void theAnchorStillNamesTheWordsAfterAnotherSentenceIsRevised() throws Exception {
        Path dir = repository("revised", "The visit was brief.");
        Path file = dir.resolve(FILE);
        PublicId document = GitDocumentMaterializer.materialize(dir, file).anchor();
        String before = recognitionOfNamed(StoredSentences.read(document, FILE).orElseThrow()).anchor();

        Files.writeString(file, NOTE.formatted("revised", "The visit took most of an hour.", named.text()));
        git(dir, "commit", "-q", "-am", "Revise the first paragraph");
        GitDocumentMaterializer.materialize(dir, file);

        StoredSentences after = StoredSentences.read(document, FILE).orElseThrow();
        assertTrue(after.narrative().narrative().contains("most of an hour"), "the store holds the revision");
        Span span = after.narrative().resolve(before).orElseThrow();
        assertEquals(named.text(), after.narrative().narrative().substring(span.start(), span.end()));
        assertEquals(before, recognitionOfNamed(after).anchor(), "read again, the words anchor alike");
    }

    @Test
    void theRunnerReadsARepositoryFromTheStore() throws Exception {
        Path dir = repository("run", "The visit was brief.");
        Files.writeString(dir.resolve("assembly.adoc"), "= Notes\n\ninclude::visit.adoc[]\n");
        Files.writeString(dir.resolve("code.adoc"), ":topic-id: dev-recognized-code\n\n----\nx = 1\n----\n");
        git(dir, "add", "assembly.adoc", "code.adoc");
        git(dir, "commit", "-q", "-m", "Add an assembly and a file of code");
        Files.writeString(dir.resolve("draft.adoc"), "= Draft\n\nNot yet committed.\n");
        Files.writeString(Files.createDirectories(dir.resolve("target")).resolve("built.adoc"), "= Built\n\nGenerated.\n");
        Path out = root.resolve("run-out");

        ViewCalculator view = Calculators.View.Default();
        Concordance.Report report = ConcordanceRun.measure(view, ConcordanceRun.domain(view, Optional.empty()),
                ConcordanceRun.Hierarchy.INFERRED, new ConcordanceRun.Source.Repository(dir, dir), out);

        StoredSentences visit = StoredSentences.read(GitDocumentMaterializer.materialize(dir, dir.resolve(FILE)).anchor(),
                FILE).orElseThrow();
        assertEquals(1, report.documents());
        assertEquals(visit.sentences().size(), report.sentences());
        assertEquals(Map.of(NotRead.ASSEMBLY, 1, NotRead.NOT_COMMITTED, 1, NotRead.NO_STORED_SENTENCES, 1),
                report.notRead());
        assertEquals(List.of("file\twhy", "assembly.adoc\tASSEMBLY", "code.adoc\tNO_STORED_SENTENCES",
                "draft.adoc\tNOT_COMMITTED"), Files.readAllLines(out.resolve(ConcordanceRun.NOT_READ_TABLE)));

        String expected = recognitionOfNamed(visit).anchor();
        List<String> rows = Files.readAllLines(out.resolve(ConcordanceRun.RECOGNITIONS_TABLE));
        assertTrue(rows.stream().anyMatch(row -> row.startsWith(FILE + "\t") && row.endsWith("\t" + expected)),
                expected + " is not in " + rows);
        String concordance = Files.readString(out.resolve("concordance.adoc"));
        assertTrue(concordance.startsWith("= Concordance of run, read from the store\n"), concordance);
        assertTrue(concordance.contains("| Files not read (assemblies, not committed, no stored sentences) | 1, 1, 1\n"),
                concordance);
    }

    @Test
    void theRunnerWritesAMatchTableAndARerunKeepsWhatWasJudged() throws Exception {
        // A second name in the note, so the run has a match left over once one is judged, whichever
        // name of the knowledge base is the longest.
        Path dir = repository("tables", "The clinician asked about " + anotherRegularName().text() + ".");
        GitDocumentMaterializer.Result before = GitDocumentMaterializer.materialize(dir, dir.resolve(FILE));
        StoredSentences stored = StoredSentences.read(before.anchor(), FILE).orElseThrow();
        AnchoredRecognition expected = recognitionOfNamed(stored);
        MatchRow.Place place = stored.place(expected.sentence(), expected.recognition());
        ViewCalculator view = Calculators.View.Default();
        ConcordanceRun.Source.Repository source = new ConcordanceRun.Source.Repository(dir, dir,
                Optional.of(new ConcordanceRun.Tables(RunIdentity.DEVELOPMENT, "ike-starter-set reasoned")));

        Concordance.Report first = ConcordanceRun.measure(view, ConcordanceRun.domain(view, Optional.empty()),
                ConcordanceRun.Hierarchy.INFERRED, source, root.resolve("tables-first"));

        List<MatchTables.Placed> written = MatchTables.find(Files.readAllLines(dir.resolve(FILE)));
        assertEquals(1, written.size());
        MatchTable table = written.getFirst().table();
        assertEquals(first.recognitions(), table.rows().size());
        assertTrue(table.run().development());
        assertEquals("description 1", table.run().stages());
        assertEquals("ike-starter-set reasoned", table.run().data());
        assertTrue(table.run().splitter().startsWith("stamp-adoc-core 1."), table.run().splitter());
        assertTrue(table.run().descriptions().startsWith(DescriptionFingerprint.PREFIX), table.run().descriptions());
        MatchRow row = table.rows().stream().filter(r -> r.place().equals(place)).findFirst()
                .orElseThrow(() -> new AssertionError(place + " has no row in " + table.rows()));
        assertEquals(named.text(), row.words());
        assertTrue(row.candidates().stream().anyMatch(c -> c.uuid().equals(PrimitiveData.publicId(named.conceptNid()).asUuidArray()[0])),
                row.candidates().toString());

        // the page reads each match in its sentence, found in the store by the row's anchor
        List<DecisionFigure> printed = new MatchesBlockKind().figures(StructuredBlockKind.Source.of(String.join("\n", table.lines())),
                new StructuredBlockKind.Context(view));
        List<String> words = new ArrayList<>();
        List<String> contexts = new ArrayList<>();
        for (DecisionFigure figure : printed) {
            for (DecisionFigure.Mark mark : figure.marks()) {
                if (mark instanceof DecisionFigure.Text text && text.role().equals("matches-words")) {
                    words.add(text.text());
                } else if (mark instanceof DecisionFigure.Text text && text.role().equals("matches-context")) {
                    contexts.add(text.text());
                }
            }
        }
        assertTrue(words.contains(named.text()), words.toString());
        assertTrue(contexts.stream().noneMatch(text -> text.contains("not in the open store")), contexts.toString());
        // the words around the match come from its stored sentence, as many as the page's width lets stand: a long
        // concept name beside the stage and judgment columns may leave room for none, and the cut is then marked
        String sentence = "The patient reports " + named.text() + " today.";
        assertFalse(contexts.isEmpty(), "the sentence was found in the store");
        for (String context : contexts) {
            assertTrue(sentence.contains(context.replace(network.ike.komet.text.concordance.InContext.ELLIPSIS, "").strip()), context);
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(FILE)));
        MatchRow judged = row.judged(Judgment.AFFIRM, Optional.empty(), Optional.empty());
        lines.set(lines.indexOf(row.text()), judged.text());
        Files.write(dir.resolve(FILE), lines);
        git(dir, "commit", "-q", "-am", "Affirm a match");
        GitDocumentMaterializer.Result after = GitDocumentMaterializer.materialize(dir, dir.resolve(FILE));
        assertEquals(1, after.commits(), "the commit that wrote the table is materialized");
        assertEquals(0, after.references(), "its k: tokens are not stored as references");

        Concordance.Report second = ConcordanceRun.measure(view, ConcordanceRun.domain(view, Optional.empty()),
                ConcordanceRun.Hierarchy.INFERRED, source, root.resolve("tables-second"));

        assertEquals(first.sentences(), second.sentences(), "the committed table is not read as sentences");
        assertEquals(first.recognitions(), second.recognitions(), "nor are its concepts recognized");
        List<MatchTables.Placed> rerun = MatchTables.find(Files.readAllLines(dir.resolve(FILE)));
        assertEquals(2, rerun.size());
        assertEquals(List.of(judged), rerun.get(0).table().rows(), "the judged row stays in the table of its run");
        assertEquals(table.rows().size() - 1, rerun.get(1).table().rows().size(), "the new run leaves out the words judged");
        assertTrue(rerun.get(1).table().rows().stream().noneMatch(r -> r.place().equals(place)));
    }

    @Test
    void aSentenceOfAnotherDocumentIsRefused() throws Exception {
        StoredSentences stored = StoredSentences.read(store("refused", "The visit was brief."), FILE).orElseThrow();
        Sentence elsewhere = new Sentence("other.adoc", 0, "", stored.sentences().getFirst().text());
        assertThrows(IllegalArgumentException.class, () -> stored.sentenceId(elsewhere));
        Sentence first = stored.sentences().getFirst();
        Recognition beyond = new Recognition(0, first.text().length() + 1, first.text() + " ", recognitionOfNamed(stored)
                .recognition().candidates());
        assertThrows(IllegalArgumentException.class, () -> stored.anchor(first, beyond));
    }

    /** The recognition of the named concept's words, read from the stored sentences. */
    private AnchoredRecognition recognitionOfNamed(StoredSentences stored) {
        try (Recognizer recognizer = new Recognizer(DescriptionMaps.build(descriptions))) {
            List<AnchoredRecognition> recognized = stored.recognize(recognizer);
            return recognized.stream()
                    .filter(r -> r.recognition().words().equals(named.text()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(named.text() + " is not recognized in " + recognized));
        }
    }

    /** Writes the note to a new repository and materializes it; one topic per repository. */
    private PublicId store(String name, String firstParagraph) throws Exception {
        Path dir = repository(name, firstParagraph);
        return GitDocumentMaterializer.materialize(dir, dir.resolve(FILE)).anchor();
    }

    private Path repository(String name, String firstParagraph) throws Exception {
        return repositoryWith(name, NOTE.formatted(name, firstParagraph, named.text()));
    }

    /** A new repository whose one commit writes the note's text. */
    private Path repositoryWith(String name, String text) throws Exception {
        Path dir = Files.createDirectories(root.resolve(name));
        git(dir, "init", "-q", "-b", "main");
        git(dir, "config", "user.name", "Rita Reviewer");
        git(dir, "config", "user.email", "rita@example.test");
        git(dir, "config", "commit.gpgsign", "false");
        Files.writeString(dir.resolve(FILE), text);
        git(dir, "add", FILE);
        git(dir, "commit", "-q", "-m", "Write the visit note");
        return dir;
    }

    private static void git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
        }
    }

    /** The regular name with the most words, so the test names a concept by more than one word. */
    /** A regular name of another concept that shares no word with the longest one, so the two are found apart. */
    private Description anotherRegularName() {
        Set<String> itsWords = new HashSet<>(Arrays.asList(named.text().toLowerCase(Locale.ROOT).split(" ")));
        return descriptions.stream()
                .filter(d -> d.kind() == DescriptionKind.REGULAR_NAME && !d.caseSignificant())
                .filter(d -> d.conceptNid() != named.conceptNid())
                .filter(d -> d.text().chars().allMatch(c -> Character.isLetter(c) || c == ' '))
                .filter(d -> Arrays.stream(d.text().toLowerCase(Locale.ROOT).split(" ")).noneMatch(itsWords::contains))
                .max(Comparator.comparingInt((Description d) -> d.text().split(" ").length)
                        .thenComparing(Description::text))
                .orElseThrow();
    }

    private Description longestRegularName() {
        return descriptions.stream()
                .filter(d -> d.kind() == DescriptionKind.REGULAR_NAME && !d.caseSignificant())
                .filter(d -> d.text().chars().allMatch(c -> Character.isLetter(c) || c == ' '))
                .max(Comparator.comparingInt((Description d) -> d.text().split(" ").length)
                        .thenComparing(Description::text))
                .orElseThrow();
    }
}
