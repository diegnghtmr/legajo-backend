package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkHarness;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * If the benchmark report files are missing or malformed, the server must fail to start:
 * {@link BenchmarksStartupValidator} must stop the Spring context from
 * starting when the wired {@link BenchmarkReportRepository} cannot load a report — the same
 * fail-closed contract {@link EmbeddingCacheStartupValidator} already applies to a
 * missing/malformed embedding cache. Uses an {@link ApplicationContextRunner} with a
 * hand-written fake repository rather than a real {@code CsvBenchmarkReportRepository} plus
 * fixture files — that CSV-level failure shape is already covered by {@code
 * CsvBenchmarkReportRepositoryTest}; this test only proves the boot-time wiring.
 */
class BenchmarksStartupValidatorTest {

    @Test
    void aRepositoryThatFailsToLoadStopsTheBootAndNamesTheExportCommand() {
        BenchmarkReportRepository failing = () -> {
            throw new IllegalStateException(
                    "benchmarks/results/jmh-results.csv is missing; re-run ./gradlew :benchmarks:jmh :benchmarks:jmhExport");
        };

        runner(failing).run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = rootCause(context.getStartupFailure());
            assertThat(rootCause).isInstanceOf(IllegalStateException.class);
            assertThat(rootCause.getMessage()).contains("jmhExport");
        });
    }

    @Test
    void aRepositoryThatLoadsSuccessfullyStartsCleanly() {
        BenchmarkReport report = new BenchmarkReport(
                new BenchmarkHarness("cpu-x", 4, 8_000_000_000L, "jdk-x", "os-x", "2026-01-01T00:00:00Z"),
                List.of(), List.of());

        runner(() -> report).run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner runner(BenchmarkReportRepository repository) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withBean(BenchmarkReportRepository.class, () -> repository);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration
    static class TestConfig {

        /** Delegates to the production factory, so this exercises DomainConfiguration's own
         * wiring of the validator, not a copy of it. */
        @Bean
        SmartInitializingSingleton benchmarksStartupValidator(BenchmarkReportRepository benchmarkReportRepository) {
            return new DomainConfiguration().benchmarksStartupValidator(benchmarkReportRepository);
        }
    }
}
