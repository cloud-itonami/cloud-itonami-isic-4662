# Contributing

`cloud-itonami-isic-4662` accepts contributions to the OSS blueprint, the
Metal Trading Governor, policy tests, documentation and operator model.

## Development
This vertical is self-contained: there is no `kotoba-lang/metaltrade`
capability library. The credit-clearance / contract-on-file / conflict-
minerals-provenance / sanctions-screening checks live directly in
`metaltrade.governor`.

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run    # demo driver
```

## Rules
- Do not commit real counterparty, credit, sanctions or conflict-minerals
  sourcing (mine-of-origin, smelter/refiner identity) data.
- Keep dispatch, provenance-verification and settlement behind the Metal
  Trading Governor.
- Treat metal-wholesale workflows as high-risk: add tests for provenance,
  jurisdiction, sanctions, double-actuation and audit logging.
- If you add or extend a jurisdiction in `metaltrade.facts/catalog` or
  `metaltrade.facts/conflict-minerals-basis`, cite a REAL official source --
  never fabricate a jurisdiction's or a statute's requirements.
- If you add a metal type to `metaltrade.facts/conflict-minerals-metals`,
  document the real-world reasoning (which regime treats it as a conflict
  mineral, and since when) in `docs/business-model.md`'s honest coverage
  section -- do not add a metal type "for completeness" without a citation.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
