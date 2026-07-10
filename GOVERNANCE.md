# Governance

`cloud-itonami-isic-4662` is an OSS open-business blueprint for wholesale
of metals and metal ores.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a metal-order with no official spec-basis can never be verified,
  dispatched, or invoiced against.
- the Metal Trading Governor remains independent of the advisor.
- hard policy violations (spec-basis fabrication, evidence-suppression,
  conflict-minerals-provenance suppression, forced dispatch/settlement)
  cannot be overridden by human approval.
- `:delivery/dispatch` and `:invoice/settle` never auto-commit at any phase.
- every dispatch, invoice, provenance verification and hold is auditable.
- counterparty, credit, sanctions and conflict-minerals sourcing data stays
  outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, conflict-minerals check scope, public business model,
operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing provenance-verification, dispatch or settlement policy checks
- dispatching 3TG/cobalt metal without a genuinely verified chain of
  custody and conflict-free-certified smelter/refiner
- mishandling counterparty or sourcing data
- misrepresenting certification status
- failing to respond to security incidents
