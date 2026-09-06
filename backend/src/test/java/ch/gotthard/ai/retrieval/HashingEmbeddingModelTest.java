package ch.gotthard.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void given_anyText_when_embed_then_returnsAVectorOfTheDeclaredDimensions() {
        float[] vector = model.embed("structuring near-threshold reporting");

        assertThat(vector).hasSize(HashingEmbeddingModel.DIMENSIONS);
        assertThat(model.dimensions()).isEqualTo(HashingEmbeddingModel.DIMENSIONS);
    }

    @Test
    void given_theSameText_when_embeddedByTwoFreshInstances_then_vectorsAreIdentical() {
        float[] first = new HashingEmbeddingModel().embed("dormancy then a burst of activity");
        float[] second = new HashingEmbeddingModel().embed("dormancy then a burst of activity");

        assertThat(first).containsExactly(second);
    }

    @Test
    void given_theSameText_when_embeddedRepeatedlyByOneInstance_then_everyCallAgrees() {
        String text = "quasi-cash concentration at gambling merchants";

        float[] first = model.embed(text);
        for (int i = 0; i < 5; i++) {
            assertThat(model.embed(text)).containsExactly(first);
        }
    }

    @Test
    void given_twoUnrelatedTexts_when_embedded_then_vectorsDiffer() {
        float[] structuring = model.embed(RuleQueryVocabulary.describe("R-01").orElseThrow());
        float[] dormancy = model.embed(RuleQueryVocabulary.describe("R-06").orElseThrow());

        assertThat(structuring).isNotEqualTo(dormancy);
    }

    @Test
    void given_aVector_when_embedded_then_itIsUnitLength() {
        float[] vector = model.embed("card-not-present decline cluster across merchants");

        double normSquared = 0.0;
        for (float component : vector) {
            normSquared += (double) component * component;
        }

        assertThat(Math.sqrt(normSquared)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void given_relatedAndUnrelatedText_when_comparedByCosineSimilarity_then_theRelatedPairScoresHigher() {
        String structuringQuery = RuleQueryVocabulary.describe("R-01").orElseThrow();
        String structuringPolicy = "Structuring and near-threshold reporting: three or more amounts placed just below"
                + " the reporting threshold within seven days, aggregating to it or more. Smurfing.";
        String unrelatedPolicy = "Card-not-present fraud and card testing: declined authorisations spread across"
                + " merchants, consistent with a stolen card number being validated.";

        double similarityToRelated = cosineSimilarity(model.embed(structuringQuery), model.embed(structuringPolicy));
        double similarityToUnrelated = cosineSimilarity(model.embed(structuringQuery), model.embed(unrelatedPolicy));

        assertThat(similarityToRelated).isGreaterThan(similarityToUnrelated);
    }

    @Test
    void given_textWithNoRecognisedTokens_when_embed_then_returnsTheZeroVectorWithoutThrowing() {
        assertThatCode(() -> model.embed("   ...   ---   ")).doesNotThrowAnyException();

        float[] vector = model.embed("!!!");

        assertThat(vector).containsOnly(0.0f);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }
}
