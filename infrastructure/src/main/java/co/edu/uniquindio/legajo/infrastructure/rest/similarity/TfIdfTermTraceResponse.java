package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.TfIdfTermTrace;

import java.util.Objects;

/** Wire shape of one term's TF-IDF trace row: frequencies, weights, and the normalized weights. */
public record TfIdfTermTraceResponse(
        String term,
        int frequencyA,
        int frequencyB,
        int documentFrequency,
        double tfA,
        double tfB,
        double idf,
        double rawWeightA,
        double rawWeightB,
        double normalizedWeightA,
        double normalizedWeightB) {

    public static TfIdfTermTraceResponse from(TfIdfTermTrace term) {
        Objects.requireNonNull(term, "term");
        return new TfIdfTermTraceResponse(
                term.term(), term.frequencyA(), term.frequencyB(), term.documentFrequency(), term.tfA(), term.tfB(),
                term.idf(), term.rawWeightA(), term.rawWeightB(), term.normalizedWeightA(), term.normalizedWeightB());
    }
}
