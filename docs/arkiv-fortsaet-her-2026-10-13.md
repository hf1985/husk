# Arkiveret fra FORTSÆT-HER.md 2026-10-13

Flyttet ordret for at holde FORTSÆT-HER.md under handoff-loftet.

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-27, `HFs-lenovo`)

Ad hoc-runde »token på spares«. Frisk `fable`-agent over `76a0439`, `362a1ca`, `1ed2db1` og `enheder.md`-diffen. Forrige rundes tabel (1.4-planen): `git show 1ed2db1:FORTSÆT-HER.md`.

| Linse | REFUTERET | Målt og gjort |
|---|---|---|
| Gate 1-3 | nej | kun docs; intet modul, ingen vendored kilde, intet template-bidrag |
| Gate 4 | delvist | F-Droid-bot-kapabiliteten stod kun i projektet; nu også i `infra/gitlab.md`. Memory `reference_husk_spare_wake_first` sagde »spares er tokenløse«; rettet kun i `~/.claude` (begge projektmapper); `.claude-k`, `.claude-dlm` og det kanoniske sæt får den ved næste memory-synk |
| Gate 7 | delvist | 14 forældede steder (fleet-doc, `BUILD.md`, `besluttede-opgaver.md` §2, `fdroid-fork-update.py`, denne fil); rettet |
| Gate 8 | nej | 0 tokens i diffen; `/info` 401 uden token på alle tre. Tokenet gik som `?token=` i lokal curl-argv under målingen, ikke via config-fil |
| Gate 9 | delvist | `pc/spare.sh shot` meldte en 401 som »skærmen sov«; rettet til `ERR HTTP <kode>` og målt (401, 200, 200). husk-viewer-docs sagde »INGEN token«; rettet |
| Gate 11 | delvist | `enheder.md` modsagde sig selv (»umålt«, »VALG«-afsnittet); rettet |
| Gate 9b (2 stemmer) | ja, begge | 503-årsagen og »lukket som overflødig/dublet« var stærkere end målingen; memory-påstanden for bred; `enheder.md`s gamle overskrift og A9-kabellinjen modsagde. Alt omformuleret. `spare.sh` grøn (401, curl-timeout, 200) |
| Egen måling | ja | min diagnose »skærmdeling slået fra« for A10e's `/screen.jpg` 503 holdt ikke: 200 efter `wake` (årsag: sovende skærm eller dovne producer, se flåde-afsnittet) |

**Trin 4 (`koer-tjek.sh --kun-nye`, baseline-filen, 81 kald: 47 målt, 34 sprunget):** NYE FUND 2, BAGGRUND 27, UKENDT 7.
NYE: `check-memory-spejl.ps1` var rundens (memory rettet i én home) og er grøn efter `sync-config-homes.ps1`; `check-relative-refs.sh` stod på synk-gaten (to deltag-repoer bagud) og er grøn efter scopet `sync-repos.sh`.
UKENDT: fire med `@tjek-korpus: ingen` og to uden feltet er baggrund pr. konstruktion; `check-trae-tilbagerulning.sh#1` målt særskilt: to filer i Kärnfull og dells paritetsmarkør, ingen fra runden. BAGGRUND: ti bash-tjek og tre PowerShell-tjek (de sidste scopet til `P_app_husk` og `-viewer`, rc=0) nævner ingen af rundens filer.

`P_kontor/docs/tailscale-migration.md:66` bytter `xperia-hfb` og `.102` i en tabel dateret 2026-05-24; lukket som historik, ikke rettet.

