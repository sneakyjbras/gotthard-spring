package ch.gotthard.ai.analysis;

import ch.gotthard.ai.retrieval.RetrievedChunk;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything an {@link LlmClient} is allowed to know about one customer, in this layer's own
 * vocabulary.
 *
 * <p><b>Why this is not {@code CustomerRiskReport}.</b> {@code service/} sits above {@code ai/} in
 * the pyramid, so the port cannot take the use case's own record without inverting the dependency —
 * the adapters would then be unable to exist without the use case that calls them. Translating at
 * the boundary costs one mapping method in {@code AiAnalysisService} and buys a package that
 * compiles, and is tested, entirely on its own.
 *
 * <p>Three kinds of thing, in the order the prompt needs them: what the rules concluded, what the
 * customer did, and the policy that was retrieved for the codes that fired. Nothing else — in
 * particular no operator free text, which is the same discipline {@link
 * ch.gotthard.ai.retrieval.KnowledgeRetriever} imposes on retrieval and for the same reason.
 *
 * @param policy the chunks the retriever returned, ranked; every one of them is cited on the
 *     analysis afterwards, whether or not the model refers to it
 */
public record AnalysisRequest(
        AnalysedCustomer customer,
        OffsetDateTime windowFrom,
        OffsetDateTime windowTo,
        ComputedRisk computed,
        List<ChannelLine> activity,
        List<FiredRule> firedRules,
        List<RetrievedChunk> policy) {

    public AnalysisRequest {
        activity = List.copyOf(activity);
        firedRules = List.copyOf(firedRules);
        policy = List.copyOf(policy);
    }
}
