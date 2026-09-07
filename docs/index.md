# gotthard-spring

Customer activity analytics for financial-services operators. An operator looks
up a customer, sees their card, payment and cryptocurrency activity, and can
ask for an AI-written assessment of the risk that activity carries.

Named for the Gotthard Pass — the way through the mountain.

## The one idea

**Rules decide risk. The model explains it.**

A deterministic rule engine — seven small, independently tested rules, each
bound to a weight held in the database — reads a customer's activity and
produces a score. That score, and only that score, decides the risk level. It
is the same for the same activity every time, and every rule that fires writes
an audit row.

The model is never asked to arrive at that number. It is shown what fired and
why, retrieves the policy language that speaks to those specific findings, and
writes the summary and recommendations an operator actually acts on — the job
language models are good at. Its own view of the risk level is recorded too,
in a separate column, precisely so that a disagreement between the two is
something an operator can see rather than something the system quietly
resolved on its behalf.

Everything else in this system — the pure-Java core, the window functions, the
retrieval grounded in fired rules rather than free text, the two levels stored
side by side — exists to keep that one sentence true under pressure.

## Where to go next

- **[Getting Started](getting-started.md)** — run it, log in, see what the
  seeded customers demonstrate.
- **[Architecture](architecture.md)** — the layers, the dependency rules that
  are enforced rather than suggested, and the path one analysis takes from a
  database row to a persisted verdict.
- **[Risk Model](risk-model.md)** — the seven rules, their weights, how a score
  becomes a band, and how the wallet graph feeds the one rule that isn't a
  window function.
- **[AI Methodology](ai-methodology.md)** and **[Demo](demo.md)** — how this
  repository was built, and how it's presented.
