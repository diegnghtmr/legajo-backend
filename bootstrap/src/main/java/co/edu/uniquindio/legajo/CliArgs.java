package co.edu.uniquindio.legajo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal {@code --key=value} / {@code --flag} argument parsing shared by the ingestion
 * CLI entry points ({@link IngestCli}, {@link VerifyCorpusCli}, {@link
 * ValidateCorpusCli}). A standalone flag (no {@code =}) is stored with value {@code
 * "true"}, which is all {@code --all} needs.
 */
final class CliArgs {

    private CliArgs() {
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int equalsIndex = body.indexOf('=');
            if (equalsIndex >= 0) {
                options.put(body.substring(0, equalsIndex), body.substring(equalsIndex + 1));
            } else {
                options.put(body, "true");
            }
        }
        return options;
    }
}
