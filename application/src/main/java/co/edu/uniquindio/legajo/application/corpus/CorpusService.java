package co.edu.uniquindio.legajo.application.corpus;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestration behind {@code GET /corpus} and {@code GET /corpus/{id}} (TRD §6.6): reads
 * the corpus through {@link CorpusRepository} and shapes it for each of the two endpoints.
 * Pure orchestration — no Spring, no HTTP, no DTO/JSON annotations, no caching (request-keyed
 * caching is a separate feature task, A5).
 *
 * <p>Reloads the corpus from {@link CorpusRepository} on every call rather than caching it
 * in memory: the TRD does not ask for an in-memory corpus cache here, and {@code
 * corpus.json} is "generated, not edited" (backend/AGENTS.md) so a fresh load is always
 * correct, if not the fastest possible implementation — performance is out of this
 * feature's A1/A2 scope.
 */
public final class CorpusService {

    private final CorpusRepository corpusRepository;

    public CorpusService(CorpusRepository corpusRepository) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
    }

    /** {@code GET /corpus}: every document's id/title/authors, in corpus order. */
    public List<CorpusSummary> listDocuments() {
        return corpusRepository.load().documents().stream()
                .map(document -> new CorpusSummary(document.id(), document.title(), document.authors()))
                .toList();
    }

    /** {@code GET /corpus/{id}}: the full document (including its abstract), if it exists. */
    public Optional<CorpusDocument> findDocument(String id) {
        Objects.requireNonNull(id, "id");
        return corpusRepository.load().documents().stream()
                .filter(document -> document.id().equals(id))
                .findFirst();
    }
}
