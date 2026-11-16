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
import dev.ikm.komet.layout.expand.ExpansionCorner;
import dev.ikm.komet.layout.expand.KlExpandable;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import network.ike.komet.document.DecisionFigure;
import network.ike.komet.document.DecisionFigureText;
import network.ike.komet.document.DocumentNarratives;
import network.ike.komet.document.KonceptChips;
import network.ike.komet.document.SegmentNarrative;
import network.ike.komet.document.StructuredBlockKind;
import network.ike.komet.text.concordance.InContext;
import network.ike.komet.text.matches.Judgment;
import network.ike.komet.text.matches.MatchRow;
import network.ike.komet.text.matches.MatchTable;
import network.ike.komet.text.matches.PhraseEntry;
import network.ike.komet.text.matches.RunIdentity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The structured block kind of a match table, {@code [matches]}. A table stores a row for each
 * match, which no reader can judge one at a time, so both the Reading view and the page read it by
 * phrase ({@link PhraseEntry}): each phrase numbered, with the concepts it was matched to, and
 * beneath it each of its matches numbered as {@code 5.2} and shown in the words of its sentence,
 * then the stage that proposed it with its score when the stage gives one, then its judgment or
 * that it is not judged (ike-lab-documents topic {@code dev-conceptification-match-table}). A caption heads the table:
 * its title, what it holds, counted from its rows so that it is never out of date, and a line that
 * says what the run was made by. The sentences are read from the store, where the rows' anchors
 * name them; a sentence the open store does not hold shows the matched words alone.
 *
 * <p>A table of more than {@link #WHOLE_LINES} lines, a phrase's head or one of its matches each,
 * is drawn a line at a time as the lines scroll into view, since a 510(k) holds thousands of
 * matches and drawing them all at once would stall the window.
 *
 * <p>In the Reading view the table grows, as a figure in a document does: its Expand button,
 * drawn always at its top right, grows it to its card, then to the journal window, then to the
 * whole screen. The block is edited as its text. A block that cannot be read as a match table is
 * drawn as its source under the reason, so nothing it holds is hidden.
 */
public final class MatchesBlockKind implements StructuredBlockKind {

    /** The title of a match table's caption. */
    static final String TITLE = "Concept matches";

    /** How many of the concepts matched most often a caption names. */
    static final int MOST_MATCHED = 3;

    /** The room the Expand button takes at the end of the caption's title, in points: its 24 and a gap. */
    static final double BUTTON_ROOM = 30;

    /** The least height of the caption's title, so that what follows starts beneath the Expand button. */
    static final double BUTTON_HEIGHT = 26;

    /** How many words the Reading view shows on each side of a match. */
    static final int CONTEXT_WORDS = 8;

    /** The tallest the Reading view's list grows before it scrolls, in points. */
    static final double LIST_HEIGHT = 480;

    /**
     * The most lines, a phrase's head or one of its matches each, that a table is drawn with whole;
     * a longer table is drawn a line at a time as it scrolls into view.
     */
    static final int WHOLE_LINES = 400;

    /** The width of a phrase's figure on a page, in points: inside the measure of a letter or A4 page at normal margins. */
    static final double PRINTED_WIDTH = 432;

    /** What a match whose sentence the open store does not hold says in place of its sentence. */
    static final String NO_SENTENCE = "(its sentence is not in the open store)";

    /** What a match no rater has judged shows where a judgment stands. */
    static final String NOT_JUDGED = "not judged";

    private static final String GREY = "#5f6b7a";
    private static final String INK = "#1b1f24";
    private static final DateTimeFormatter RUN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    /** Creates the kind; found through {@link java.util.ServiceLoader}. */
    public MatchesBlockKind() {
    }

    @Override
    public String style() {
        return MatchTable.STYLE;
    }

    @Override
    public String name() {
        return "match table";
    }

    @Override
    public Node view(Source source, Context context) {
        MatchTable table;
        try {
            table = read(source);
        } catch (IllegalArgumentException unreadable) {
            return unreadable(source, unreadable.getMessage());
        }
        Function<String, Optional<String>> sentences = storedSentences(table);
        return new Grow(drawn(table, sentences, context, false), () -> drawn(table, sentences, context, true))
                .withExpandAffordance();
    }

    /**
     * The table as the Reading view draws it: its caption, then its phrases. In the document the
     * phrases scroll beneath the caption once they are taller than {@link #LIST_HEIGHT}; grown, they
     * fill the place the table grew into.
     *
     * @param table     the table
     * @param sentences each sentence's stored text by its identifier
     * @param context   what the control may read
     * @param grown     whether the drawing is the grown table's
     * @return the drawing
     */
    static VBox drawn(MatchTable table, Function<String, Optional<String>> sentences, Context context, boolean grown) {
        double size = context.base() * 0.9;
        Label title = new Label(TITLE);
        title.setFont(Font.font(null, FontWeight.BOLD, context.base() * 0.93));
        title.getStyleClass().add("stamped-document-matches-title");
        if (!grown) {
            // the Expand button is drawn over the end of the title's row
            title.setPadding(new Insets(0, BUTTON_ROOM, 0, 0));
            title.setMinHeight(BUTTON_HEIGHT);
        }
        Label contents = new Label(contents(table));
        contents.setWrapText(true);
        contents.setMaxWidth(Double.MAX_VALUE);
        contents.setFont(Font.font(context.base() * 0.93));
        contents.getStyleClass().add("stamped-document-matches-contents");
        Label made = new Label(provenance(table.run()));
        made.setTooltip(new Tooltip(identity(table.run())));
        made.setFont(Font.font(context.base() * 0.8));
        made.setStyle("-fx-text-fill: " + GREY + ";");

        List<PhraseEntry> entries = PhraseEntry.of(table.rows());
        int lineCount = entries.stream().mapToInt(entry -> 1 + entry.rows().size()).sum();
        Region phrases;
        if (lineCount > WHOLE_LINES) {
            // a long table is drawn a line at a time, as it scrolls; in the document it stands at the list's height
            VirtualFlow<Line> flow = lines(entries, sentences, context, size);
            flow.setPrefHeight(LIST_HEIGHT);
            flow.setMinHeight(grown ? 0 : LIST_HEIGHT);
            phrases = flow;
        } else {
            VBox list = new VBox(size * 0.8);
            list.setPadding(new Insets(4, 8, 4, 2));
            for (PhraseEntry entry : entries) {
                list.getChildren().add(entry(entry, sentences, context, size));
            }
            ScrollPane scroll = new ScrollPane(list);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            phrases = scroll;
        }

        VBox box = new VBox(4, title, contents, made, phrases);
        box.getStyleClass().add("stamped-document-matches");
        if (grown) {
            VBox.setVgrow(phrases, Priority.ALWAYS);
            phrases.setMaxHeight(Double.MAX_VALUE);
            // the contract button is at the foot of the grown table, clear of its last phrase
            box.setPadding(new Insets(16, 16, 48, 16));
        } else {
            phrases.setMaxHeight(LIST_HEIGHT);
        }
        return box;
    }

    @Override
    public Optional<Editor> editor(Source source, Context context) {
        return Optional.empty();
    }

    /** On a page, the table's caption, then each phrase as a figure of its own, so pages break between phrases. */
    @Override
    public List<DecisionFigure> figures(Source source, Context context) {
        MatchTable table;
        try {
            table = read(source);
        } catch (IllegalArgumentException unreadable) {
            return List.of();                                        // the page prints the source
        }
        Function<String, Optional<String>> sentences = storedSentences(table);
        double size = Math.max(7, context.base() * 0.85);
        return DecisionFigureText.inSerif(() -> {
            List<DecisionFigure> figures = new ArrayList<>();
            figures.add(printedCaption(table, size));
            for (PhraseEntry entry : PhraseEntry.of(table.rows())) {
                figures.add(printed(entry, sentences, size));
            }
            return List.copyOf(figures);
        });
    }

    /**
     * Reads a block's source as a match table.
     *
     * @param source the block's source
     * @return the table
     * @throws IllegalArgumentException if the source is not a match table
     */
    static MatchTable read(Source source) {
        List<String> lines = new ArrayList<>();
        lines.add(source.parameters());
        lines.add(MatchTable.FENCE);
        String content = source.content();
        if (!content.isEmpty()) {
            lines.addAll(List.of(content.split("\n", -1)));
        }
        lines.add(MatchTable.FENCE);
        return MatchTable.parse(lines);
    }

    /**
     * What the table holds, as its caption says it: how many phrases were matched how often, how
     * many of the matches are judged and how, and the concepts matched most often.
     *
     * @param table the table
     * @return {@code 36 phrases matched 57 times; 2 judged (1 refined, 1 rejected), 55 to judge.
     *         Matched most often: Concept kind (7), Conditional (5), Set difference (3).}
     */
    static String contents(MatchTable table) {
        int count = table.rows().size();
        int phrases = PhraseEntry.of(table.rows()).size();
        StringBuilder text = new StringBuilder().append(phrases).append(phrases == 1 ? " phrase" : " phrases")
                .append(" matched ").append(count == 1 ? "once" : count + " times");
        Map<Judgment, Integer> judged = new EnumMap<>(Judgment.class);
        table.rows().forEach(row -> row.judgment().ifPresent(judgment -> judged.merge(judgment, 1, Integer::sum)));
        int judgedCount = judged.values().stream().mapToInt(Integer::intValue).sum();
        if (judgedCount == 0) {
            text.append(", none judged yet.");
        } else {
            int open = count - judgedCount;
            text.append("; ").append(judgedCount).append(" judged (")
                    .append(judged.entrySet().stream().map(each -> each.getValue() + " " + judgedAs(each.getKey()))
                            .collect(Collectors.joining(", ")))
                    .append(')').append(open == 0 ? ", all of them." : ", " + open + " to judge.");
        }
        List<Map.Entry<KonceptRef, Integer>> most = mostMatched(table.rows());
        if (!most.isEmpty()) {
            text.append(" Matched most often: ")
                    .append(most.stream().map(each -> each.getKey().label() + " (" + each.getValue() + ")").collect(Collectors.joining(", ")))
                    .append('.');
        }
        return text.toString();
    }

    /**
     * The concepts matched more than once, most often first and those matched as often in the
     * order the table first has them; at most {@link #MOST_MATCHED}. A match counts for the
     * concept proposed, its first candidate, or for the concept a rater named for a missed match.
     *
     * @param rows the table's rows
     * @return each concept with how often it was matched
     */
    static List<Map.Entry<KonceptRef, Integer>> mostMatched(List<MatchRow> rows) {
        Map<UUID, KonceptRef> concepts = new LinkedHashMap<>();
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (MatchRow row : rows) {
            Optional<KonceptRef> proposed = row.candidates().isEmpty() ? row.named() : Optional.of(row.candidates().getFirst());
            proposed.ifPresent(concept -> {
                concepts.putIfAbsent(concept.uuid(), concept);
                counts.merge(concept.uuid(), 1, Integer::sum);
            });
        }
        // a sorted stream of an ordered source is stable: concepts matched as often keep the table's order
        return counts.entrySet().stream()
                .filter(each -> each.getValue() > 1)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(MOST_MATCHED)
                .map(each -> Map.entry(concepts.get(each.getKey()), each.getValue()))
                .toList();
    }

    /** A judgment as a caption counts it: {@code refined}. */
    private static String judgedAs(Judgment judgment) {
        return switch (judgment) {
            case AFFIRM -> "affirmed";
            case REFINE -> "refined";
            case GENERALIZE -> "generalized";
            case REJECT -> "rejected";
            case MISSED -> "missed";
        };
    }

    /**
     * What made the table, in one line beneath its caption: when the run was, the matcher and its
     * stages. The whole of the run's identity is the line's tooltip.
     *
     * @param run the run
     * @return {@code Run 2026-11-14 22:10 UTC · matcher development · description 1}
     */
    static String provenance(RunIdentity run) {
        return "Run " + RUN.format(run.run()) + " · matcher " + run.matcher() + " · " + run.stages();
    }

    /**
     * The table's caption as a page prints it: the title, what the table holds wrapped to the
     * figure's width, and what made it.
     *
     * @param table the table
     * @param size  the size of the text, in points
     * @return the figure
     */
    static DecisionFigure printedCaption(MatchTable table, double size) {
        double line = DecisionFigureText.lineHeight(size);
        List<DecisionFigure.Mark> marks = new ArrayList<>();
        marks.add(text(0, 0, TITLE, size, true, false, INK, "matches-title"));
        double widest = DecisionFigureText.width(TITLE, size, true);
        double top = line;
        for (String part : DecisionFigureText.wrap(contents(table), size, PRINTED_WIDTH)) {
            marks.add(text(0, top, part, size, false, false, INK, "matches-contents"));
            widest = Math.max(widest, DecisionFigureText.width(part, size, false));
            top += line;
        }
        double small = size * 0.9;
        String made = provenance(table.run());
        marks.add(text(0, top, made, small, false, true, GREY, "matches-provenance"));
        widest = Math.max(widest, DecisionFigureText.width(made, small, false));
        top += DecisionFigureText.lineHeight(small);
        return new DecisionFigure(Math.max(PRINTED_WIDTH, widest), top, "matches-caption", marks);
    }

    /**
     * How many matches a phrase has, and how many of them are judged when any are.
     *
     * @param entry the phrase's entry
     * @return {@code 5 matches · 2 judged}, or {@code 1 match}
     */
    static String count(PhraseEntry entry) {
        int count = entry.rows().size();
        int judged = entry.judged();
        return count + (count == 1 ? " match" : " matches") + (judged == 0 ? "" : " · " + judged + " judged");
    }

    /**
     * Everything the run's attribute line names, one item a line.
     *
     * @param run the run
     * @return the lines
     */
    static String identity(RunIdentity run) {
        return String.join("\n",
                "Run: " + RUN.format(run.run()),
                "Matcher: " + run.matcher(),
                "Stages: " + run.stages(),
                "Splitter: " + run.splitter(),
                "Data: " + run.data(),
                "Position: " + run.position(),
                "Descriptions: " + run.descriptions());
    }

    /**
     * A judgment as a match shows it.
     *
     * @param judgment the judgment, empty for a match no one has judged
     * @return the judgment with its mark, or empty
     */
    static String mark(Optional<Judgment> judgment) {
        return judgment.map(j -> switch (j) {
            case AFFIRM -> "✓ affirm";
            case REFINE -> "↓ refine";
            case GENERALIZE -> "↑ generalize";
            case REJECT -> "✗ reject";
            case MISSED -> "+ missed";
        }).orElse("");
    }

    /**
     * Where a match came from, as its line shows it: the stage that proposed it, and its score when
     * the stage gives one. The description stage matches a description word for word and gives
     * none. A missed match was proposed by no stage, and its judgment says so.
     *
     * @param row the match
     * @return {@code description}, {@code variant 0.82}, or empty for a missed match
     */
    static String source(MatchRow row) {
        return row.stage().map(stage -> stage.word() + (row.score().isPresent()
                ? " " + String.format(Locale.ROOT, "%.2f", row.score().getAsDouble()) : "")).orElse("");
    }

    /**
     * A judgment as a page prints it: its mark, then the concept the rater named.
     *
     * @param row the match
     * @return {@code ↓ refine → Severe hepatic impairment}, or empty for a match no one has judged
     */
    static String printedJudgment(MatchRow row) {
        String mark = mark(row.judgment());
        return mark.isEmpty() ? "" : mark + row.named().map(named -> " → " + named.label()).orElse("");
    }

    /**
     * A match in the words of its sentence.
     *
     * @param row       the match
     * @param sentences each sentence's stored text by its identifier
     * @return the words in their sentence; empty when the sentence is not held or no longer has the words
     */
    static Optional<InContext> inContext(MatchRow row, Function<String, Optional<String>> sentences) {
        return sentences.apply(row.sentence()).flatMap(stored -> InContext.of(stored, row.from(), row.to()));
    }

    /**
     * A phrase as a page prints it: its number, the phrase and its concepts, and how many matches
     * it has; then each match by its number, in the words of its sentence clipped to the figure's
     * width, with two columns at the right: the stage that proposed it with its score, and its
     * judgment, or that it is not judged.
     *
     * @param entry     the phrase's entry
     * @param sentences each sentence's stored text by its identifier
     * @param size      the size of the text, in points
     * @return the figure
     */
    static DecisionFigure printed(PhraseEntry entry, Function<String, Optional<String>> sentences, double size) {
        double line = DecisionFigureText.lineHeight(size);
        double indent = DecisionFigureText.width("00.00", size, false) + size * 0.6;
        double gap = size;
        List<DecisionFigure.Mark> marks = new ArrayList<>();

        double x = indent;
        marks.add(text(0, 0, String.valueOf(entry.number()), size, true, false, GREY, "matches-number"));
        marks.add(text(x, 0, entry.phrase(), size, true, false, INK, "matches-phrase"));
        x += DecisionFigureText.width(entry.phrase(), size, true);
        String concepts = " → " + entry.concepts().stream().map(KonceptRef::label).collect(Collectors.joining(" or "));
        marks.add(text(x, 0, concepts, size, false, false, INK, "matches-concepts"));
        x += DecisionFigureText.width(concepts, size, false);
        String count = "  · " + count(entry);
        marks.add(text(x, 0, count, size, false, true, GREY, "matches-count"));
        double widest = x + DecisionFigureText.width(count, size, false);

        double judgments = entry.rows().stream().mapToDouble(row -> DecisionFigureText.width(judgmentOrNot(row), size, false)).max().orElse(0);
        double sources = entry.rows().stream().mapToDouble(row -> DecisionFigureText.width(source(row), size, false)).max().orElse(0);
        double judgmentAt = PRINTED_WIDTH - judgments;
        double sourceAt = judgmentAt - (sources > 0 ? sources + gap : 0);
        double room = sourceAt - gap - indent;
        double top = line;
        for (int i = 0; i < entry.rows().size(); i++) {
            MatchRow row = entry.rows().get(i);
            marks.add(text(0, top, entry.numberOf(i), size, false, false, GREY, "matches-number"));
            Optional<InContext> context = inContext(row, sentences);
            x = indent;
            if (context.isPresent()) {
                InContext shown = fitted(context.get(), size, room);
                if (!shown.before().isEmpty()) {
                    marks.add(text(x, top, shown.before(), size, false, false, INK, "matches-context"));
                    x += DecisionFigureText.width(shown.before(), size, false);
                }
                marks.add(text(x, top, shown.words(), size, true, false, INK, "matches-words"));
                x += DecisionFigureText.width(shown.words(), size, true);
                if (!shown.after().isEmpty()) {
                    marks.add(text(x, top, shown.after(), size, false, false, INK, "matches-context"));
                    x += DecisionFigureText.width(shown.after(), size, false);
                }
            } else {
                marks.add(text(x, top, row.words(), size, true, false, INK, "matches-words"));
                x += DecisionFigureText.width(row.words() + " ", size, true);
                marks.add(text(x, top, NO_SENTENCE, size, false, true, GREY, "matches-context"));
                x += DecisionFigureText.width(NO_SENTENCE, size, false);
            }
            widest = Math.max(widest, x);
            String source = source(row);
            double at = Math.max(sourceAt, x + gap);
            if (!source.isEmpty()) {
                marks.add(text(at, top, source, size, false, false, GREY, "matches-source"));
                at = Math.max(judgmentAt, at + DecisionFigureText.width(source, size, false) + gap);
            } else {
                at = Math.max(judgmentAt, at);
            }
            String judgment = printedJudgment(row);
            if (judgment.isEmpty()) {
                marks.add(text(at, top, NOT_JUDGED, size, false, true, GREY, "matches-unjudged"));
                widest = Math.max(widest, at + DecisionFigureText.width(NOT_JUDGED, size, false));
            } else {
                marks.add(text(at, top, judgment, size, false, false, INK, "matches-judgment"));
                widest = Math.max(widest, at + DecisionFigureText.width(judgment, size, false));
            }
            top += line;
        }
        return new DecisionFigure(Math.max(PRINTED_WIDTH, widest), top, "matches-phrase", marks);
    }

    /** A match's judgment as a page prints it, or that no rater has judged it. */
    private static String judgmentOrNot(MatchRow row) {
        String judgment = printedJudgment(row);
        return judgment.isEmpty() ? NOT_JUDGED : judgment;
    }

    /** The context with as many words on each side as fit a width, down to none. */
    static InContext fitted(InContext context, double size, double room) {
        for (int each = CONTEXT_WORDS; each > 0; each--) {
            InContext clipped = context.clipped(each);
            if (width(clipped, size) <= room) {
                return clipped;
            }
        }
        return context.clipped(0);
    }

    private static double width(InContext context, double size) {
        return DecisionFigureText.width(context.before(), size, false) + DecisionFigureText.width(context.words(), size, true)
                + DecisionFigureText.width(context.after(), size, false);
    }

    private static DecisionFigure.Text text(double x, double top, String text, double size, boolean bold, boolean italic,
                                            String colour, String role) {
        return new DecisionFigure.Text(x, top, text, size, bold, italic, colour, role);
    }

    /** A phrase in the Reading view: its numbered head, then each of its matches in its sentence. */
    private static Node entry(PhraseEntry entry, Function<String, Optional<String>> sentences, Context context, double size) {
        VBox box = new VBox(2, head(entry, context, size));
        for (int i = 0; i < entry.rows().size(); i++) {
            box.getChildren().add(match(entry, i, sentences, context, size));
        }
        return box;
    }

    /** A phrase's head: its number, the phrase, the concepts it was matched to, and how many matches it has. */
    private static FlowPane head(PhraseEntry entry, Context context, double size) {
        FlowPane head = new FlowPane(size * 0.4, 2);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().add(number(String.valueOf(entry.number()), size, true));
        Label phrase = new Label(entry.phrase());
        phrase.setFont(Font.font(null, FontWeight.BOLD, size));
        head.getChildren().addAll(phrase, new Label("→"));
        for (int i = 0; i < entry.concepts().size(); i++) {
            if (i > 0) {
                head.getChildren().add(grey("or", size));
            }
            head.getChildren().add(chip(entry.concepts().get(i), context));
        }
        head.getChildren().add(grey("· " + count(entry), size));
        return head;
    }

    /** One match of a phrase on its line: its number, its words in its sentence, its stage, and its judgment. */
    private static HBox match(PhraseEntry entry, int index, Function<String, Optional<String>> sentences, Context context, double size) {
        MatchRow row = entry.rows().get(index);
        TextFlow words = sentence(row, sentences, size);
        HBox.setHgrow(words, Priority.ALWAYS);
        Label source = grey(source(row), size);
        source.setMinWidth(Region.USE_PREF_SIZE);
        source.getStyleClass().add("stamped-document-matches-source");
        HBox match = new HBox(size * 0.6, number(entry.numberOf(index), size, false), words, source, judgment(row, context, size));
        match.setAlignment(Pos.BASELINE_LEFT);
        return match;
    }

    /**
     * The lines of a table too long to draw whole: each phrase's head, then each of its matches, a
     * line each, drawn only as they scroll into view. A long table holds thousands of matches, and
     * drawing every one at once would stall the window it is in.
     *
     * @param entries   the table's phrases
     * @param sentences each sentence's stored text by its identifier
     * @param context   what the control may read
     * @param size      the size of the text, in points
     * @return the flow of lines
     */
    static VirtualFlow<Line> lines(List<PhraseEntry> entries, Function<String, Optional<String>> sentences, Context context, double size) {
        List<Spot> spots = new ArrayList<>();
        for (PhraseEntry entry : entries) {
            spots.add(new Spot(entry, Spot.HEAD));
            for (int i = 0; i < entry.rows().size(); i++) {
                spots.add(new Spot(entry, i));
            }
        }
        Function<Spot, Node> draw = spot -> {
            if (spot.match() != Spot.HEAD) {
                return match(spot.entry(), spot.match(), sentences, context, size);
            }
            FlowPane head = head(spot.entry(), context, size);
            if (spot.entry().number() > 1) {
                head.setPadding(new Insets(size * 0.8, 0, 0, 0));       // the space between phrases
            }
            return head;
        };
        VirtualFlow<Line> flow = new VirtualFlow<>();
        flow.setCellFactory(unused -> new Line(spots, draw));
        flow.setCellCount(spots.size());
        flow.getStyleClass().add("stamped-document-matches-lines");
        return flow;
    }

    /**
     * A line of a long table: a phrase's head, or one of its matches.
     *
     * @param entry the phrase
     * @param match the match's index among the phrase's rows, or {@link #HEAD} for the head
     */
    record Spot(PhraseEntry entry, int match) {
        /** The index that names a phrase's head rather than a match. */
        static final int HEAD = -1;
    }

    /**
     * A cell of a long table's flow. The flow gives each line its whole width, so a sentence wraps
     * within it, and the line is as tall as its drawing at that width.
     */
    static final class Line extends IndexedCell<Spot> {

        private final List<Spot> spots;
        private final Function<Spot, Node> draw;

        Line(List<Spot> spots, Function<Spot, Node> draw) {
            this.spots = spots;
            this.draw = draw;
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setPadding(new Insets(1, 8, 1, 2));
            getStyleClass().add("stamped-document-matches-line");
        }

        /**
         * The skin that makes the drawing a child of the line. {@link IndexedCell} has none of its
         * own (only its concrete kinds, such as {@code ListCell}, do), and no style sheet names one
         * for this line, so without it the line is an empty box and its drawing is never shown.
         */
        @Override
        protected Skin<?> createDefaultSkin() {
            return new CellSkinBase<>(this);
        }

        @Override
        public void updateIndex(int index) {
            super.updateIndex(index);
            Spot spot = index >= 0 && index < spots.size() ? spots.get(index) : null;
            // the spots are made once, so the same line is the same instance; comparing by value would compare a
            // phrase's every match on each step of a scroll
            if (spot != getItem() || (spot != null && getGraphic() == null)) {
                updateItem(spot, spot == null);
                setGraphic(spot == null ? null : draw.apply(spot));
            }
        }

        /** No width of its own: the flow gives the line the width of its view. */
        @Override
        protected double computePrefWidth(double height) {
            return snappedLeftInset() + snappedRightInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            return 0;
        }

        @Override
        protected double computeMaxHeight(double width) {
            return Double.MAX_VALUE;
        }

        /** As tall as the drawing is at the line's width, so a wrapped sentence is shown whole. */
        @Override
        protected double computePrefHeight(double width) {
            Node drawing = getGraphic();
            double insets = snappedTopInset() + snappedBottomInset();
            if (drawing == null) {
                return insets;
            }
            double inner = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
            return insets + drawing.prefHeight(inner);
        }

        /** The drawing fills the line, whatever the skin made of it. */
        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            Node drawing = getGraphic();
            if (drawing != null) {
                drawing.resizeRelocate(snappedLeftInset(), snappedTopInset(),
                        Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset()),
                        Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset()));
            }
        }
    }

    /** A number naming a phrase or a match, in a column wide enough for {@code 12.10}. */
    private static Label number(String number, double size, boolean phrase) {
        Label label = new Label(number);
        label.setFont(Font.font(null, phrase ? FontWeight.BOLD : FontWeight.NORMAL, size));
        label.setStyle("-fx-text-fill: " + GREY + ";");
        label.setMinWidth(size * 3.2);
        return label;
    }

    private static Label grey(String text, double size) {
        Label label = new Label(text);
        label.setFont(Font.font(size * 0.9));
        label.setStyle("-fx-text-fill: " + GREY + ";");
        return label;
    }

    /** A match in the words of its sentence, the matched words bold. */
    private static TextFlow sentence(MatchRow row, Function<String, Optional<String>> sentences, double size) {
        Font plain = Font.font(size);
        Font bold = Font.font(null, FontWeight.BOLD, size);
        TextFlow flow = new TextFlow();
        flow.setMinWidth(0);
        Optional<InContext> context = inContext(row, sentences).map(found -> found.clipped(CONTEXT_WORDS));
        if (context.isPresent()) {
            flow.getChildren().addAll(styled(context.get().before(), plain, Color.web(INK)),
                    styled(context.get().words(), bold, Color.web(INK)), styled(context.get().after(), plain, Color.web(INK)));
        } else {
            flow.getChildren().addAll(styled(row.words() + " ", bold, Color.web(INK)),
                    styled(NO_SENTENCE, Font.font(null, FontPosture.ITALIC, size * 0.9), Color.web(GREY)));
        }
        return flow;
    }

    private static Text styled(String text, Font font, Color fill) {
        Text run = new Text(text);
        run.setFont(font);
        run.setFill(fill);
        return run;
    }

    /** A match's judgment: its mark, the concept the rater named, and the rater; or that no rater has judged it. */
    private static Node judgment(MatchRow row, Context context, double size) {
        if (row.judgment().isEmpty()) {
            Label open = new Label(NOT_JUDGED);
            open.setFont(Font.font(null, FontPosture.ITALIC, size * 0.9));
            open.setStyle("-fx-text-fill: " + GREY + ";");
            open.setMinWidth(Region.USE_PREF_SIZE);
            open.getStyleClass().add("stamped-document-matches-unjudged");
            return open;
        }
        Label mark = new Label(mark(row.judgment()));
        mark.setFont(Font.font(size));
        HBox box = new HBox(size * 0.4, mark);
        box.setAlignment(Pos.CENTER_LEFT);
        row.named().ifPresent(named -> box.getChildren().add(chip(named, context)));
        row.rater().ifPresent(rater -> box.getChildren().add(grey(rater, size)));
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * Each stored sentence's text by its identifier, read from the store for the document the
     * table's anchors name; nothing when the open store holds none of them.
     */
    private static Function<String, Optional<String>> storedSentences(MatchTable table) {
        try {
            for (MatchRow row : table.rows()) {
                Optional<PublicId> document = DocumentNarratives.documentOf(row.sentence());
                if (document.isPresent()) {
                    Map<String, String> texts = new HashMap<>();
                    DocumentNarratives.narrative(document.get()).ifPresent(narrative -> {
                        for (SegmentNarrative.Sentence sentence : narrative.sentences()) {
                            texts.put(sentence.id(), sentence.text());
                        }
                    });
                    return id -> Optional.ofNullable(texts.get(id));
                }
            }
        } catch (RuntimeException noStore) {
            // no store open, as in a test or a page drawn before one is: the matched words stand alone
        }
        return id -> Optional.empty();
    }

    /**
     * A concept's chip, or its name in italics when the open knowledge base does not hold it, as
     * when a table made against a larger knowledge base is read against a smaller one.
     */
    private static Node chip(KonceptRef concept, Context context) {
        PublicId id = PublicIds.of(concept.uuid());
        boolean held;
        try {
            held = PrimitiveData.get().hasPublicId(id);
        } catch (RuntimeException noStore) {
            held = false;
        }
        if (!held) {
            Label name = new Label(concept.label());
            name.setFont(Font.font(null, FontPosture.ITALIC, context.base() * 0.9));
            name.setTooltip(new Tooltip("Not in the open knowledge base: " + concept));
            return name;
        }
        return context.viewProperties() != null
                ? KonceptChips.chip(id, null, context.viewProperties(), context.base())
                : KonceptChips.chip(id, null, context.view(), context.base());
    }

    /** A block that is not a match table: the reason, then its source as written. */
    private static Node unreadable(Source source, String reason) {
        Label why = new Label("Not a match table: " + reason);
        why.setWrapText(true);
        why.setStyle("-fx-text-fill: #b3261e;");
        Label text = new Label(String.join("\n", source.lines()));
        text.setFont(Font.font("Monospaced", 12));
        VBox box = new VBox(4, why, text);
        box.getStyleClass().add("stamped-document-matches");
        return box;
    }

    /**
     * The grow of a match table: its Expand button, drawn always at the table's top right as a
     * figure in a document draws it, grows the table to its card, then to the journal window, then
     * to the whole screen. Each place has a fresh drawing of the table, its phrases filling the
     * place, so the drawing in the document is never moved; the contract button is at the grown
     * table's foot, where nothing of the window's own is drawn.
     */
    static final class Grow implements KlExpandable {

        private final Region figure;
        private final Supplier<Region> grown;

        /**
         * A table's grow.
         *
         * @param figure the table as the document draws it
         * @param grown  draws the table afresh for the place it grows into
         */
        Grow(Region figure, Supplier<Region> grown) {
            this.figure = figure;
            this.grown = grown;
        }

        @Override
        public Region figureNode() {
            return figure;
        }

        @Override
        public Region expandedView() {
            return grown.get();
        }

        @Override
        public ExpansionCorner expansionCorner() {
            return ExpansionCorner.TOP_RIGHT;
        }

        @Override
        public ExpansionCorner contractionCorner() {
            return ExpansionCorner.BOTTOM_RIGHT;
        }

        /** A table in a document is come to from its top and may be taller than its window: the button is drawn always. */
        @Override
        public boolean expandIconAlwaysShown() {
            return true;
        }

        /** The button stands at the corner itself, in the room the caption's title leaves for it. */
        @Override
        public Insets expandIconInsets() {
            return Insets.EMPTY;
        }

        /** A long table is worth the whole screen. */
        @Override
        public boolean osFullScreenCapable() {
            return true;
        }
    }
}
