package ch.gotthard.ai.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits one policy document's Markdown source into retrievable chunks, one per numbered {@code ##}
 * section, preserving the document id and the section number every chunk needs for citation.
 *
 * <p>Pure and stateless on purpose: no I/O, no Spring, trivial to unit test directly. It is not wired
 * into the running application — {@code docs/policy/*.md} is chunked once, applying this exact
 * convention, when authoring {@code V3__seed_policy_corpus.sql} (see that migration's header comment
 * for why seeding is SQL rather than a runtime classpath scan). What this class buys instead is an
 * executable, tested specification of the chunking convention: a document only has to follow the same
 * {@code # DOC-ID — Title} / {@code ## N. Section} shape this class already parses, and {@link
 * MarkdownPolicyChunkerTest} proves that shape parses the way the corpus assumes it does.
 *
 * <p>The expected shape:
 *
 * <pre>{@code
 * # AML-001 — Structuring and Near-Threshold Reporting Behaviour
 *
 * (front matter before the first numbered section — a disclaimer, a status line — is not policy
 * substance and is never turned into a chunk)
 *
 * ## 1. Purpose and scope
 * ...section body...
 *
 * ## 2. Definitions
 * ...section body...
 * }</pre>
 */
public final class MarkdownPolicyChunker {

    private static final Pattern DOCUMENT_HEADING = Pattern.compile("^#\\s+([A-Z]+-\\d+)\\s+—\\s+(.+?)\\s*$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^##\\s+(\\d+(?:\\.\\d+)*)\\.\\s+(.+?)\\s*$");

    private MarkdownPolicyChunker() {}

    /**
     * One chunk per {@code ##} section in {@code markdown}, in document order. Throws if the document
     * has no {@code # DOC-ID — Title} heading. A document with no numbered sections at all yields an
     * empty list rather than one large chunk — an unsectioned document is not a shape this project's
     * corpus ever intends to seed.
     */
    public static List<PolicyChunkDraft> chunk(final String markdown) {
        final List<String> lines = markdown.lines().toList();
        final String documentId = documentIdOf(lines);
        return chunkSections(documentId, lines);
    }

    private static String documentIdOf(final List<String> lines) {
        return lines.stream()
                .map(DOCUMENT_HEADING::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no '# DOC-ID — Title' heading found"));
    }

    /**
     * A line-by-line scan accumulating the current section's body until the next heading appears —
     * inherently stateful, so a loop reads more honestly here than forcing a stream over what is
     * unavoidably a sequential fold.
     */
    private static List<PolicyChunkDraft> chunkSections(final String documentId, final List<String> lines) {
        final List<PolicyChunkDraft> chunks = new ArrayList<>();
        String section = null;
        String title = null;
        StringBuilder body = new StringBuilder();
        for (final String line : lines) {
            final Matcher heading = SECTION_HEADING.matcher(line);
            if (heading.matches()) {
                addIfPresent(chunks, documentId, section, title, body);
                section = heading.group(1);
                title = heading.group(2);
                body = new StringBuilder();
            } else if (section != null) {
                body.append(line).append('\n');
            }
        }
        addIfPresent(chunks, documentId, section, title, body);
        return List.copyOf(chunks);
    }

    private static void addIfPresent(
            final List<PolicyChunkDraft> chunks,
            final String documentId,
            final String section,
            final String title,
            final StringBuilder body) {
        if (section != null) {
            chunks.add(new PolicyChunkDraft(
                    documentId, title, section, body.toString().strip()));
        }
    }
}
