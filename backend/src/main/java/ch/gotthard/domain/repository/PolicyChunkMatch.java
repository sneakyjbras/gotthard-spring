package ch.gotthard.domain.repository;

import java.util.UUID;

/**
 * One row of a nearest-neighbour search over {@code policy_chunks}: a chunk's text and its cosine
 * similarity to the query that found it. See {@link PolicyChunkRepositoryCustom#findNearest}.
 */
public record PolicyChunkMatch(
        UUID chunkId, String document, String title, String section, String body, double similarity) {}
