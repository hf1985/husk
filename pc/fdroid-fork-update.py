#!/usr/bin/env python3
"""Opdater F-Droid-forkens recipe og vent på pipelinen.

Spejler `fdroid/co.xplat.husk.yml` op i forken `hf16/f-droid` på branch
`co.xplat.husk` via GitLab commits-API'et. Forken klones bevidst ikke:
fdroiddata er for stor, og hele proceduren står i `docs/BUILD.md` afsnit 7.

Brug:
    export GL_TOKEN="$(bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt' \
        | grep -oE 'glpat-[A-Za-z0-9_.-]+' | head -1)"
    py -3.11 pc/fdroid-fork-update.py fdroid/co.xplat.husk.yml -m "commit-besked"

Med `--opret-mr <fil>` åbnes desuden en NY merge request mod upstream, når pipelinen er
grøn. Filens første linje er MR-titlen, resten er beskrivelsen. Det er nødvendigt fra og
med 1.1: !40810 (»New app«) blev MERGET 15-09-2026, og hverken en ny commit på forkens
gren eller en kommentar på den lukkede MR fører ændringen videre til upstream. En
opdatering kræver sin egen MR.

Exit 0 = pipelinen blev grøn (og MR'en oprettet, hvis der blev bedt om en).
Exit 1 = den fejlede. Exit 2 = brugsfejl.

⛔ `--opret-mr` er UDADVENDT og kan ikke kaldes tilbage, og gaten er nu MEKANISK:
`--adversarisk` er PÅKRÆVET sammen med `--opret-mr`, og en beskrivelse over
`mr-beskrivelse-loft` tegn afvises. Begge dele håndhæves i `pc/udadvendt.py`, som
`fdroid-mr-comment.py` deler. Ejerbeslutning 2026-09-19, verbatim: »Regel +
tegn-lofter + udvid gaten til al udadvendt tekst«.

Kør altså en frisk, adversarisk gennemgang over titel og beskrivelse OG det bevis
de hviler på, og skriv i `--adversarisk` hvad den så og hvad den fandt.

En kørsel UDEN `--opret-mr` skriver kun til vores egen fork og er ikke omfattet:
den spejler en recipe op og venter på pipelinen.

Tokenet læses KUN fra miljøet, aldrig fra argv: en hemmelighed i en
kommandolinje er offentlig, så længe processen kører (måleregel 185).
"""
import argparse
import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from udadvendt import Afvist, doem_adversarisk, doem_laengde, kvitter, laes_loft  # noqa: E402

PROJEKT = "hf16%2Ff-droid"
BRANCH = "co.xplat.husk"
STI = "metadata/co.xplat.husk.yml"
API = "https://gitlab.com/api/v4/projects/" + PROJEKT
# fdroid/fdroiddata har numerisk projekt-id 36528 (samme tal som fdroid-mr-comment.py).
UPSTREAM_ID = 36528
UPSTREAM_API = "https://gitlab.com/api/v4/projects/%d" % UPSTREAM_ID


def kald(token, url, data=None, metode=None):
    req = urllib.request.Request(url, method=metode)
    req.add_header("PRIVATE-TOKEN", token)
    krop = None
    if data is not None:
        krop = json.dumps(data).encode("utf-8")
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, krop, timeout=90) as r:
            raa = r.read().decode("utf-8")
            return json.loads(raa) if raa.strip() else {}
    except urllib.error.HTTPError as e:
        print("HTTP %s: %s" % (e.code, e.read().decode("utf-8", "replace")[:800]))
        raise


def main():
    p = argparse.ArgumentParser()
    p.add_argument("recipe", help="sti til fdroid/co.xplat.husk.yml")
    p.add_argument("-m", "--besked", required=True, help="commit-besked på forken")
    p.add_argument("--timeout", type=int, default=1500, help="sekunder at vente på pipelinen")
    p.add_argument("--opret-mr", metavar="FIL",
                   help="åbn en NY merge request mod upstream når pipelinen er grøn; "
                        "filens første linje er titlen, resten beskrivelsen")
    # PAAKRAEVET sammen med --opret-mr fra 2026-09-19. Se docstring og pc/udadvendt.py.
    p.add_argument("--adversarisk", metavar="TEKST", default="",
                   help="PAAKRAEVET ved --opret-mr: hvad gennemgangen saa, og hvad den fandt")
    a = p.parse_args()

    # Forudsætnings-tjek FØR der skrives noget: et --opret-mr der peger på en tom eller
    # manglende fil må ikke opdages EFTER commit og en kvarters pipeline, hvor det eneste
    # der er tilbage er at gøre det i hånden. DE UDADVENDTE VAGTER HØRER SAMME STED, og af
    # samme grund: en beskrivelse over loftet eller en manglende adversarisk kvittering må
    # ikke opdages når commiten allerede ligger på forken.
    titel = beskrivelse = note = None
    if a.opret_mr:
        tekst = io.open(a.opret_mr, encoding="utf-8").read().strip()
        if not tekst:
            print("--opret-mr: filen er tom", file=sys.stderr)
            return 2
        dele = tekst.split("\n", 1)
        titel = dele[0].strip()
        beskrivelse = dele[1].strip() if len(dele) > 1 else ""
        if not titel:
            print("--opret-mr: første linje (titlen) er tom", file=sys.stderr)
            return 2
        try:
            note = doem_adversarisk(a.adversarisk)
            loft = laes_loft("mr-beskrivelse-loft")
            n = doem_laengde(beskrivelse, loft, "MR-BESKRIVELSEN")
            # Filens FOERSTE linje er titlen, og den er lige saa udadvendt som kroppen.
            # Den var ugatet indtil 2026-09-20; se noten i fdroid-mr-comment.py.
            doem_laengde(titel, laes_loft("mr-titel-loft"), "MR-TITLEN")
        except Afvist as e:
            print(e, file=sys.stderr)
            return 2
        print("beskrivelse: %d tegn (loft %d)" % (n, loft))

    token = os.environ.get("GL_TOKEN", "").strip()
    if not token:
        print("GL_TOKEN mangler i miljøet - se docstring", file=sys.stderr)
        return 2

    indhold = io.open(a.recipe, encoding="utf-8").read()

    # IDEMPOTENS: er forkens gren allerede identisk, så commit IKKE igen. Uden den koster
    # hvert genforsøg - fx efter en fejl i MR-oprettelsen længere nede - en ny tom commit og
    # en ny ~2 minutters pipeline på F-Droids delte runnere. Målt 2026-09-19: to identiske
    # commits i træk, fordi kun det sidste trin fejlede.
    sha = None
    try:
        raa = kald_raa_fil(token, BRANCH)
        if raa == indhold:
            gren = kald(token, API + "/repository/branches/" + urllib.parse.quote(BRANCH, safe=""))
            sha = (gren.get("commit") or {}).get("id")
            print("forken har allerede dette indhold - springer commiten over:", sha)
    except urllib.error.HTTPError:
        sha = None   # filen findes ikke på grenen endnu; så commit vi som normalt

    if sha is None:
        res = kald(token, API + "/repository/commits", {
            "branch": BRANCH,
            "commit_message": a.besked,
            "actions": [{"action": "update", "file_path": STI, "content": indhold}],
        })
        sha = res.get("id")
        print("commit på forken:", sha)

    # Pipelinen SKAL pinnes til den sha vi lige skrev. Et nøgent
    # `?ref=<branch>&per_page=1` giver den NYESTE pipeline paa grenen, og maalt
    # 2026-09-06 er den nye pipeline foerst oprettet 11-14 sekunder efter commiten.
    # Er GitLab langsommere end ventetiden, er `pls[0]` altsaa den FORRIGE pipeline -
    # som typisk er groen - og scriptet ville melde en forældet succes som sin egen.
    pid = None
    for _ in range(30):
        pls = kald(token, API + "/pipelines?ref=" + BRANCH + "&sha=" + sha + "&per_page=1")
        if pls and pls[0].get("sha") == sha:
            pid = pls[0]["id"]
            break
        time.sleep(10)
    if not pid:
        print("INGEN PIPELINE for sha", sha)
        return 1
    print("pipeline: %d  https://gitlab.com/hf16/f-droid/-/pipelines/%d" % (pid, pid))

    frist = time.time() + a.timeout
    sidst = None
    while time.time() < frist:
        st = kald(token, API + "/pipelines/%d" % pid).get("status")
        if st != sidst:
            print("  status:", st, flush=True)
            sidst = st
        if st in ("success", "failed", "canceled", "skipped"):
            break
        time.sleep(20)

    print("SLUTSTATUS:", sidst)
    if sidst != "success":
        for j in kald(token, API + "/pipelines/%d/jobs" % pid):
            print("  job %s -> %s" % (j.get("name"), j.get("status")))
        return 1

    # Reproducerbarheds-dommen står i build-jobbets trace, ikke i pipeline-statussen.
    # Et grønt pipeline-svar alene siger intet om at APK'en matcher referencebinæren.
    for j in kald(token, API + "/pipelines/%d/jobs" % pid):
        if j.get("name") == "fdroid build":
            spor = kald_raa_trace(token, j["id"])
            for linje in spor.splitlines():
                if any(n in linje for n in (
                        "Successfully built", "compared built binary",
                        "allowed signer", "successfully verified")):
                    print("  " + linje.split("Z 01E ")[-1].strip())
            break

    if titel is None:
        return 0

    # En ALLEREDE åben MR fra samme gren må ikke blive til to. GitLab afviser dubletten
    # med 409, men fejlen ville stå som en rød kørsel frem for som den normale tilstand
    # den er, så vi spørger først.
    aabne = kald(token, UPSTREAM_API + "/merge_requests?state=opened&source_branch=" + BRANCH)
    if aabne:
        print("MR findes allerede - opretter ikke en ny:")
        for m in aabne:
            print("  !%s %s" % (m["iid"], m.get("web_url")))
        return 0

    # ⛔ MR'en oprettes paa KILDE-projektet (forken), ikke paa upstream. GitLab svarer 403
    # Forbidden paa upstreams eget /merge_requests naar man ikke er medlem af det projekt -
    # og 403 ligner et manglende token-scope frem for en forkert adresse. Maalt 2026-09-19.
    mr = kald(token, API + "/merge_requests", {
        "source_branch": BRANCH,
        "target_project_id": UPSTREAM_ID,
        "target_branch": "master",
        "title": titel,
        "description": beskrivelse,
        "remove_source_branch": False,
    })
    print("MR oprettet: !%s  %s" % (mr.get("iid"), mr.get("web_url")))
    kvitter(note)
    return 0


def kald_raa_fil(token, ref):
    """Filens RAA indhold paa en gren. Bruges kun til idempotens-tjekket ovenfor."""
    url = (API + "/repository/files/" + urllib.parse.quote(STI, safe="")
           + "/raw?ref=" + urllib.parse.quote(ref, safe=""))
    req = urllib.request.Request(url)
    req.add_header("PRIVATE-TOKEN", token)
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read().decode("utf-8")


def kald_raa_trace(token, job_id):
    req = urllib.request.Request(API + "/jobs/%d/trace" % job_id)
    req.add_header("PRIVATE-TOKEN", token)
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read().decode("utf-8", "replace")


if __name__ == "__main__":
    sys.exit(main())
