package ch.gotthard.ai.retrieval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * A deterministic, offline stand-in for a real text embedding model.
 *
 * <p>This is <strong>not</strong> a semantic embedding model, and it does not pretend to be one. A
 * production system would call a hosted model that has learned which words mean similar things; this
 * class knows nothing about meaning at all. It is a classic <a
 * href="https://en.wikipedia.org/wiki/Feature_hashing">feature-hashing</a> bag-of-words model — the
 * same family as scikit-learn's {@code HashingVectorizer} or Vowpal Wabbit's hashing trick — chosen
 * because it needs no training data, no corpus statistics, no model weights to ship and no network
 * call, while still placing documents that share vocabulary closer together than documents that do
 * not. That is genuinely sufficient here: the retrieval query is never operator free text, it is a
 * short, controlled phrase per fired rule code (see {@link RuleQueryVocabulary}), deliberately written
 * to echo the vocabulary of the one policy chunk it should retrieve — lexical overlap is exactly the
 * signal this model measures, because lexical overlap is exactly the signal the query was designed to
 * contain.
 *
 * <p><strong>How it works.</strong> Lower-case the text and tokenize on runs of letters and digits,
 * then take both single tokens and adjacent-pair bigrams, so a compound term like "near-threshold"
 * contributes the bigram {@code near_threshold} and not merely its two halves separately — the bigram
 * is far more specific to one document than either word alone. Each feature is hashed with {@link
 * String#hashCode()}, whose algorithm is fixed by the Java Language Specification — not merely
 * observed to be stable — so the same text produces the same vector on every run, in every JVM,
 * forever. The hash's low bits pick one of 384 dimensions and a higher bit picks a sign: accumulating
 * &plusmn;1 per occurrence rather than always +1 is the "signed" hashing trick, and it makes
 * collisions between unrelated features partly cancel rather than only ever inflate a dimension. The
 * accumulated vector is finally scaled to unit length, so cosine similarity between two embeddings
 * reduces to a plain dot product.
 *
 * <p><strong>Honest limitations.</strong> It has no notion of synonymy — "wire transfer" and "SEPA
 * payment" share no tokens and will not look related — no notion of negation, and hash collisions mean
 * two unrelated words can occasionally land in the same dimension. Token overlap is the entire signal.
 * None of that matters for grounding a deterministic rule engine's findings in a small, purpose-written
 * policy corpus with controlled vocabulary; it would matter a great deal for open-ended free text,
 * which is exactly why {@link KnowledgeRetriever} is never built from any.
 */
@Component
public final class HashingEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 384;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(final String text) {
        final double[] accumulator = new double[DIMENSIONS];
        features(tokenize(text)).forEach(feature -> accumulate(accumulator, feature));
        return normalize(accumulator);
    }

    private static List<String> tokenize(final String text) {
        final Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        final List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /** Unigrams plus adjacent-pair bigrams, so a compound term outweighs its two halves apart. */
    private static Stream<String> features(final List<String> tokens) {
        final Stream<String> unigrams = tokens.stream();
        final Stream<String> bigrams = IntStream.range(0, Math.max(0, tokens.size() - 1))
                .mapToObj(i -> tokens.get(i) + "_" + tokens.get(i + 1));
        return Stream.concat(unigrams, bigrams);
    }

    /** The signed hashing trick: {@link String#hashCode()} picks both a dimension and a sign. */
    private static void accumulate(final double[] vector, final String feature) {
        final int hash = feature.hashCode();
        final int dimension = Math.floorMod(hash, vector.length);
        final double sign = ((hash >>> 16) & 1) == 0 ? 1.0 : -1.0;
        vector[dimension] += sign;
    }

    /**
     * Unit length, so cosine similarity between two embeddings is a plain dot product. Text with no
     * recognised tokens (empty, or punctuation-only) has nothing to normalise and comes back as the
     * zero vector — callers must never write that to pgvector's cosine operator, which rejects a
     * zero-norm vector outright. Every string this project actually embeds (policy chunk bodies, {@link
     * RuleQueryVocabulary} phrases) is non-empty prose, so the case is real but never reached.
     */
    private static float[] normalize(final double[] accumulator) {
        final double normSquared = Arrays.stream(accumulator).map(v -> v * v).sum();
        final double norm = normSquared == 0.0 ? 1.0 : Math.sqrt(normSquared);
        final float[] normalized = new float[accumulator.length];
        for (int i = 0; i < accumulator.length; i++) {
            normalized[i] = (float) (accumulator[i] / norm);
        }
        return normalized;
    }
}
