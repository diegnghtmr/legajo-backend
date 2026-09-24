package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.clustering.LinkageStep;

/**
 * Wire shape of one linkage-matrix row, per the fixed linkage-matrix conventions:
 * {@code idx1 < idx2}, the merge distance, and the resulting cluster's size.
 */
public record LinkageStepResponse(int idx1, int idx2, double mergeDistance, int size) {

    public static LinkageStepResponse from(LinkageStep step) {
        return new LinkageStepResponse(step.idx1(), step.idx2(), step.mergeDistance(), step.size());
    }
}
