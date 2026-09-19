package co.edu.uniquindio.legajo.infrastructure.corpus;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Output adapter for {@link CorpusRepository}: reads and writes {@code data/corpus.json}
 * exactly in the TRD §9 schema — pretty-printed, UTF-8, a stable field order (carried by
 * {@link CorpusJson}/{@link CorpusDocumentJson}, whose declaration order Jackson uses
 * for record serialization) and a trailing newline, so the file diffs cleanly in git
 * regardless of the platform that generated it.
 *
 * <p>TRD §9's "generated, not edited" rule for {@code corpus.json} means this adapter
 * is only ever invoked by the ingestion/validation use cases (task T4's {@code
 * IngestCorpus} and {@code ValidateCorpus}), never by a human editing the file by hand.
 */
public final class JsonCorpusRepository implements CorpusRepository {

    private static final DefaultIndenter LF_INDENTER = new DefaultIndenter("  ", "\n");

    private final Path corpusPath;
    private final JsonMapper jsonMapper;

    public JsonCorpusRepository(Path corpusPath) {
        this.corpusPath = Objects.requireNonNull(corpusPath, "corpusPath");
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    public Corpus load() {
        try (InputStream in = Files.newInputStream(corpusPath)) {
            return jsonMapper.readValue(in, CorpusJson.class).toDomain();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read corpus from " + corpusPath, e);
        }
    }

    @Override
    public void save(Corpus corpus) {
        Objects.requireNonNull(corpus, "corpus");
        CorpusJson json = CorpusJson.fromDomain(corpus);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
                .withObjectIndenter(LF_INDENTER)
                .withArrayIndenter(LF_INDENTER);
        String content = jsonMapper.writer().with(prettyPrinter).writeValueAsString(json);
        try {
            Files.writeString(corpusPath, content + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write corpus to " + corpusPath, e);
        }
    }
}
