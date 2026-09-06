package ch.gotthard.ai.retrieval;

import ch.gotthard.domain.model.PolicyChunk;
import ch.gotthard.domain.repository.PolicyChunkRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Embeds the policy corpus at application startup, once.
 *
 * <p>{@code V3__seed_policy_corpus.sql} seeds {@code policy_chunks} with document text only — {@code
 * document}, {@code title}, {@code section}, {@code body} — and leaves {@code embedding} {@code NULL}.
 * A migration is the wrong place for the vectors themselves: a chunk's embedding is a derived artefact
 * of its text <em>and</em> the embedding algorithm, not a fact about the world, and writing several
 * dozen chunks' worth of 384-dimensional literals into a SQL file would both bloat the migration and
 * let the vectors silently drift out of step with whatever {@link EmbeddingModel} produced them if the
 * model ever changed. Computing them here instead — from the very same {@link EmbeddingModel} bean
 * {@link PgVectorKnowledgeRetriever} embeds queries with — means the corpus and every query always
 * speak the same vector space by construction, and re-embedding after a model change is a restart, not
 * a migration.
 *
 * <p>Idempotent and cheap: only chunks with a {@code NULL} embedding are touched, so a warm database
 * does nothing on every subsequent startup, and a corpus of a few dozen short chunks costs low
 * milliseconds to hash-embed even on a cold one.
 */
@Component
public class PolicyCorpusEmbeddingInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyCorpusEmbeddingInitializer.class);

    private final PolicyChunkRepository policyChunks;
    private final EmbeddingModel embeddingModel;

    public PolicyCorpusEmbeddingInitializer(PolicyChunkRepository policyChunks, EmbeddingModel embeddingModel) {
        this.policyChunks = policyChunks;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<PolicyChunk> unembedded = policyChunks.findByEmbeddingIsNull();
        unembedded.forEach(this::embedAndSave);
        if (!unembedded.isEmpty()) {
            log.info(
                    "Embedded {} policy chunk(s) with {}",
                    unembedded.size(),
                    embeddingModel.getClass().getSimpleName());
        }
    }

    private void embedAndSave(PolicyChunk chunk) {
        chunk.setEmbedding(embeddingModel.embed(chunk.getBody()));
        policyChunks.save(chunk);
    }
}
