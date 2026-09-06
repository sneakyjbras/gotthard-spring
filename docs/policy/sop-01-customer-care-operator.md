# SOP-01 — Customer-Care Operator Standard Operating Procedure

> **Exercise disclaimer.** This document was written for the gotthard-spring take-home
> exercise. It is not the policy of any real financial institution and must never be
> treated as an actual operating procedure outside this project.

**Status:** Internal draft · **Effective:** 2026-01-01 · **Owner:** Customer Care Operations (exercise)

## 1. Purpose and scope

This document is the single reference for what a customer-care operator using the
console may do when a customer's activity is flagged, at any risk level and for any of
the patterns described elsewhere in this corpus. Where a topic-specific document
(`AML-001` through `CRYPTO-02`) gives pattern-specific guidance, it points back here for
what an operator is actually authorised to do; this document is where that authority is
defined once.

## 2. Roles

The console recognises two operator roles. An **operator** handles day-to-day review:
reading activity, requesting documents, placing a temporary hold, and escalating. A
**supervisor** holds everything an operator holds, plus the authority to freeze an
account fully, close or dismiss an escalated case, and lift a hold or a freeze. A
finding never requires action beyond an operator's own authority to be looked at — only
certain actions require a supervisor.

## 3. What an operator may do without escalation

- **Request documents.** Ask the customer for supporting evidence — proof of funds,
  an invoice, a contract, identification — through the standard document request flow.
- **Ask clarifying questions.** Contact the customer through a verified channel to ask
  about a specific transaction or pattern.
- **Review.** Read the customer's full activity history and any AI-generated analysis
  in the console; reviewing is never itself an action that needs authorisation.
- **Place a temporary hold on a single transaction.** Pause one pending transaction
  while documentation is gathered. This is not the same as freezing the account, and
  does not require supervisor sign-off.
- **Note the case file.** Record findings, correspondence and reasoning against the
  customer's record for the audit trail.

## 4. What requires supervisor sign-off

- **Freezing an account.** Stopping all activity on an account, as opposed to holding
  one transaction, is a supervisor decision.
- **Unfreezing or lifting a hold.** Only a supervisor reverses either action, even the
  operator who placed it.
- **Dismissing a HIGH or CRITICAL finding.** An operator may close a LOW or MEDIUM
  finding with a documented, credible explanation; HIGH and CRITICAL findings are closed
  by a supervisor only.
- **Filing an Internal Escalation Report.** An operator prepares the report; a
  supervisor reviews and files it (§6).

## 5. Escalation triggers by risk level

| Level | Operator action | Timing |
|---|---|---|
| LOW | Note the file. No customer contact required unless curious. | — |
| MEDIUM | Review within two business days; may request documents. | 2 business days |
| HIGH | Escalate to a supervisor; consider a temporary hold. | Same business day |
| CRITICAL | Escalate to a supervisor immediately; hold pending review. | Immediately |

## 6. Filing an Internal Escalation Report

Where a topic document directs it, or where the console's computed level and the AI
assistant's own assessed level diverge, prepare an **Internal Escalation Report (IER)**
— this corpus's fictional stand-in for the kind of internal suspicion report a real
compliance function would file. An IER records: the customer reference, the activity
window reviewed, the rule codes that fired, the AI assistant's summary, and the
operator's own notes and any customer response obtained. A supervisor reviews and files
it; an operator never files one unreviewed.

**Never tell a customer they are the subject of an escalation or a report.** Disclosing
that fact is called tipping off, and it can destroy the value of a review in progress
even when the underlying activity turns out to be entirely legitimate. Document requests
and clarifying questions are always framed as routine.
