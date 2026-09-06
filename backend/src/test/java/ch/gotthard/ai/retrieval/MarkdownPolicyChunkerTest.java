package ch.gotthard.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownPolicyChunkerTest {

    /** The exact shape the real corpus uses — see docs/policy/aml-001-*.md §1 and §2. */
    private static final String SAMPLE_DOCUMENT =
            """
            # AML-001 — Structuring and Near-Threshold Reporting Behaviour

            > **Exercise disclaimer.** Written for the gotthard-spring take-home exercise.

            **Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Financial Crime Compliance (exercise)

            ## 1. Purpose and scope

            This document tells an operator what structuring looks like in customer activity.

            ## 2. Definitions

            **Structuring** is breaking one transaction into several smaller ones.
            **Near-threshold** describes an amount placed just under the reporting line.
            """;

    @Test
    void given_aDocumentWithTwoSections_when_chunk_then_returnsTwoChunksPreservingDocumentAndSectionOrder() {
        List<PolicyChunkDraft> chunks = MarkdownPolicyChunker.chunk(SAMPLE_DOCUMENT);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).document()).isEqualTo("AML-001");
        assertThat(chunks.get(0).section()).isEqualTo("1");
        assertThat(chunks.get(0).title()).isEqualTo("Purpose and scope");
        assertThat(chunks.get(0).body())
                .isEqualTo("This document tells an operator what structuring looks like in customer activity.");
        assertThat(chunks.get(1).document()).isEqualTo("AML-001");
        assertThat(chunks.get(1).section()).isEqualTo("2");
        assertThat(chunks.get(1).title()).isEqualTo("Definitions");
        assertThat(chunks.get(1).body()).contains("Structuring").contains("Near-threshold");
    }

    @Test
    void given_frontMatterBeforeTheFirstSection_when_chunk_then_theDisclaimerAndStatusLineAreNotChunked() {
        List<PolicyChunkDraft> chunks = MarkdownPolicyChunker.chunk(SAMPLE_DOCUMENT);

        assertThat(chunks).noneMatch(chunk -> chunk.body().contains("Exercise disclaimer"));
        assertThat(chunks).noneMatch(chunk -> chunk.body().contains("Internal draft"));
    }

    @Test
    void given_aDocumentWithNoHeading_when_chunk_then_throws() {
        assertThatThrownBy(() -> MarkdownPolicyChunker.chunk("## 1. Purpose\n\nBody text.\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOC-ID");
    }

    @Test
    void given_aDocumentWithNoNumberedSections_when_chunk_then_returnsNoChunks() {
        List<PolicyChunkDraft> chunks =
                MarkdownPolicyChunker.chunk("# AML-999 — Untitled\n\nJust a preamble, no sections.\n");

        assertThat(chunks).isEmpty();
    }

    @Test
    void given_aMultiLevelSectionNumber_when_chunk_then_theWholeNumberIsPreserved() {
        String markdown =
                """
                # GOV-01 — Program Governance

                ## 3.2. A subsection

                Body text for the subsection.
                """;

        List<PolicyChunkDraft> chunks = MarkdownPolicyChunker.chunk(markdown);

        assertThat(chunks).singleElement().satisfies(chunk -> assertThat(chunk.section())
                .isEqualTo("3.2"));
    }
}
