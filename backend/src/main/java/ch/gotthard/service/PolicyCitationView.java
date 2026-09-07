package ch.gotthard.service;

import java.util.UUID;

/**
 * One policy chunk that was put in front of the model, with the similarity and rank that earned it
 * the place.
 *
 * <p>Every retrieved chunk is cited, whether or not the summary refers to it. The question a citation
 * list answers is "what was this written from", and dropping the clauses the model chose not to use
 * would turn an audit record into a highlight reel.
 */
public record PolicyCitationView(
        UUID chunkId, String document, String title, String section, String body, double similarity, int rank) {}
