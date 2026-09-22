package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 * (feature doc task A8/F5, TRD §6.3 "Modo en vivo de {@code embedding-api} (fijado)"): fetches
 * every not-yet-cached corpus document's vector with ONE batched network request per
 * {@link LiveApiEmbeddingRepository#load()} call ({@code R3-live-load-fetches-whole-corpus-serially}),
 * renormalizes each vector, caches it so each document is requested at most once per instance,
 * and maps every failure shape — 5xx, timeout, a missing/blank API key, and a provider vector
 * count that does not match the request — to {@link EmbeddingApiException}, never a silent
 * fallback or a silently misassigned vector. No test here needs a real key or network.
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
        stubEmbeddingResponse(3, "[2.0, 0.0, 0.0, 0.0]");

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

    /**
     * {@code R3-live-load-fetches-whole-corpus-serially}: {@link LiveApiEmbeddingRepository#load()}
     * must send exactly ONE batched request for all not-yet-cached documents (TRD §6.3), with
     * the corpus's abstracts in corpus order, and map each returned vector back to the right
     * document id strictly by position. Each stub vector below is a distinct one-hot direction
     * precisely so a positional mix-up (e.g. the old per-document loop reversed, or an
     * off-by-one) makes this assertion fail instead of passing by coincidence.
     */
    @Test
    void loadSendsExactlyOneBatchedRequestInCorpusOrderAndMapsVectorsToTheRightDocumentIds() {
        stubEmbeddingResponse(3, List.of("[1.0, 0.0, 0.0, 0.0]", "[0.0, 1.0, 0.0, 0.0]", "[0.0, 0.0, 1.0, 0.0]"));

        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        EmbeddingCache cache = repository.load();

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));
        JsonNode input = requestBodyOf(wireMockServer.getAllServeEvents().get(0)).get("input");
        assertThat(input.size()).isEqualTo(3);
        assertThat(input.get(0).asString()).isEqualTo("abstract text for d01");
        assertThat(input.get(1).asString()).isEqualTo("abstract text for d02");
        assertThat(input.get(2).asString()).isEqualTo("abstract text for d03");

        assertThat(vectorFor(cache, "d01").values()).containsExactly(1.0, 0.0, 0.0, 0.0);
        assertThat(vectorFor(cache, "d02").values()).containsExactly(0.0, 1.0, 0.0, 0.0);
        assertThat(vectorFor(cache, "d03").values()).containsExactly(0.0, 0.0, 1.0, 0.0);
    }

    @Test
    void aSecondLoadMakesZeroFurtherRequestsAfterTheFirstBatchCachesEveryDocument() {
        stubEmbeddingResponse(3, "[1.0, 0.0, 0.0, 0.0]");
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        repository.load();
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));

        repository.load();

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));
    }

    /**
     * {@code R3-live-load-fetches-whole-corpus-serially}: a provider response whose vector
     * count does not match the request must fail closed instead of silently misassigning
     * vectors, and — because {@link LiveApiEmbeddingRepository}'s cache is only populated
     * after a whole batch succeeds — a failed batch must leave nothing cached, so the very
     * next {@code load()} re-requests every document, not just the ones the failed attempt
     * happened not to reach.
     */
    @Test
    void aProviderVectorCountMismatchFailsClosedAndANextLoadRetriesEveryDocument() {
        stubEmbeddingResponse(2, "[1.0, 0.0, 0.0, 0.0]"); // corpus has 3 documents, provider returns 2
        LiveApiEmbeddingRepository repository =
                new LiveApiEmbeddingRepository(corpusRepository, "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION);

        assertThatThrownBy(repository::load).isInstanceOf(EmbeddingApiException.class);

        wireMockServer.resetAll();
        stubEmbeddingResponse(3, "[1.0, 0.0, 0.0, 0.0]");

        EmbeddingCache cache = repository.load();

        assertThat(cache.vectors()).hasSize(3);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));
        assertThat(requestBodyOf(wireMockServer.getAllServeEvents().get(0)).get("input").size()).isEqualTo(3);
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

    /** Stubs a batch response of {@code count} items, all sharing the same {@code embeddingArray}. */
    private void stubEmbeddingResponse(int count, String embeddingArray) {
        stubEmbeddingResponse(count, java.util.Collections.nCopies(count, embeddingArray));
    }

    /** Stubs a batch response with one distinct embedding array per item, in order. */
    private void stubEmbeddingResponse(int count, List<String> embeddingArrays) {
        if (embeddingArrays.size() != count) {
            throw new IllegalArgumentException("embeddingArrays must have exactly " + count + " entries");
        }
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    { "object": "embedding", "index": %d, "embedding": %s }
                    """.formatted(i, embeddingArrays.get(i)));
        }
        String body = """
                {
                  "object": "list",
                  "data": [ %s ],
                  "model": "%s",
                  "usage": { "prompt_tokens": %d, "total_tokens": %d }
                }
                """.formatted(items, MODEL, count * 3, count * 3);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static JsonNode requestBodyOf(com.github.tomakehurst.wiremock.stubbing.ServeEvent event) {
        LoggedRequest request = event.getRequest();
        return JsonMapper.builder().build().readTree(request.getBodyAsString());
    }

    private static EmbeddingVector vectorFor(EmbeddingCache cache, String documentId) {
        return cache.vectors().stream()
                .filter(vector -> vector.documentId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no vector for document '" + documentId + "'"));
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
