# Agent 02 — JPA entities and repositories

**Model:** Claude Sonnet 5 · **Wave:** 1 · **Scope:** `backend/src/main/java/ch/gotthard/domain/**`

Sonnet because the schema is fully specified and joined-table inheritance is a
well-trodden pattern. This is careful boilerplate, not design.

## Instructions given

> Map `V1__baseline.sql` to JPA entities and Spring Data repositories in `domain/`.
> `transactions` plus its three subtype tables is a joined-table inheritance hierarchy
> discriminated on `activity_type`. Hibernate must validate against the migration, never
> generate schema. Entities are plain classes, not records — JPA needs a no-arg
> constructor and mutability. Repository tests use Testcontainers against
> `pgvector/pgvector:pg17`, never H2.
