package ch.gotthard.ai.retrieval;

import ch.gotthard.core.model.Require;

/**
 * One retrievable section of a policy document, chunked but not yet embedded or persisted — the
 * output of {@link MarkdownPolicyChunker}.
 *
 * <p>{@code document} is normalised to upper case via {@link Require#code}, the same treatment
 * {@code risk_rules.rule_code} gets, since both are identifiers a human types and a database compares
 * case-insensitively in spirit.
 */
public record PolicyChunkDraft(String document, String title, String section, String body) {

    public PolicyChunkDraft {
        document = Require.code(document, "document");
        title = Require.text(title, "title");
        section = Require.text(section, "section");
        body = Require.text(body, "body");
    }
}
