package co.edu.uniquindio.legajo.infrastructure.embedding;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Works around two verified compatibility gaps between Google's Gemini OpenAI-compatible
 * embeddings endpoint and the official OpenAI Java SDK that Spring AI 2.0.x's
 * {@code OpenAiEmbeddingModel} is built on top of (both reproduced directly against the real
 * endpoint, {@code curl}-verified before this class existed):
 *
 * <ul>
 *   <li><b>Missing {@code data[i].index}.</b> Gemini's OpenAI-compatible layer serializes its
 *       response through a proto3-style JSON mapper that omits any field left at its default
 *       value. The per-embedding {@code index} field is an {@code int32} defaulting to
 *       {@code 0}, so a batch's very first embedding never carries an {@code index} key at
 *       all — confirmed by comparing a one-input request (whose single {@code data[0]} object
 *       has no {@code index}) against a two-input request (whose {@code data[1]} object does
 *       carry {@code "index": 1}). {@code Embedding.index()} treats the field as required.</li>
 *   <li><b>Missing top-level {@code usage}.</b> Gemini's response has no {@code usage} object
 *       at all (confirmed the same way: the raw response's top-level keys are exactly
 *       {@code object}, {@code data}, {@code model}). This is the same underlying gap
 *       spring-projects/spring-ai#2485 reported against Spring AI 1.x's old {@code OpenAiApi}
 *       client (fixed there in milestone 1.0.0-M7); it resurfaced in 2.0.x's rewrite onto the
 *       official OpenAI Java SDK, whose {@code CreateEmbeddingResponse.usage()} is required
 *       too. This class synthesizes a zero-valued {@code usage} object — Gemini's real token
 *       counts are simply not available on this response, and TRD §6.3's traces never surface
 *       token usage, only vector/metric data — matching the workaround the original issue's
 *       reporter already used (mocking the usage values).</li>
 * </ul>
 *
 * <p>Both required fields make the OpenAI Java SDK throw {@code OpenAIInvalidDataException}
 * ({@code index is not set} / {@code usage is not set}) before any Spring AI or application
 * code runs — exactly the shape a single-document precompute call always hits. This
 * interceptor rewrites only the {@code /embeddings} response body to fill in what Gemini
 * omitted, and leaves every other response, and every other request path, untouched. It is
 * registered on {@link OpenAiCompatibleEmbedder}'s HTTP client through Spring AI's documented
 * {@code httpClientBuilderCustomizer} extension point; it never touches the hand-written
 * metric or normalization code R-02 protects (TRD §3.3) — this only repairs the wire shape of
 * a third-party response before Spring AI's own SDK parses it.
 */
final class GeminiEmbeddingsCompatibilityInterceptor implements Interceptor {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (!response.isSuccessful() || !isEmbeddingsRequest(request)) {
            return response;
        }
        ResponseBody body = response.body();
        if (body == null) {
            return response;
        }

        String original = body.string();
        String patched = fillMissingRequiredFields(original);
        MediaType contentType = body.contentType();
        return response.newBuilder().body(ResponseBody.create(patched, contentType)).build();
    }

    private static boolean isEmbeddingsRequest(Request request) {
        return request.url().encodedPath().endsWith("/embeddings");
    }

    /**
     * Parses {@code json} as a tree and, if it has the OpenAI embeddings response shape
     * ({@code {"data": [...] }}), assigns each element of {@code data} missing an
     * {@code "index"} field its own array position, and fills in a zero-valued top-level
     * {@code "usage"} object if absent. Any other shape, or unparsable input, is returned
     * unchanged so the SDK reports whatever error is actually appropriate for it.
     */
    private static String fillMissingRequiredFields(String json) {
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(json);
        } catch (RuntimeException e) {
            return json;
        }
        if (!(root instanceof ObjectNode objectRoot) || !(objectRoot.get("data") instanceof ArrayNode data)) {
            return json;
        }

        boolean changed = false;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i) instanceof ObjectNode item && !item.has("index")) {
                item.put("index", i);
                changed = true;
            }
        }
        if (!objectRoot.has("usage")) {
            ObjectNode usage = JSON_MAPPER.createObjectNode();
            usage.put("prompt_tokens", 0);
            usage.put("total_tokens", 0);
            objectRoot.set("usage", usage);
            changed = true;
        }
        return changed ? JSON_MAPPER.writeValueAsString(root) : json;
    }
}
