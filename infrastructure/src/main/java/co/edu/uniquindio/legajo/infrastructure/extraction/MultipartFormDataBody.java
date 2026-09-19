package co.edu.uniquindio.legajo.infrastructure.extraction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds a single-file {@code multipart/form-data} request body. {@code
 * java.net.http.HttpClient} has no built-in multipart support, and pulling in an HTTP
 * client library only to POST one file to GROBID would be a heavier dependency than
 * this ~20-line encoder; the wire format itself (RFC 7578) is fixed and not something
 * this class has any latitude to get "wrong" in an interesting way.
 */
final class MultipartFormDataBody {

    private MultipartFormDataBody() {
    }

    static byte[] build(String boundary, String fieldName, String fileName, byte[] fileBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/pdf\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException in practice; this keeps
            // the method signature clean for callers instead of forcing a throws clause.
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
