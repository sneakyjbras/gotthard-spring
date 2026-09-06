package ch.gotthard.ai.retrieval;

/**
 * Turns text into a fixed-length vector for similarity search — the port {@link
 * PgVectorKnowledgeRetriever} embeds both the policy corpus and each rule-driven query through, and
 * {@link PolicyCorpusEmbeddingInitializer} embeds the corpus with at startup.
 *
 * <p>The same bean must embed both sides of every comparison. Cosine similarity between a vector from
 * one model and a vector from another is meaningless even if both happen to be 384-dimensional;
 * swapping the implementation bound to this port means re-embedding the whole corpus, not just the
 * next query.
 *
 * <p>{@link HashingEmbeddingModel} is the only implementation this project ships, and it is a
 * deliberate stand-in for a real embedding model — see its Javadoc.
 */
public interface EmbeddingModel {

    /** The vector for {@code text}. Always {@link #dimensions()} long, never {@code null}. */
    float[] embed(String text);

    /** The length of every vector this model produces — {@code policy_chunks.embedding} is {@code vector(384)}. */
    int dimensions();
}
