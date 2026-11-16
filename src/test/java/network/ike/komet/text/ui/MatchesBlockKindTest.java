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
package network.ike.komet.text.ui;

import design.knowledge.stamp.adoc.model.KonceptRef;
import dev.ikm.komet.layout.expand.KlExpandable;
import dev.ikm.komet.layout.expand.KlExpansionHost;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import network.ike.komet.document.DecisionFigure;
import network.ike.komet.document.StructuredBlockKind;
import network.ike.komet.document.StructuredBlocks;
import network.ike.komet.text.concordance.InContext;
import network.ike.komet.text.matches.Judgment;
import network.ike.komet.text.matches.MatchRow;
import network.ike.komet.text.matches.MatchTable;
import network.ike.komet.text.matches.PhraseEntry;
import network.ike.komet.text.matches.RunIdentity;
import network.ike.komet.text.recognize.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The match table's block kind: a block's source read as its table, its caption, the page's
 * figures, and the Reading view's control and its grow, drawn on Monocle's headless toolkit with no
 * store open.
 */
class MatchesBlockKindTest {

    private static final KonceptRef HEPATIC = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000001"), "Hepatic impairment");
    private static final KonceptRef LIVER = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000002"), "Liver disorder");
    private static final KonceptRef SEVERE = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000003"), "Severe hepatic impairment");
    private static final String SENTENCE = "0f1c2d3e-4f50-4617-8899-aabbccddeeff";
    private static final RunIdentity RUN = new RunIdentity(Instant.parse("2026-11-14T22:10:00Z"), RunIdentity.DEVELOPMENT,
            "description 1", "stamp-adoc-core 1.3.0-SNAPSHOT", "ike-starter-set", "Latest on Development path", "sha256:00ff");
    private static final KonceptRef DOSE = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000004"), "Dose");
    /** The sentence the matches are in: hepatic impairment twice, then dose. */
    private static final String TEXT = "Severe hepatic impairment bars it; hepatic impairment lowers the dose.";
    private static final int FIRST = TEXT.indexOf("hepatic impairment");
    private static final int SECOND = TEXT.lastIndexOf("hepatic impairment");
    private static final int DOSE_AT = TEXT.indexOf("dose");
    private static final MatchRow PROPOSED = MatchRow.proposed(SENTENCE, FIRST, FIRST + 18, "hepatic impairment", List.of(HEPATIC, LIVER),
            Stage.DESCRIPTION, OptionalDouble.empty());
    private static final MatchRow REFINED = MatchRow.proposed(SENTENCE, SECOND, SECOND + 18, "hepatic impairment", List.of(HEPATIC, LIVER),
            Stage.DESCRIPTION, OptionalDouble.empty()).judged(Judgment.REFINE, Optional.of(SEVERE), Optional.of("assistant"));
    private static final MatchRow DOSED = MatchRow.proposed(SENTENCE, DOSE_AT, DOSE_AT + 4, "dose", List.of(DOSE),
            Stage.DESCRIPTION, OptionalDouble.empty());
    private static final MatchTable TABLE = new MatchTable(RUN, List.of(PROPOSED, REFINED, DOSED));

    private final MatchesBlockKind kind = new MatchesBlockKind();

    @BeforeAll
    static void toolkit() {
        HeadlessFx.select();
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Started by another test class in this JVM.
        } catch (RuntimeException | Error noToolkit) {
            Assumptions.abort("no JavaFX toolkit here: " + noToolkit);
        }
    }

    @Test
    void theKindIsFoundByItsStyle() {
        assertEquals(MatchTable.STYLE, kind.style());
        assertEquals("Match table", kind.menuName());
        assertTrue(kind.editor(source(TABLE), new StructuredBlockKind.Context(null)).isEmpty(), "edited as text");
        assertEquals(Optional.empty(), kind.skeleton(new StructuredBlockKind.Context(null)), "never inserted from the menu");
        assertInstanceOf(MatchesBlockKind.class, StructuredBlocks.kindFor(MatchTable.STYLE).orElseThrow(),
                "found through the service loader");
    }

    @Test
    void aBlocksSourceIsReadAsItsTable() {
        assertEquals(TABLE, MatchesBlockKind.read(source(TABLE)));
        List<String> titled = new ArrayList<>(List.of(".Matches"));
        titled.addAll(TABLE.lines());
        assertEquals(TABLE, MatchesBlockKind.read(StructuredBlockKind.Source.of(String.join("\n", titled))));
        assertThrows(IllegalArgumentException.class, () -> MatchesBlockKind.read(
                StructuredBlockKind.Source.of(RUN.attributeLine() + "\n----\nnot a row\n----")));
    }

    @Test
    void theCaptionSaysWhatTheTableHoldsAndWhatMadeIt() {
        assertEquals("2 phrases matched 3 times; 1 judged (1 refined), 2 to judge. Matched most often: Hepatic impairment (2).",
                MatchesBlockKind.contents(TABLE));
        assertEquals("2 phrases matched 2 times, none judged yet.", MatchesBlockKind.contents(new MatchTable(RUN, List.of(PROPOSED, DOSED))),
                "no concept matched more than once: none is named");
        assertEquals("1 phrase matched once, none judged yet.", MatchesBlockKind.contents(new MatchTable(RUN, List.of(DOSED))));
        assertEquals("1 phrase matched once; 1 judged (1 refined), all of them.",
                MatchesBlockKind.contents(new MatchTable(RUN, List.of(REFINED))));
        MatchRow rejected = DOSED.judged(Judgment.REJECT, Optional.empty(), Optional.of("KEC"));
        assertEquals("2 phrases matched 3 times; 2 judged (1 refined, 1 rejected), 1 to judge. Matched most often: Hepatic impairment (2).",
                MatchesBlockKind.contents(new MatchTable(RUN, List.of(PROPOSED, REFINED, rejected))), "the judgments in their order");
        assertEquals("Run 2026-11-14 22:10 UTC · matcher development · description 1", MatchesBlockKind.provenance(RUN));
        assertTrue(MatchesBlockKind.identity(RUN).contains("Descriptions: sha256:00ff"));
        List<PhraseEntry> entries = PhraseEntry.of(TABLE.rows());
        assertEquals("2 matches · 1 judged", MatchesBlockKind.count(entries.get(0)));
        assertEquals("1 match", MatchesBlockKind.count(entries.get(1)));
        assertEquals("↓ refine", MatchesBlockKind.mark(REFINED.judgment()));
        assertEquals("", MatchesBlockKind.mark(PROPOSED.judgment()));
        assertEquals("↓ refine → Severe hepatic impairment", MatchesBlockKind.printedJudgment(REFINED));
        assertEquals("", MatchesBlockKind.printedJudgment(PROPOSED));
        assertEquals("✗ reject", MatchesBlockKind.printedJudgment(
                PROPOSED.judged(Judgment.REJECT, Optional.empty(), Optional.empty())));
    }

    @Test
    void aMatchNamesTheStageThatProposedItAndItsScore() {
        assertEquals("description", MatchesBlockKind.source(PROPOSED), "the description stage gives no score");
        assertEquals("description 0.82", MatchesBlockKind.source(MatchRow.proposed(SENTENCE, FIRST, FIRST + 18, "hepatic impairment",
                List.of(HEPATIC), Stage.DESCRIPTION, OptionalDouble.of(0.8234))));
        assertEquals("", MatchesBlockKind.source(MatchRow.missed(SENTENCE, 0, 6, "Severe", SEVERE, Optional.of("KEC"))),
                "a missed match was proposed by no stage");
        assertEquals("description", MatchesBlockKind.source(REFINED), "judging a match keeps its stage");
    }

    @Test
    void theConceptsMatchedMostOftenAreCountedByTheConceptProposed() {
        MatchRow missed = MatchRow.missed(SENTENCE, 0, 6, "Severe", SEVERE, Optional.of("KEC"));
        List<MatchRow> rows = List.of(PROPOSED, DOSED, missed, REFINED, DOSED, missed, DOSED, missed);
        assertEquals(List.of(Map.entry(DOSE, 3), Map.entry(SEVERE, 3), Map.entry(HEPATIC, 2)), MatchesBlockKind.mostMatched(rows),
                "most often first, ties in the table's order; a missed match counts for the concept its rater named");
        assertEquals(List.of(), MatchesBlockKind.mostMatched(List.of(PROPOSED, DOSED)), "a concept matched once is not named");
        KonceptRef fourth = new KonceptRef(UUID.fromString("6111dc03-0000-4000-8000-000000000005"), "Fourth");
        MatchRow other = MatchRow.proposed(SENTENCE, 0, 6, "Fourth", List.of(fourth), Stage.DESCRIPTION, OptionalDouble.empty());
        List<MatchRow> more = new ArrayList<>(rows);
        more.addAll(List.of(other, other));
        assertEquals(MatchesBlockKind.MOST_MATCHED, MatchesBlockKind.mostMatched(more).size(), "at most three are named");
    }

    @Test
    void aPagePrintsTheTableByPhraseEachMatchNumberedInItsSentence() {
        List<DecisionFigure> figures = kind.figures(source(TABLE), new StructuredBlockKind.Context(null));
        assertEquals(3, figures.size(), "the caption, then a figure for each phrase");
        DecisionFigure caption = figures.getFirst();
        assertEquals(List.of(MatchesBlockKind.TITLE), texts(caption, "matches-title"));
        assertEquals(MatchesBlockKind.contents(TABLE), String.join(" ", texts(caption, "matches-contents")));
        assertEquals(List.of(MatchesBlockKind.provenance(RUN)), texts(caption, "matches-provenance"));

        KonceptRef[] many = new KonceptRef[12];
        for (int i = 0; i < many.length; i++) {
            many[i] = new KonceptRef(new UUID(7, i), "A concept with a name long enough to wrap " + i);
        }
        List<MatchRow> twice = new ArrayList<>();
        for (KonceptRef concept : many) {
            MatchRow row = MatchRow.proposed(SENTENCE, 0, 6, concept.label(), List.of(concept), Stage.DESCRIPTION, OptionalDouble.empty());
            twice.add(row);
            twice.add(row);
        }
        DecisionFigure wrapped = MatchesBlockKind.printedCaption(new MatchTable(RUN, twice), 9);
        List<String> lines = texts(wrapped, "matches-contents");
        assertTrue(lines.size() > 1, "what the table holds wraps: " + lines);
        for (String part : lines) {
            assertTrue(network.ike.komet.document.DecisionFigureText.width(part, 9, false) <= MatchesBlockKind.PRINTED_WIDTH, part);
        }
        assertEquals(MatchesBlockKind.PRINTED_WIDTH, wrapped.width());

        PhraseEntry hepatic = PhraseEntry.of(TABLE.rows()).getFirst();
        DecisionFigure printed = MatchesBlockKind.printed(hepatic, id -> id.equals(SENTENCE) ? Optional.of(TEXT) : Optional.empty(), 9);
        assertEquals(List.of("1", "1.1", "1.2"), texts(printed, "matches-number"));
        assertEquals(List.of("hepatic impairment"), texts(printed, "matches-phrase"));
        assertEquals(List.of(" → Hepatic impairment or Liver disorder"), texts(printed, "matches-concepts"));
        assertEquals(List.of("  · 2 matches · 1 judged"), texts(printed, "matches-count"));
        assertEquals(List.of("hepatic impairment", "hepatic impairment"), texts(printed, "matches-words"));
        List<String> contexts = texts(printed, "matches-context");
        assertEquals("Severe ", contexts.getFirst(), "each match in the words of its sentence");
        for (String context : contexts) {
            assertTrue(TEXT.contains(context.replace(InContext.ELLIPSIS, "").strip()), "as many as fit: " + context);
        }
        assertEquals(List.of("↓ refine → Severe hepatic impairment"), texts(printed, "matches-judgment"));
        assertEquals(List.of(MatchesBlockKind.NOT_JUDGED), texts(printed, "matches-unjudged"), "1.1 is not judged");
        assertEquals(List.of("description", "description"), texts(printed, "matches-source"), "each match names its stage");
        List<Double> sourceColumn = printed.marks().stream().filter(mark -> mark.role().equals("matches-source"))
                .map(mark -> ((DecisionFigure.Text) mark).x()).distinct().toList();
        assertEquals(1, sourceColumn.size(), "the stages stand in one column");
        double judgmentAt = printed.marks().stream().filter(mark -> mark.role().equals("matches-judgment"))
                .mapToDouble(mark -> ((DecisionFigure.Text) mark).x()).findFirst().orElseThrow();
        double unjudgedAt = printed.marks().stream().filter(mark -> mark.role().equals("matches-unjudged"))
                .mapToDouble(mark -> ((DecisionFigure.Text) mark).x()).findFirst().orElseThrow();
        assertEquals(judgmentAt, unjudgedAt, "judged or not, in one column");
        assertTrue(sourceColumn.getFirst() < judgmentAt);
        assertEquals(MatchesBlockKind.PRINTED_WIDTH, printed.width());
        assertEquals(3, rows(printed), "the phrase, then its two matches, a line each");

        DecisionFigure unheld = MatchesBlockKind.printed(hepatic, id -> Optional.empty(), 9);
        assertEquals(List.of(MatchesBlockKind.NO_SENTENCE, MatchesBlockKind.NO_SENTENCE), texts(unheld, "matches-context"));

        assertEquals(List.of(), kind.figures(StructuredBlockKind.Source.of("[matches]\n----\nnot a row\n----"),
                new StructuredBlockKind.Context(null)), "an unreadable block prints as its source");
    }

    @Test
    void aLongSentenceIsClippedToTheFigure() {
        String words = "hepatic impairment";
        String longer = "In " + "a very long clause ".repeat(20) + words + " and " + "another very long clause ".repeat(20) + "ends.";
        int from = longer.indexOf(words);
        MatchRow row = MatchRow.proposed(SENTENCE, from, from + words.length(), words, List.of(HEPATIC), Stage.DESCRIPTION,
                OptionalDouble.empty());
        DecisionFigure printed = MatchesBlockKind.printed(PhraseEntry.of(List.of(row)).getFirst(), id -> Optional.of(longer), 9);
        double right = printed.marks().stream().filter(mark -> mark instanceof DecisionFigure.Text)
                .map(mark -> (DecisionFigure.Text) mark).filter(text -> !text.role().equals("matches-count"))
                .mapToDouble(text -> text.x() + network.ike.komet.document.DecisionFigureText.width(text.text(), 9, text.bold()))
                .max().orElseThrow();
        assertTrue(right <= MatchesBlockKind.PRINTED_WIDTH, "the context fits the figure: " + right);
        assertTrue(texts(printed, "matches-context").getFirst().startsWith("…"));
    }

    @Test
    void theReadingViewListsEachPhraseAndItsMatchesNumbered() throws Exception {
        Node view = onFx(() -> kind.view(source(TABLE), new StructuredBlockKind.Context(null)));
        StackPane host = assertInstanceOf(StackPane.class, view);
        assertTrue(host.getStyleClass().contains(KlExpandable.HOST_STYLE_CLASS), "the table grows");
        VBox box = assertInstanceOf(VBox.class, host.getChildren().getFirst());
        Label title = assertInstanceOf(Label.class, box.getChildren().getFirst());
        assertEquals(MatchesBlockKind.TITLE, title.getText());
        assertEquals(MatchesBlockKind.BUTTON_ROOM, title.getPadding().getRight(), "the title leaves room for the Expand button");
        assertEquals(MatchesBlockKind.contents(TABLE), assertInstanceOf(Label.class, box.getChildren().get(1)).getText());
        assertEquals(MatchesBlockKind.provenance(RUN), assertInstanceOf(Label.class, box.getChildren().get(2)).getText());
        Node button = host.lookup(".kl-expand-icon");
        assertTrue(button != null && button.getStyleClass().contains(KlExpandable.SHOWN_STYLE_CLASS), "its Expand button is drawn always");
        assertEquals(javafx.geometry.Pos.TOP_RIGHT, StackPane.getAlignment(button));
        ScrollPane scroll = assertInstanceOf(ScrollPane.class, box.getChildren().get(3));
        assertEquals(MatchesBlockKind.LIST_HEIGHT, scroll.getMaxHeight(), "in the document the phrases scroll beneath the caption");
        VBox list = assertInstanceOf(VBox.class, scroll.getContent());
        assertEquals(2, list.getChildren().size(), "a phrase each");
        VBox first = assertInstanceOf(VBox.class, list.getChildren().getFirst());
        assertEquals(3, first.getChildren().size(), "the phrase, then its two matches");
        FlowPane head = assertInstanceOf(FlowPane.class, first.getChildren().getFirst());
        assertEquals("1", assertInstanceOf(Label.class, head.getChildren().getFirst()).getText());
        HBox firstMatch = assertInstanceOf(HBox.class, first.getChildren().get(1));
        assertEquals("description", assertInstanceOf(Label.class, firstMatch.getChildren().get(2)).getText(), "its stage");
        assertEquals(MatchesBlockKind.NOT_JUDGED, assertInstanceOf(Label.class, firstMatch.getChildren().get(3)).getText());
        HBox second = assertInstanceOf(HBox.class, first.getChildren().get(2));
        assertEquals("1.2", assertInstanceOf(Label.class, second.getChildren().getFirst()).getText());
        HBox judged = assertInstanceOf(HBox.class, second.getChildren().get(3));
        assertEquals("↓ refine", assertInstanceOf(Label.class, judged.getChildren().getFirst()).getText());
        TextFlow words = assertInstanceOf(TextFlow.class, second.getChildren().get(1));
        assertEquals("hepatic impairment " + MatchesBlockKind.NO_SENTENCE,
                words.getChildren().stream().map(node -> ((Text) node).getText()).reduce("", String::concat),
                "no store open: the matched words alone");

        Node unreadable = onFx(() -> kind.view(StructuredBlockKind.Source.of("[matches]\n----\nnot a row\n----"),
                new StructuredBlockKind.Context(null)));
        Label why = assertInstanceOf(Label.class, assertInstanceOf(VBox.class, unreadable).getChildren().getFirst());
        assertTrue(why.getText().startsWith("Not a match table: "), why.getText());
    }

    @Test
    void theTableGrowsIntoItsCardWhereItsPhrasesFillThePlace() throws Exception {
        VBox grown = onFx(() -> MatchesBlockKind.drawn(TABLE, id -> Optional.empty(), new StructuredBlockKind.Context(null), true));
        ScrollPane scroll = assertInstanceOf(ScrollPane.class, grown.getChildren().get(3));
        assertEquals(Double.MAX_VALUE, scroll.getMaxHeight(), "grown, the phrases are not held to the document's height");
        assertEquals(Priority.ALWAYS, VBox.getVgrow(scroll));
        assertEquals(0, assertInstanceOf(Label.class, grown.getChildren().getFirst()).getPadding().getRight(),
                "no Expand button stands over a grown table's title");

        int[] drawings = onFx(() -> {
            Node view = kind.view(source(TABLE), new StructuredBlockKind.Context(null));
            VBox document = new VBox(view);
            StackPane card = new StackPane(document);
            KlExpansionHost.mark(card, () -> "Card", false);
            new Scene(card, 800, 600);
            card.applyCss();
            card.layout();
            int before = card.lookupAll(".stamped-document-matches").size();
            assertInstanceOf(Button.class, view.lookup(".kl-expand-icon")).fire();
            Region figure = assertInstanceOf(Region.class, ((StackPane) view).getChildren().getFirst());
            int expanded = figure.getProperties().get(KlExpandable.PropertyKeys.EXPANSION) != null ? 1 : 0;
            return new int[] {before, expanded, card.lookupAll(".stamped-document-matches").size()};
        });
        assertEquals(1, drawings[0]);
        assertEquals(1, drawings[1], "the Expand button grows the table into its card");
        assertEquals(2, drawings[2], "a fresh drawing fills the card; the document's drawing stays where it was");
    }

    @Test
    void aLongTableIsDrawnALineAtATimeAsItScrolls() throws Exception {
        List<MatchRow> rows = new ArrayList<>();
        for (int p = 1; p <= 60; p++) {
            KonceptRef concept = new KonceptRef(new UUID(9, p), "Concept " + p);
            MatchRow row = MatchRow.proposed(SENTENCE, 0, 4, "word " + p, List.of(concept), Stage.DESCRIPTION, OptionalDouble.empty());
            for (int m = 0; m < 10; m++) {
                rows.add(row);
            }
        }
        MatchTable longTable = new MatchTable(RUN, rows);
        int lineCount = 60 + 600;
        assertTrue(lineCount > MatchesBlockKind.WHOLE_LINES);

        Object[] seen = onFx(() -> {
            Node view = kind.view(source(longTable), new StructuredBlockKind.Context(null));
            VBox box = (VBox) ((StackPane) view).getChildren().getFirst();
            javafx.scene.control.skin.VirtualFlow<?> flow = assertInstanceOf(javafx.scene.control.skin.VirtualFlow.class, box.getChildren().get(3));
            VBox document = new VBox(view);
            new Scene(document, 800, 1000);
            document.applyCss();
            document.layout();
            document.layout();
            int drawn = flow.lookupAll(".stamped-document-matches-line").size();
            MatchesBlockKind.Line firstLine = (MatchesBlockKind.Line) flow.getFirstVisibleCell();
            Node first = firstLine.getGraphic();
            // the drawing is on screen only when the line's skin has made it a child: a graphic alone draws nothing
            boolean onScreen = firstLine.getSkin() != null && firstLine.getChildrenUnmodifiable().contains(first)
                    && first.getScene() != null && firstLine.getHeight() > firstLine.snappedTopInset() + firstLine.snappedBottomInset();
            // laid out once, a line rests: a skin that sizes the drawing one way and the line another lays it out forever
            Region drawing = (Region) first;
            double drawnWidth = drawing.getWidth();
            document.layout();
            boolean rests = !firstLine.isNeedsLayout() && !drawing.isNeedsLayout() && drawing.getWidth() == drawnWidth
                    && drawnWidth == firstLine.getWidth() - firstLine.snappedLeftInset() - firstLine.snappedRightInset();
            String firstNumber = ((Label) ((FlowPane) first).getChildren().getFirst()).getText();
            double height = flow.getHeight();
            flow.scrollTo(lineCount - 1);
            document.layout();
            Node last = ((MatchesBlockKind.Line) flow.getLastVisibleCell()).getGraphic();
            String lastNumber = ((Label) ((HBox) last).getChildren().getFirst()).getText();
            return new Object[] {flow.getCellCount(), drawn, firstNumber, height, lastNumber, onScreen, rests};
        });
        assertEquals(lineCount, seen[0], "a line for each phrase and each match");
        assertTrue((Boolean) seen[5], "a line shows its drawing, as tall as the drawing is");
        assertTrue((Boolean) seen[6], "a line laid out once stays laid out, its drawing as wide as the line inside its padding");
        int drawn = (Integer) seen[1];
        assertTrue(drawn > 0 && drawn < 100, "only the lines in view are drawn: " + drawn);
        assertEquals("1", seen[2], "the first line is the first phrase's head");
        assertEquals(MatchesBlockKind.LIST_HEIGHT, (Double) seen[3], 1, "in the document the table stands at the list's height");
        assertEquals("60.10", seen[4], "scrolled to its end, the last match of the last phrase");

        VBox grown = onFx(() -> MatchesBlockKind.drawn(longTable, id -> Optional.empty(), new StructuredBlockKind.Context(null), true));
        assertEquals(Priority.ALWAYS, VBox.getVgrow(grown.getChildren().get(3)), "grown, the lines fill the place");
    }

    /**
     * A grown table as wide as a journal window: each line's drawing takes the line's whole width
     * and keeps it, so a line laid out once is not laid out again with nothing changed.
     */
    @Test
    void aGrownTableAsWideAsTheWindowRestsOnceLaidOut() throws Exception {
        List<MatchRow> rows = new ArrayList<>();
        for (int p = 1; p <= 60; p++) {
            MatchRow row = MatchRow.proposed(SENTENCE, 0, 4, "word " + p, List.of(new KonceptRef(new UUID(9, p), "Concept " + p)),
                    Stage.DESCRIPTION, OptionalDouble.empty());
            for (int m = 0; m < 10; m++) {
                rows.add(row);
            }
        }
        MatchTable longTable = new MatchTable(RUN, rows);
        Object[] seen = onFx(() -> {
            // grown by its Expand button into a window-wide card, through the host Komet grows it with
            Node view = kind.view(source(longTable), new StructuredBlockKind.Context(null));
            StackPane window = new StackPane(new VBox(view));
            KlExpansionHost.mark(window, () -> "Journal window", false);
            new Scene(window, 2000, 1200);
            window.applyCss();
            window.layout();
            assertInstanceOf(Button.class, view.lookup(".kl-expand-icon")).fire();
            window.applyCss();
            window.layout();
            window.layout();
            VBox grown = (VBox) window.lookupAll(".stamped-document-matches").stream()
                    .filter(box -> box.getScene() != null && ((Region) box).getWidth() > 1500).findFirst().orElseThrow();
            javafx.scene.control.skin.VirtualFlow<?> flow = (javafx.scene.control.skin.VirtualFlow<?>) grown.getChildren().get(3);
            MatchesBlockKind.Line line = (MatchesBlockKind.Line) flow.getFirstVisibleCell();
            Region drawing = (Region) line.getGraphic();
            double inner = line.getWidth() - line.snappedLeftInset() - line.snappedRightInset();
            double before = drawing.getWidth();
            // pulsed as Komet pulses a window, the table comes to rest: nothing is laid out over and over
            List<String> unsettled = dev.ikm.komet.framework.graphics.LayoutWatchdog.unsettled(window, 2000, 1200);
            return new Object[] {inner, before, drawing.getWidth(), unsettled};
        });
        assertTrue((Double) seen[0] > 1500, "the line is as wide as the window: " + seen[0]);
        assertEquals((Double) seen[0], (Double) seen[1], 0.5, "the drawing takes the line's whole width");
        assertEquals((Double) seen[1], (Double) seen[2], 0.5, "and keeps it from one layout to the next");
        assertEquals(List.of(), seen[3], "nothing is still being laid out");
    }

    /** The text of a figure's marks of a role, in drawing order. */
    private static List<String> texts(DecisionFigure figure, String role) {
        return figure.marks().stream().filter(mark -> mark instanceof DecisionFigure.Text && mark.role().equals(role))
                .map(mark -> ((DecisionFigure.Text) mark).text()).toList();
    }

    /** How many lines a figure's text stands on. */
    private static long rows(DecisionFigure figure) {
        return figure.marks().stream().filter(mark -> mark instanceof DecisionFigure.Text)
                .mapToDouble(mark -> ((DecisionFigure.Text) mark).top()).distinct().count();
    }

    private static StructuredBlockKind.Source source(MatchTable table) {
        return StructuredBlockKind.Source.of(String.join("\n", table.lines()));
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }
}
