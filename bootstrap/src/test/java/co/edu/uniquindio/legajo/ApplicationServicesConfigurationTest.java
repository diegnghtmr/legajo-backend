package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.corpus.CorpusService;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingStatus;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingsService;
import co.edu.uniquindio.legajo.application.similarity.SimilarityService;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the four A2 application services are wired as beans (so a future REST controller,
 * A3/A4, can simply {@code @Autowired} them), and that {@code legajo.embedding-provider} —
 * the second dead property this feature makes live — genuinely flows from configuration
 * into an {@link EmbeddingStatus} response, not just into an unread {@link LegajoProperties}
 * field.
 */
@SpringBootTest
class ApplicationServicesConfigurationTest {

    @Autowired
    private CorpusService corpusService;
    @Autowired
    private SimilarityService similarityService;
    @Autowired
    private ClusteringService clusteringService;
    @Autowired
    private EmbeddingsService embeddingsService;

    @Test
    void allFourApplicationServicesAreRegisteredAsBeans() {
        assertThat(corpusService).isNotNull();
        assertThat(similarityService).isNotNull();
        assertThat(clusteringService).isNotNull();
        assertThat(embeddingsService).isNotNull();
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "legajo.embedding-provider=live")
    class WithLiveModeConfigured {

        @Autowired
        private LegajoProperties properties;
        @Autowired
        private EmbeddingsService embeddingsService;
        @Autowired
        @Qualifier("localEmbeddingRepository")
        private EmbeddingRepository localEmbeddingRepository;

        @Test
        void theConfiguredProviderModeFlowsIntoAnEmbeddingsStatusResponse() {
            EmbeddingStatus status =
                    embeddingsService.status(localEmbeddingRepository, "cpu", properties.embeddingProvider());

            assertThat(status.mode()).isEqualTo(EmbeddingProviderMode.LIVE);
        }
    }
}
