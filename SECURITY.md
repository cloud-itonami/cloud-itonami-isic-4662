# Security Policy

This project handles metal-wholesale trading, counterparty-diligence and
conflict-minerals provenance workflows. Treat vulnerabilities as potentially
high impact even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real counterparty, credit or sanctions data exposure
- authorization bypass
- Metal Trading Governor bypass
- conflict-minerals provenance (chain-of-custody / smelter-certification)
  falsification or suppression
- audit-ledger tampering
- over-disclosure in reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on trade data, provenance/policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real counterparty, credit, sanctions and conflict-minerals sourcing
  data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
