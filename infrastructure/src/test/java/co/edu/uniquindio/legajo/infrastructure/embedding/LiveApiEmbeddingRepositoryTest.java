package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link LiveApiEmbeddingRepository} against a stubbed OpenAI-compatible embeddings endpoint
 * (feature doc task A8, TRD §6.3 "Modo en vivo de {@code embedding-api} (fijado)"): fetches
 * every corpus document's vector over the network, renormalizes it, caches it so each
 * document is requested at most once per instance, and maps every failure shape — 5xx,
 * timeout, and a missing/blank API key — to {@link EmbeddingApiException}, never a silent
 * fallback. No test here needs a real key or network.
 */
class LiveApiEmbeddingRepositoryTest {

    private static final String MODEL = "gemini-embedding-2-preview";
    private static final int DIMENSION = 4;

    private WireMockServer wireMockServer;
    private CorpusRepository corpusRepository;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        corpusRepository = fixedCorpus("d01", "d02", "d03");
    }

    @AfterEach
    void stopServer() {
        wireMockServer.stop();
    }

    @Test
    void loadFetchesEveryCorpusDocumentAndL2RenormalizesEachVector() {
        // Deliberately not unit length, so a passing assertion proves this class's pipeline
        // (via OpenAiCompatibleEmbedder) renormalizes rather than trusting the provider.
        stubEmbeddingResponse("[2.0, 0.0, 0.0, 0.0]");

        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        EmbeddingCache cache = repository.load();

        assertThat(cache.model()).isEqualTo(MODEL);
        assertThat(cache.dimension()).isEqualTo(DIMENSION);
        assertThat(cache.corpusSha256()).isEqualTo(corpusRepository.load().corpusSha256());
        assertThat(cache.vectors()).hasSize(3);
        for (EmbeddingVector vector : cache.vectors()) {
            assertThat(EmbeddingVector.l2Norm(vector.values())).isCloseTo(1.0, within(1e-12));
        }
    }

    @Test
    void eachDocumentIsRequestedAtMostOnceAcrossMultipleLoadCalls() {
        stubEmbeddingResponse("[1.0, 0.0, 0.0, 0.0]");
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        repository.load();
        repository.load();

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/embeddings")));
    }

    @Test
    void wrapsAServerErrorResponseInEmbeddingApiException() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"boom\"}}")));
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        assertThatThrownBy(repository::load).isInstanceOf(EmbeddingApiException.class);
    }

    @Test
    void wrapsAClientSideTimeoutInEmbeddingApiException() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
        LiveApiEmbeddingRepository repository = new LiveApiEmbeddingRepository(
                corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION, Duration.ofMillis(300));

        assertThatThrownBy(repository::load).isInstanceOf(EmbeddingApiException.class);
    }

    @Test
    void wrapsAMissingApiKeyInEmbeddingApiExceptionInsteadOfFailingAtConstruction() {
        // A blank key must never crash construction (TRD: a missing key answers 503 per
        // request, not at startup); the WireMock server answers 401 regardless of whether the
        // SDK sends a real Authorization header, so the request-time failure is observed
        // either way.
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"missing api key\"}}")));

        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "", wireMockServer.baseUrl(), MODEL, DIMENSION);

        assertThatThrownBy(repository::load).isInstanceOf(EmbeddingApiException.class);
    }

    /**
     * {@code R3-client-construction-failure-path-untested}: {@link
     * LiveApiEmbeddingRepository#embedder()} wraps any exception raised while <em>building</em>
     * the underlying {@link OpenAiCompatibleEmbedder} client in {@link EmbeddingApiException},
     * the same as a call failure — never a startup crash. A malformed base URL makes {@code
     * new OpenAiCompatibleEmbedder(...)} itself throw {@link IllegalArgumentException} (Spring's
     * {@code RestClient} builder rejects it while parsing the URI), verified empirically
     * against this exact string before writing this test, not assumed; no WireMock stub is
     * needed since the failure happens before any network call is attempted.
     */
    @Test
    void wrapsAMalformedBaseUrlConstructionFailureInEmbeddingApiException() {
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", "ht!tp://foo", MODEL, DIMENSION);

        assertThatThrownBy(repository::load)
                .isInstanceOf(EmbeddingApiException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveIsUnsupported() {
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        assertThatThrownBy(() -> repository.save(
                new EmbeddingCache("1.0", "1.0", "sha", MODEL, DIMENSION, List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void stubEmbeddingResponse(String embeddingArray) {
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "index": 0, "embedding": %s } ],
                  "model": "%s",
                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                }
                """.formatted(embeddingArray, MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static CorpusRepository fixedCorpus(String... ids) {
        List<CorpusDocument> documents = List.of(ids).stream()
                .map(id -> new CorpusDocument(id, "title-" + id, List.of("author"), "abstract text for " + id,
                        "source", "extractor", true, "sha-" + id))
                .toList();
        Corpus corpus = new Corpus("1.0", documents.size(), "corpus-sha-" + String.join("-", ids), documents);
        return new CorpusRepository() {
            @Override
            public Corpus load() {
                return corpus;
            }

            @Override
            public void save(Corpus toSave) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
