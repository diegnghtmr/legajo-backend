package co.edu.uniquindio.legajo.similarity;

import java.util.Objects;

/**
 * The three vector-space representations RF2's clustering distance base can be built over
 * (TRD §6.4, "Representación y base de distancia (regla técnica fijada)"): {@code
 * tfidf-cosine} over the preprocessed abstracts (TRD §6.2/§6.3), {@code embedding-local}
 * (offline MiniLM precompute, TRD §6.3/§9), and {@code embedding-api} (OpenAI-compatible
 * precompute). All four linkage criteria of one clustering run share exactly one of these
 * three; {@code representation} never selects RF1's per-capability similarity metric — it
 * only selects which V (the set of L2-normalized vectors) the clustering distance
 * D = 1 − cos(V) is built over (TRD §6.4, "Representación frente a métrica de RF1
 * (desambiguación fijada)").
 *
 * <p><b>Enum, not a sealed interface.</b> Unlike {@link SimilarityAlgorithm} or the future
 * {@code LinkageCriterion} (TRD §6.4), where each permitted type carries its own distinct
 * behavior, the three representations here carry no per-variant behavior: this type is a
 * closed label set that only ever exposes a stable id string and a default, exactly the
 * shape {@link AlgorithmKind} already uses for the two similarity families. An enum is
 * therefore the better fit — a sealed interface would add three empty implementing classes
 * with nothing to differentiate them, and would not model "closed set of three labels" any
 * more precisely than {@code enum} already does.
 *
 * <p>{@link #id()} mirrors {@link SimilarityAlgorithm#id()}'s stable, hyphenated identifier
 * convention (e.g. {@code "tfidf-cosine"}), so a future REST/CLI parameter can round-trip
 * through the same string shape as an algorithm id; {@link #fromId(String)} is the reverse
 * lookup, mirroring {@link SimilarityAlgorithmRegistry#find(String)}'s id-based resolution.
 */
public enum Representation {

    TFIDF_COSINE("tfidf-cosine"),
    EMBEDDING_LOCAL("embedding-local"),
    EMBEDDING_API("embedding-api");

    /**
     * The default and documented run (TRD §6.4: "El valor por defecto y la ejecución
     * documentada es tfidf-cosine ... porque ejercita toda la cadena que pide el enunciado
     * del curso (preprocesado → similitud → agrupamiento → dendrograma) con componentes
     * implementados a mano").
     */
    public static final Representation DEFAULT = TFIDF_COSINE;

    private final String id;

    Representation(String id) {
        this.id = id;
    }

    /** Stable, hyphenated identifier (TRD §6.4), e.g. {@code "tfidf-cosine"}. */
    public String id() {
        return id;
    }

    /** Finds the representation whose {@link #id()} equals {@code id}, or throws if none matches. */
    public static Representation fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (Representation representation : values()) {
            if (representation.id.equals(id)) {
                return representation;
            }
        }
        throw new IllegalArgumentException("no representation with id: " + id);
    }
}
