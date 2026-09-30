package co.edu.uniquindio.legajo.application.error;

import java.util.List;

/**
 * Guards the identifier lists a request body may carry. A {@code null} or blank element is a
 * malformed request, never a lookup miss, so it is reported as a plain
 * {@link InvalidRequestException} (400 with no fixed problem type) before any id is resolved.
 */
public final class RequestIds {

    private RequestIds() {
    }

    /** Rejects a list holding a {@code null} or blank element; a {@code null} list is left to the caller. */
    public static void requireNoBlankElements(List<String> ids, String field) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new InvalidRequestException(field + " must not contain null or blank ids");
            }
        }
    }
}
