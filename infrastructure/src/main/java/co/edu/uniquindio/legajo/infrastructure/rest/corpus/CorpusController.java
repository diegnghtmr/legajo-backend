package co.edu.uniquindio.legajo.infrastructure.rest.corpus;

import co.edu.uniquindio.legajo.application.corpus.CorpusService;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * {@code GET /api/v1/corpus} and {@code GET /api/v1/corpus/{id}} (TRD §6.6, feature doc task
 * A3). Pure adapter: delegates every rule to {@link CorpusService} (application) and only
 * shapes the response as {@link CorpusSummaryResponse}/{@link CorpusDocumentResponse}. An
 * unknown id throws {@link ResourceNotFoundException} (task A3b), mapped to a 404 RFC 9457
 * Problem Detail by {@code ProblemDetailExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/corpus")
public class CorpusController {

    private final CorpusService corpusService;

    public CorpusController(CorpusService corpusService) {
        this.corpusService = Objects.requireNonNull(corpusService, "corpusService");
    }

    /** {@code GET /api/v1/corpus}: every document's id/title/authors, in corpus order. */
    @GetMapping
    public List<CorpusSummaryResponse> list() {
        return corpusService.listDocuments().stream().map(CorpusSummaryResponse::from).toList();
    }

    /** {@code GET /api/v1/corpus/{id}}: the full document, or a 404 Problem Detail. */
    @GetMapping("/{id}")
    public CorpusDocumentResponse get(@PathVariable("id") String id) {
        CorpusDocument document = corpusService.findDocument(id)
                .orElseThrow(() -> new ResourceNotFoundException("no corpus document with id: " + id));
        return CorpusDocumentResponse.from(document);
    }
}
