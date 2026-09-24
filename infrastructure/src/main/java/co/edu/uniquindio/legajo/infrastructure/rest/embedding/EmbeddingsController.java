package co.edu.uniquindio.legajo.infrastructure.rest.embedding;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingStatus;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingsService;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * {@code GET /api/v1/embeddings/status}: one response carrying both embedding families, one
 * object per capability. Pure adapter: {@link EmbeddingsService#status} (application)
 * computes one family's status at a time — this class calls it twice, once per {@link
 * EmbeddingRepository} bean {@code DomainConfiguration} registers, exactly the composition
 * its own Javadoc anticipated ("the REST layer can call it once for a single-family response
 * or twice ... for a per-family response").
 *
 * <p><b>{@code device} — a fixed, undocumented deployment fact (author decision, flagged).</b>
 * {@code EmbeddingsService.status} requires a {@code device} argument for every call, but
 * the fixed rule only requires that the default profile is CPU-only, and there is no
 * {@code application.yml} key for it. This class supplies the
 * fixed literal {@code "cpu"} rather than inventing a new configuration property — not
 * part of the fixed rule, so flagged here rather than silently decided.
 */
@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingsController {

    private static final String DEVICE = "cpu";

    private final EmbeddingsService embeddingsService;
    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final EmbeddingProviderMode embeddingProviderMode;

    public EmbeddingsController(EmbeddingsService embeddingsService,
            @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository,
            EmbeddingProviderMode embeddingProviderMode) {
        this.embeddingsService = Objects.requireNonNull(embeddingsService, "embeddingsService");
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
        this.embeddingProviderMode = Objects.requireNonNull(embeddingProviderMode, "embeddingProviderMode");
    }

    @GetMapping("/status")
    public EmbeddingStatusResponse status() {
        EmbeddingStatus local = embeddingsService.status(localEmbeddingRepository, DEVICE, embeddingProviderMode);
        EmbeddingStatus api = embeddingsService.status(apiEmbeddingRepository, DEVICE, embeddingProviderMode);
        return new EmbeddingStatusResponse(
                EmbeddingLocalStatusResponse.from(local), EmbeddingApiStatusResponse.from(api));
    }
}
