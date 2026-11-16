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
import design.knowledge.stamp.adoc.git.GitRepo;
import design.knowledge.stamp.adoc.model.KonceptRef;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.terms.KernelConcept;
import network.ike.komet.document.GitDocumentMaterializer;
import network.ike.komet.text.matches.DescriptionFingerprint;
import network.ike.komet.text.matches.MatchRow;
import network.ike.komet.text.matches.MatchTable;
import network.ike.komet.text.matches.MatchTables;
import network.ike.komet.text.matches.RunIdentity;
import network.ike.komet.text.recognize.Candidate;
import network.ike.komet.text.recognize.Description;
import network.ike.komet.text.recognize.DescriptionMaps;
import network.ike.komet.text.recognize.Descriptions;
import network.ike.komet.text.recognize.Omitted;
import network.ike.komet.text.recognize.Reading;
import network.ike.komet.text.recognize.Recognition;
import network.ike.komet.text.recognize.Recognizer;
import network.ike.komet.text.recognize.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.function.LongPredicate;
import java.util.stream.Stream;

/**
 * The headless run of the description stage: opens a store, builds the description maps under the
 * default view, reads every AsciiDoc file of a collection, and writes the concordance report and a
 * table of every recognition (IKE-Network/ike-issues#1372).
 *
 * <p>A collection is read one of two ways. From a folder, each file is read as text, without the
 * store. From a git working tree, each committed file is materialized into the store from its
 * history, as Komet stores a document it opens, and its sentences are read back as the store holds
 * them ({@link StoredSentences}), so what is measured is the committed text and each recognition
 * carries its segment anchor; a file HEAD does not hold, a file of {@code include::} directives,
 * and a document the store holds no sentences for are listed with the reason instead. Either way, folders whose names begin with a dot and folders named
 * {@code target} are passed over.
 *
 * <p>Arguments: {@code --load <protobuf zip>} loads a knowledge base export into a memory-only
 * store, and may be given more than once: the IKE starter set first, since an export such as
 * SNOMED CT holds data only and relies on the starter set's patterns and kernel concepts;
 * {@code --documents <folder>} names a collection read from a folder;
 * {@code --repository <working tree>} names a collection read from the store, the working tree's
 * root, with {@code --documents} then naming the folder within it to read, the whole tree when
 * absent; {@code --out <folder>} receives {@code concordance.adoc}, {@code recognitions.tsv},
 * {@code left-out.tsv}, the descriptions left out of the maps with the reason for each, and
 * {@code not-read.tsv}, the files of a repository not read with the reason for each;
 * {@code --domain <uuid>} limits the maps to that concept and its descendants;
 * {@code --navigation stated} reports the parts of the hierarchy by stated rather than inferred
 * parents, for a knowledge base that has not been classified; with {@code --repository},
 * {@code --tables <matcher>} writes each document's match table into its file in the working tree,
 * uncommitted, naming the matcher by its released version, the workspace checkpoint it was built
 * from, or {@code development}, and {@code --data <data>} names the data loaded, each by its name
 * and version, the loaded files' names when absent.
 */
public final class ConcordanceRun {

    private static final Logger LOG = LoggerFactory.getLogger(ConcordanceRun.class);

    /** The sentence splitter's artifact, which a run's match table names by its version. */
    private static final String SPLITTER = "stamp-adoc-core";

    /** Where the splitter's jar records its version. */
    private static final String SPLITTER_POM = "/META-INF/maven/design.knowledge.stamp/stamp-adoc-core/pom.properties";

    /** How many entries of the gap list the report keeps. */
    public static final int GAP_LIMIT = 100;

    /** The table of every recognition. */
    public static final String RECOGNITIONS_TABLE = "recognitions.tsv";

    /** The table of descriptions left out of the maps, with the reason for each. */
    public static final String LEFT_OUT_TABLE = "left-out.tsv";

    /** The table of a repository's files not read from the store, with the reason for each. */
    public static final String NOT_READ_TABLE = "not-read.tsv";

    private ConcordanceRun() {
    }

    /** Which parents the report's parts of the hierarchy are read from. */
    public enum Hierarchy {
        /** The classified hierarchy. */
        INFERRED,
        /** The hierarchy as authored, for a knowledge base that has not been classified. */
        STATED
    }

    /** Where a run's documents come from and how they are read. */
    public sealed interface Source {

        /**
         * The folder whose AsciiDoc files are read, recursively.
         *
         * @return the folder
         */
        Path documents();

        /**
         * AsciiDoc files read as text from a folder, without the store; a recognition has no anchor.
         *
         * @param documents the folder
         */
        record Folder(Path documents) implements Source {

            /**
             * Validates the source.
             *
             * @throws NullPointerException if {@code documents} is null
             */
            public Folder {
                Objects.requireNonNull(documents, "documents");
            }
        }

        /**
         * AsciiDoc files of a git working tree, each materialized into the store from its history
         * and read back as the store holds it, so each recognition carries its segment anchor.
         *
         * @param root      the working tree's root, whose name with each file's path names the
         *                  stored document as Komet names it
         * @param documents the folder within the working tree whose files are read
         * @param tables    the match tables the run writes into the working tree's files, if any
         */
        record Repository(Path root, Path documents, Optional<Tables> tables) implements Source {

            /**
             * Makes both paths absolute and checks that the folder lies within the working tree.
             *
             * @throws NullPointerException     if a component is null
             * @throws IllegalArgumentException if the folder is not within the working tree
             */
            public Repository {
                root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
                documents = Objects.requireNonNull(documents, "documents").toAbsolutePath().normalize();
                Objects.requireNonNull(tables, "tables");
                if (!documents.startsWith(root)) {
                    throw new IllegalArgumentException(documents + " is not within the working tree " + root);
                }
            }

            /**
             * A repository read without writing match tables.
             *
             * @param root      the working tree's root
             * @param documents the folder within it whose files are read
             */
            public Repository(Path root, Path documents) {
                this(root, documents, Optional.empty());
            }
        }
    }

    /**
     * The match tables a repository run writes, one at the end of each document it reads through
     * the store, uncommitted in the working tree (ike-lab-documents topic
     * {@code dev-conceptification-match-table}).
     *
     * @param matcher the matcher's released version or the workspace checkpoint it was built from,
     *                or {@link RunIdentity#DEVELOPMENT}
     * @param data    the data the knowledge base was loaded from, each by its name and version
     */
    public record Tables(String matcher, String data) {

        /**
         * Validates the tables' naming.
         *
         * @throws NullPointerException     if either is null
         * @throws IllegalArgumentException if the matcher is blank
         */
        public Tables {
            if (Objects.requireNonNull(matcher, "matcher").isBlank()) {
                throw new IllegalArgumentException("a run names its matcher, " + RunIdentity.DEVELOPMENT + " when nothing else does");
            }
            Objects.requireNonNull(data, "data");
        }
    }

    /**
     * What a run reads and where it writes.
     *
     * @param loads     the protobuf zips loaded, in order, into a memory-only store
     * @param source    where the documents come from and how they are read
     * @param out       the folder the report and the tables are written to
     * @param domain    the UUID of the concept whose descendants limit the maps, if any
     * @param hierarchy which parents the report's parts of the hierarchy are read from
     */
    public record Options(List<Path> loads, Source source, Path out, Optional<String> domain, Hierarchy hierarchy) {

        /**
         * Copies the list of exports.
         */
        public Options {
            loads = List.copyOf(loads);
        }
    }

    /**
     * Runs the description stage from the command line.
     *
     * @param args the arguments described on the class
     * @throws IOException if a document cannot be read or the report cannot be written
     */
    public static void main(String[] args) throws IOException {
        Concordance.Report report = run(parse(args));
        LOG.info("{} sentences, {} with a reference ({}%), {} recognitions", report.sentences(),
                report.sentencesWithReference(), Math.round(100.0 * report.coverage()), report.recognitions());
    }

    /**
     * Opens a memory-only store loaded with the exports, measures the collection, and stops the store.
     *
     * @param options what to read and where to write
     * @return the report
     * @throws IOException if a document cannot be read or the report cannot be written
     */
    public static Concordance.Report run(Options options) throws IOException {
        PrimitiveData.selectControllerByName("Load Memory-Only 64-bit Store");
        PrimitiveData.start();
        try {
            for (Path load : options.loads()) {
                long count = new LoadEntitiesFromProtobufFile(load.toFile()).compute().getTotalCount();
                LOG.info("{} entities loaded from {}", count, load.getFileName());
            }
            ViewCalculator view = Calculators.View.Default();
            return measure(view, domain(view, options.domain()), options.hierarchy(), options.source(), options.out());
        } finally {
            PrimitiveData.stop();
        }
    }

    /**
     * Measures a collection against a running store. The description maps are built before any
     * document of a repository is materialized, so the documents' own entities never name concepts
     * in them.
     *
     * @param view      the view the descriptions and the hierarchy are read under
     * @param concepts  which concepts' descriptions go in the maps
     * @param hierarchy which parents the report's parts of the hierarchy are read from
     * @param source    where the documents come from and how they are read
     * @param out       the folder the report and the tables are written to
     * @return the report
     * @throws IOException if a document cannot be read or materialized, or the report cannot be written
     */
    public static Concordance.Report measure(ViewCalculator view, LongPredicate concepts, Hierarchy hierarchy,
                                             Source source, Path out) throws IOException {
        List<Description> descriptions = Descriptions.read(view, concepts, KernelConcept.LANGUAGE.nid());
        DescriptionMaps maps = DescriptionMaps.build(descriptions);
        LOG.info("{} descriptions read; maps: {}", descriptions.size(), maps.statistics());
        Concordance concordance = new Concordance(new Branches(navigation(view, hierarchy),
                view::getPreferredDescriptionTextWithFallbackOrNid));
        Files.createDirectories(out);
        writeLeftOut(view, maps, out);
        long readNanos = 0L;
        try (Recognizer recognizer = new Recognizer(maps);
             BufferedWriter table = Files.newBufferedWriter(out.resolve(RECOGNITIONS_TABLE));
             BufferedWriter notReadTable = Files.newBufferedWriter(out.resolve(NOT_READ_TABLE))) {
            table.write("document\tsentence\tsection\tstart\tend\twords\tcandidates\tanchor\n");
            notReadTable.write("file\twhy\n");
            switch (source) {
                case Source.Folder folder -> {
                    for (Path file : adocFiles(folder.documents())) {
                        String name = folder.documents().relativize(file).toString();
                        for (Sentence sentence : AsciiDocSentences.read(file, name)) {
                            readNanos += read(recognizer, view, concordance, table, sentence, Optional.empty());
                        }
                    }
                }
                case Source.Repository repository -> {
                    Optional<RunIdentity> identity = repository.tables().map(tables -> identity(view, maps, tables));
                    int written = 0;
                    long materializeNanos = 0L;
                    try (GitRepo git = GitRepo.open(repository.root())) {
                        for (Path file : adocFiles(repository.documents())) {
                            Path relative = repository.root().relativize(file);
                            String name = relative.toString();
                            Optional<List<String>> committed = committedLines(git, relative);
                            if (committed.isEmpty()) {
                                notRead(concordance, notReadTable, name, NotRead.NOT_COMMITTED);
                                continue;
                            }
                            if (GitDocumentMaterializer.includeCount(committed.get()) > 0) {
                                notRead(concordance, notReadTable, name, NotRead.ASSEMBLY);
                                continue;
                            }
                            long began = System.nanoTime();
                            GitDocumentMaterializer.Result stored = GitDocumentMaterializer.materialize(repository.root(), file);
                            materializeNanos += System.nanoTime() - began;
                            Optional<StoredSentences> sentences = StoredSentences.read(stored.anchor(), name);
                            if (sentences.isEmpty()) {
                                notRead(concordance, notReadTable, name, NotRead.NO_STORED_SENTENCES);
                                continue;
                            }
                            for (Sentence sentence : sentences.get().sentences()) {
                                readNanos += read(recognizer, view, concordance, table, sentence, sentences);
                            }
                            if (identity.isPresent() && writeTable(file, sentences.get(), recognizer, view, identity.get())) {
                                written++;
                            }
                        }
                    }
                    if (identity.isPresent()) {
                        LOG.info("match tables written into {} documents of {}", written, repository.root().getFileName());
                    }
                    LOG.info("documents of {} materialized in {} ms", repository.root().getFileName(),
                            materializeNanos / 1_000_000L);
                }
            }
        }
        Concordance.Report report = concordance.report(maps.statistics(), readNanos / 1_000_000L, GAP_LIMIT);
        String title = switch (source) {
            case Source.Folder folder -> "Concordance of " + folder.documents().getFileName();
            case Source.Repository repository -> "Concordance of " + repository.documents().getFileName()
                    + ", read from the store";
        };
        Files.writeString(out.resolve("concordance.adoc"), Concordance.asciiDoc(report, title));
        return report;
    }

    /**
     * Which concepts' descriptions go in the maps: every concept, or a concept and its descendants.
     *
     * @param view   the view whose navigation gives the descendants
     * @param domain the UUID of the domain's concept, if any
     * @return the filter
     */
    public static LongPredicate domain(ViewCalculator view, Optional<String> domain) {
        if (domain.isEmpty()) {
            return conceptNid -> true;
        }
        long root = PrimitiveData.nid(PublicIds.of(domain.get()));
        LongIdSet descendants = view.descendentsOf(root);
        return conceptNid -> conceptNid == root || descendants.contains(conceptNid);
    }

    /** Reads one sentence, adds it to the concordance and writes its recognitions; returns the nanoseconds reading took. */
    private static long read(Recognizer recognizer, ViewCalculator view, Concordance concordance, BufferedWriter table,
                             Sentence sentence, Optional<StoredSentences> stored) throws IOException {
        long began = System.nanoTime();
        Reading reading = recognizer.read(sentence.text());
        long nanos = System.nanoTime() - began;
        concordance.add(sentence, reading);
        for (Recognition recognition : reading.recognitions()) {
            String anchor = stored.isPresent() ? stored.get().anchor(sentence, recognition) : "";
            table.write(row(view, sentence, recognition, anchor));
        }
        return nanos;
    }

    /** A file's lines as HEAD holds them; empty when HEAD does not hold the file, as the materializer reads it. */
    private static Optional<List<String>> committedLines(GitRepo git, Path relative) {
        try {
            return Optional.of(git.headLines(relative));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void notRead(Concordance concordance, BufferedWriter table, String name, NotRead why) throws IOException {
        concordance.notRead(why);
        table.write(clean(name) + "\t" + why + "\n");
    }

    private static void writeLeftOut(ViewCalculator view, DescriptionMaps maps, Path out) throws IOException {
        try (BufferedWriter leftOut = Files.newBufferedWriter(out.resolve(LEFT_OUT_TABLE))) {
            leftOut.write("concept\tdescription\twhy\n");
            for (Omitted omitted : maps.omitted()) {
                Description description = omitted.description();
                leftOut.write(view.getPreferredDescriptionTextWithFallbackOrNid(description.conceptNid()) + " ["
                        + description.conceptNid() + "]\t" + clean(description.text()) + "\t" + omitted.why() + "\n");
            }
        }
    }

    private static NavigationCalculator navigation(ViewCalculator view, Hierarchy hierarchy) {
        return switch (hierarchy) {
            case INFERRED -> view;
            case STATED -> Calculators.Navigation.stated(view.stampCoordinate(), view.languageCoordinateList());
        };
    }

    /** What a run that writes match tables was made by and against. */
    private static RunIdentity identity(ViewCalculator view, DescriptionMaps maps, Tables tables) {
        String fingerprint = DescriptionFingerprint.of(maps.included(), PrimitiveData::publicId);
        return new RunIdentity(Instant.now(), tables.matcher(), RunIdentity.stages(EnumSet.of(Stage.DESCRIPTION)),
                splitter(), tables.data(), view.stampCoordinate().stampPosition().toUserString(), fingerprint);
    }

    /**
     * The sentence splitter by the version its jar records, since it lies outside the working set
     * and no checkpoint names it.
     */
    private static String splitter() {
        Properties pom = new Properties();
        try (InputStream in = Segmenter.class.getResourceAsStream(SPLITTER_POM)) {
            if (in != null) {
                pom.load(in);
            }
        } catch (IOException e) {
            LOG.warn("the splitter's version could not be read: {}", e.getMessage());
        }
        return SPLITTER + " " + pom.getProperty("version", "(version not recorded)");
    }

    /**
     * Writes a document's match table into its file in the working tree, keeping the rows a rater
     * has judged in older tables.
     *
     * @return whether the file changed
     */
    private static boolean writeTable(Path file, StoredSentences sentences, Recognizer recognizer, ViewCalculator view,
                                      RunIdentity identity) throws IOException {
        List<MatchRow> rows = new ArrayList<>();
        for (AnchoredRecognition anchored : sentences.recognize(recognizer)) {
            Recognition recognition = anchored.recognition();
            MatchRow.Place place = sentences.place(anchored.sentence(), recognition);
            List<KonceptRef> candidates = new ArrayList<>();
            for (Candidate candidate : recognition.candidates()) {
                candidates.add(konceptRef(view, candidate.conceptNid()));
            }
            rows.add(MatchRow.proposed(place.sentence(), place.from(), place.to(), recognition.words(), candidates,
                    recognition.candidates().getFirst().stage(), OptionalDouble.empty()));
        }
        List<String> lines = Files.readAllLines(file);
        List<String> rewritten = MatchTables.rerun(lines, new MatchTable(identity, rows));
        if (rewritten.equals(lines)) {
            return false;
        }
        Files.write(file, rewritten);
        return true;
    }

    /** A concept as a match table writes it: its first public id and its name under the view. */
    private static KonceptRef konceptRef(ViewCalculator view, long nid) {
        String label = view.getPreferredDescriptionTextWithFallbackOrNid(nid).replace(']', ')').replace('\n', ' ');
        return new KonceptRef(PrimitiveData.publicId(nid).asUuidArray()[0], label);
    }

    private static String row(ViewCalculator view, Sentence sentence, Recognition recognition, String anchor) {
        List<String> candidates = new ArrayList<>();
        for (Candidate candidate : recognition.candidates()) {
            candidates.add(view.getPreferredDescriptionTextWithFallbackOrNid(candidate.conceptNid())
                    + " [" + candidate.conceptNid() + "]");
        }
        return String.join("\t", sentence.document(), Integer.toString(sentence.index()), clean(sentence.section()),
                Integer.toString(recognition.start()), Integer.toString(recognition.end()), clean(recognition.words()),
                clean(String.join("; ", candidates)), clean(anchor)) + "\n";
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\n', ' ');
    }

    /** The AsciiDoc files under a folder, in path order, passing over dot folders and {@code target}. */
    private static List<Path> adocFiles(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".adoc"))
                    .filter(path -> !passedOver(folder.relativize(path)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean passedOver(Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String folder = relative.getName(i).toString();
            if (folder.startsWith(".") || folder.equals("target")) {
                return true;
            }
        }
        return false;
    }

    private static Options parse(String[] args) {
        List<Path> loads = new ArrayList<>();
        Path documents = null;
        Path repository = null;
        Path out = null;
        String domain = null;
        Hierarchy hierarchy = Hierarchy.INFERRED;
        String matcher = null;
        String data = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--load" -> loads.add(Path.of(args[i + 1]));
                case "--documents" -> documents = Path.of(args[i + 1]);
                case "--repository" -> repository = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--domain" -> domain = args[i + 1];
                case "--navigation" -> hierarchy = Hierarchy.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
                case "--tables" -> matcher = args[i + 1];
                case "--data" -> data = args[i + 1];
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (loads.isEmpty() || out == null || (documents == null && repository == null)
                || (matcher != null && repository == null)) {
            throw new IllegalArgumentException("usage: --load <zip> [--load <zip>]"
                    + " (--documents <folder> | --repository <working tree> [--documents <folder within it>]"
                    + " [--tables <matcher> [--data <data>]])"
                    + " --out <folder> [--domain <uuid>] [--navigation inferred|stated]");
        }
        Optional<Tables> tables = Optional.empty();
        if (matcher != null) {
            String loaded = String.join(", ", loads.stream().map(load -> load.getFileName().toString()).toList());
            tables = Optional.of(new Tables(matcher, data == null ? loaded : data));
        }
        Source source = repository == null
                ? new Source.Folder(documents)
                : new Source.Repository(repository, documents == null ? repository : documents, tables);
        return new Options(loads, source, out, Optional.ofNullable(domain), hierarchy);
    }
}
