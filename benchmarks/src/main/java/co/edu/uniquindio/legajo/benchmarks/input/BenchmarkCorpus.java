package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.preprocess.TextPreprocessor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the real reference corpus (n = 20 documents) for the classic-pairwise and clustering
 * SLO benchmarks, and derives the preprocessed token streams and flattened token pool the
 * pairwise-curve benchmarks build their synthetic sequences from
 * ({@link SyntheticTokenSequences}).
 *
 * <p>Reads {@code data/corpus.json} directly with Jackson rather than depending on
 * {@code :infrastructure}'s {@code JsonCorpusRepository}: {@code benchmarks} only ever
 * depends on {@code :domain}, per this codebase's module boundary rules, so this class
 * carries its own copy of the wire DTOs ({@link BenchmarkCorpusJson}/
 * {@link BenchmarkCorpusDocumentJson}).
 */
public final class BenchmarkCorpus {

    private BenchmarkCorpus() {
    }

    /** Loads {@code data/corpus.json} (resolved via {@link CorpusPaths}) into a domain {@link Corpus}. */
    public static Corpus load() {
        Path corpusPath = CorpusPaths.resolveCorpusJson();
        JsonMapper jsonMapper = JsonMapper.builder().build();
        try (InputStream in = Files.newInputStream(corpusPath)) {
            return jsonMapper.readValue(in, BenchmarkCorpusJson.class).toDomain();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read corpus from " + corpusPath, e);
        }
    }

    /**
     * Runs the five-step preprocessing pipeline over every document's abstract, in
     * corpus order, with Porter stemming off (the v1 default, same as every other caller of
     * {@link TextPreprocessor}).
     */
    public static List<List<String>> preprocessedTokenStreams(Corpus corpus) {
        TextPreprocessor preprocessor = new TextPreprocessor();
        List<List<String>> tokenStreams = new ArrayList<>(corpus.documents().size());
        for (CorpusDocument document : corpus.documents()) {
            tokenStreams.add(preprocessor.preprocess(document.abstractText()).tokens());
        }
        return List.copyOf(tokenStreams);
    }

    /**
     * Concatenates every token stream, in the given order, into the single pool the
     * pairwise-curve benchmarks cycle through, built by concatenating the corpus's own
     * tokens.
     */
    public static List<String> flattenTokenPool(List<List<String>> tokenStreams) {
        List<String> pool = new ArrayList<>();
        for (List<String> tokens : tokenStreams) {
            pool.addAll(tokens);
        }
        return List.copyOf(pool);
    }
}
