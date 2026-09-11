(ns metaltrade.facts
  "Per-jurisdiction metal-wholesale customs / sanctions regulatory
  catalog -- the G2-style spec-basis table the Metal Trading Governor
  checks every `:provenance/verify` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's customs /
  sanctions requirements, or did it invent one?') -- PLUS a SEPARATE
  `conflict-minerals-basis` catalog, keyed the same way, that supplies
  the citation the advisor uses when drafting a conflict-minerals
  provenance checklist for a 3TG/cobalt order (see `metaltrade.governor`
  for why the conflict-minerals CHECK itself is not jurisdiction-gated
  even though its CITATION is).

  Each `catalog` entry below is a REAL jurisdiction with a REAL customs /
  sanctions regime: Japan's Ministry of Finance (MOF) Customs / METI
  jurisdiction over trade (関税法; 輸出貿易管理令), the US Customs and
  Border Protection (CBP) entry regime (Tariff Act of 1930) plus OFAC
  (Treasury) sanctions programs, the UK's post-Brexit customs regime
  (Taxation (Cross-border Trade) Act 2018) plus OFSI financial sanctions
  (Sanctions and Anti-Money Laundering Act 2018), and Germany's customs
  administration of EU law (Union Customs Code, Regulation (EU) No
  952/2013) representing the EU regime. Unlike the fuel-wholesale
  sibling's US entry (which cites the fuel excise tax, a fuel-specific
  levy), this catalog deliberately cites GENERAL customs/sanctions law
  only -- metals and metal ores carry no analogous commodity-specific
  excise the way fuel, alcohol or tobacco do.

  Argentina (ARG) is a DELIBERATE EXCEPTION to the 'general customs law
  only' pattern above. For Argentina, the most specifically verifiable
  and genuinely citable in-force mechanism actually aimed at THIS
  sector (metal / scrap-metal wholesale) is not a general customs code
  provision but a metals/recyclables-specific tax-agency regime:
  Resolución General (AFIP) N° 2849/2010, 'Registro de Comercializadores
  de Materiales a Reciclar' -- confirmed by direct fetch of its full
  text at https://servicios.infoleg.gob.ar/infolegInternet/anexos/165000-169999/168455/norma.htm
  (2026-07-23). Its Article 1 creates the 'Registro' and enumerates FIVE
  covered material classes, one of which is metal: 'a) PET. b) Papel y
  cartón. c) Vidrio. d) Plástico. e) Metales ferrosos o no ferrosos.'
  Article 2 requires registration by five operator categories
  (recicladores / acopiadores / galponeros / generadores de \"scrap\" /
  intermediarios). Beyond registration, it layers a genuinely useful
  provenance/traceability mechanism for exactly the metal-theft concern
  this catalog is meant to capture: Título III (Arts. 55-61) is a
  monthly seller-identifying information regime for purchase operations
  (seller name, CUIT/DNI, receipt number/date, description, kg,
  price -- Form 956), and Título IV Cap. II (Arts. 64-66, Anexo IX)
  mandates a special duplicate purchase receipt specifically for
  purchases made directly from individual informal collectors
  ('recolectores'). AFIP itself was dissolved and succeeded, effective
  2024-10-24, by the Agencia de Recaudación y Control Aduanero (ARCA)
  per Decreto 953/2024 Art. 1 ('Disuélvese la ADMINISTRACIÓN FEDERAL DE
  INGRESOS PÚBLICOS (AFIP)...') / Art. 2 ('Créase la AGENCIA DE
  RECAUDACIÓN Y CONTROL ADUANERO (ARCA)...'), confirmed by direct fetch
  of https://www.boletinoficial.gob.ar/detalleAviso/primera/316055/20241025
  (2026-07-23); ARCA continues to administer this same registry (the
  legacy 'servicioscf.afip.gob.ar/Publico/RG2849/Consulta.aspx' registry
  lookup page was still live under that domain as of the same date). The
  citation below therefore names AFIP as the resolution's own issuing
  authority (that is its actual, unaltered legal citation) while noting
  ARCA as the current administering successor -- this is NOT a
  hypothesis about an AML statute; it is this catalog's honest read of
  the actual in-force instrument, which is framed by AFIP/ARCA as a tax
  procedure / information-and-retention regime rather than as a
  freestanding anti-money-laundering law, even though it functions as
  Argentina's closest real analogue to a metal-trade provenance control.
  ARG has NO entry in `conflict-minerals-basis` below, for the same
  reason JPN and GBR (also present in `catalog`) have none: no
  Argentina-specific binding conflict-minerals statute was found, so it
  falls back to the OECD Guidance baseline like every other
  non-USA/DEU jurisdiction (see `conflict-minerals-citation`).

  `conflict-minerals-basis` is a DIFFERENT catalog because the real-world
  legal picture is different in kind: Dodd-Frank Section 1502 and EU
  Regulation 2017/821 do not bind the SELLER'S jurisdiction the way
  customs/excise law does -- they bind a DOWNSTREAM company (a US
  SEC-reporting issuer, an EU importer) that may sit anywhere in the
  chain. Only two of the four seeded jurisdictions (USA, DEU/EU) have a
  binding statute in this catalog; the others cite the OECD Due
  Diligence Guidance for Responsible Supply Chains of Minerals from
  Conflict-Affected and High-Risk Areas as the operational baseline this
  actor holds itself to regardless -- see `metaltrade.governor`'s
  `conflict-minerals-provenance-unverified-violations` docstring for why
  the CHECK still applies unconditionally across every jurisdiction.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  `catalog` has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the counterparty-
  diligence evidence set (credit-clearance record, contract/PO,
  sanctions-screening record) evaluated by `evidence-incomplete-
  violations`; `:legal-basis` / `:owner-authority` / `:provenance` are
  the G2 citation the governor requires before any `:provenance/verify`
  proposal can commit. This is the GENERAL trade-jurisdiction catalog --
  see `conflict-minerals-basis` below for the separate, metal-type-keyed
  conflict-minerals citation."
  {"JPN" {:name "JPN"
          :owner-authority "財務省 (MOF) 関税局 / 経済産業省 (METI)"
          :legal-basis "関税法 (Customs Act); 輸出貿易管理令 (Foreign Exchange and Foreign Trade Control Act / Export Trade Control Order)"
          :provenance "https://www.customs.go.jp/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "USA" {:name "USA"
          :owner-authority "U.S. Customs and Border Protection (CBP, DHS) / OFAC (U.S. Treasury)"
          :legal-basis "Tariff Act of 1930 (19 U.S.C. Chapter 4), customs entry requirements; OFAC sanctions programs"
          :provenance "https://www.cbp.gov/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "GBR" {:name "GBR"
          :owner-authority "HM Revenue & Customs (HMRC) / Office of Financial Sanctions Implementation (OFSI)"
          :legal-basis "Taxation (Cross-border Trade) Act 2018; Sanctions and Anti-Money Laundering Act 2018 (SAMLA 2018)"
          :provenance "https://www.gov.uk/government/organisations/hm-revenue-customs"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "DEU" {:name "DEU"
          :owner-authority "Generalzolldirektion (German Customs) under the Bundesministerium der Finanzen (BMF)"
          :legal-basis "Union Customs Code (Regulation (EU) No 952/2013); EU financial sanctions regulations"
          :provenance "https://www.zoll.de/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "ARG" {:name "ARG"
          :owner-authority "Administración Federal de Ingresos Públicos (AFIP) -- disuelta el 24/10/2024 y sucedida por la Agencia de Recaudación y Control Aduanero (ARCA), ambas entidades autárquicas del Ministerio de Economía (Decreto 953/2024, Arts. 1-2)"
          :legal-basis "Resolución General (AFIP) N° 2849/2010: crea el \"Registro de Comercializadores de Materiales a Reciclar\" (Art. 1, inc. e: \"Metales ferrosos o no ferrosos\"; registration required per Art. 2 for recicladores/acopiadores/galponeros/generadores de \"scrap\"/intermediarios); régimen mensual de información de operaciones de compra por vendedor (Título III, Arts. 55-61, Formulario 956); comprobante especial de compra a recolectores individuales (Título IV, Cap. II, Arts. 64-66, Anexo IX)"
          :provenance "https://servicios.infoleg.gob.ar/infolegInternet/anexos/165000-169999/168455/norma.htm"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}})

(def conflict-minerals-metals
  "Metal types this actor treats as CONFLICT MINERALS, gating
  `metaltrade.governor`'s `conflict-minerals-provenance-unverified-
  violations` check -- see that function's docstring for why this set
  is 3TG plus cobalt, and why the check is a NO-OP for every other metal
  type (copper, iron ore, aluminum, nickel, zinc, lead, ...)."
  #{"tin" "tantalum" "tungsten" "gold" "cobalt"})

(defn conflict-minerals-metal? [metal-type]
  (boolean (contains? conflict-minerals-metals metal-type)))

(def conflict-minerals-basis
  "iso3 -> conflict-minerals-specific citation, DISTINCT from `catalog`
  above (see namespace docstring for why). Only USA and DEU (standing in
  for the EU regime) have a binding statute in this seed; every other
  jurisdiction -- including ones present in `catalog` -- falls back to
  the OECD Guidance as an operational, non-statutory baseline. This map
  is read by `metaltrade.fueltradeadvisor`-equivalent
  (`metaltrade.metaltradeadvisor`) to draft the conflict-minerals
  citation shown to a human reviewer; it does NOT gate whether the
  `conflict-minerals-provenance-unverified` governor check applies (that
  check is unconditional on metal-type alone, see `metaltrade.governor`)."
  {"USA" {:owner-authority "U.S. Securities and Exchange Commission (SEC)"
          :legal-basis "Dodd-Frank Wall Street Reform and Consumer Protection Act, Section 1502 (15 U.S.C. §78m note); SEC Rule 13p-1 (Conflict Minerals)"
          :provenance "https://www.sec.gov/rules-regulations/other-commission-orders-rules-notices/conflict-minerals"
          :binding? true}
   "DEU" {:owner-authority "European Commission; German national competent authority: Bundesamt für Wirtschaft und Ausfuhrkontrolle (BAFA)"
          :legal-basis "Regulation (EU) 2017/821 laying down supply chain due diligence obligations for Union importers of tin, tantalum, tungsten, their ores, and gold originating from conflict-affected and high-risk areas"
          :provenance "https://eur-lex.europa.eu/eli/reg/2017/821/oj"
          :binding? true}})

(def oecd-guidance
  "The de facto international operational framework both Dodd-Frank
  1502 and EU 2017/821 point to, used as the citation for every
  jurisdiction with NO binding statute of its own in
  `conflict-minerals-basis` above. This is NOT itself binding law in
  those jurisdictions -- it is cited honestly as the operational
  baseline this actor applies globally regardless (see
  `metaltrade.governor`'s `conflict-minerals-provenance-unverified-
  violations` docstring)."
  {:owner-authority "OECD"
   :legal-basis "OECD Due Diligence Guidance for Responsible Supply Chains of Minerals from Conflict-Affected and High-Risk Areas (3rd edition, 2016)"
   :provenance "https://www.oecd.org/en/publications/oecd-due-diligence-guidance-for-responsible-supply-chains-of-minerals-from-conflict-affected-and-high-risk-areas_9789264252479-en.html"
   :binding? false})

(defn conflict-minerals-citation
  "The citation the advisor should draft for `iso3`'s conflict-minerals
  provenance checklist: the jurisdiction's own binding statute if one is
  seeded, else the OECD Guidance baseline. Never nil -- every
  jurisdiction has AT LEAST the OECD baseline, because this actor treats
  chain-of-custody/conflict-free-smelter diligence as a universal
  operational floor for 3TG/cobalt, not merely a where-legally-mandated
  courtesy."
  [iso3]
  (or (get conflict-minerals-basis iso3) oecd-guidance))

(defn spec-basis
  "The jurisdiction's GENERAL trade requirement map, or nil -- nil means
  NO spec-basis, and the governor must hold any proposal that tries to
  verify provenance, dispatch metal, or settle an invoice on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4662 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `metaltrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every GENERAL evidence item listed for `iso3`? Missing spec-basis ->
  never satisfied. Deliberately does NOT include conflict-minerals
  chain-of-custody / smelter-certification evidence -- that is a
  separate, metal-type-gated governor check, not part of the generic
  per-jurisdiction evidence checklist (see `metaltrade.governor`)."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
