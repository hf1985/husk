#!/usr/bin/env python3
"""Opdater F-Droid-forkens recipe og vent på pipelinen.

Spejler `fdroid/co.xplat.husk.yml` op i forken `hf16/f-droid` på branch
`co.xplat.husk` via GitLab commits-API'et. Forken klones bevidst ikke:
fdroiddata er for stor, og hele proceduren står i `docs/BUILD.md` afsnit 7.

Brug:
    export GL_TOKEN="$(bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt' \
        | grep -oE 'glpat-[A-Za-z0-9_.-]+' | head -1)"
    py -3.11 pc/fdroid-fork-update.py fdroid/co.xplat.husk.yml -m "commit-besked"

Exit 0 = pipelinen blev grøn. Exit 1 = den fejlede. Exit 2 = brugsfejl.

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
import urllib.request

PROJEKT = "hf16%2Ff-droid"
BRANCH = "co.xplat.husk"
STI = "metadata/co.xplat.husk.yml"
API = "https://gitlab.com/api/v4/projects/" + PROJEKT


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
    a = p.parse_args()

    token = os.environ.get("GL_TOKEN", "").strip()
    if not token:
        print("GL_TOKEN mangler i miljøet - se docstring", file=sys.stderr)
        return 2

    indhold = io.open(a.recipe, encoding="utf-8").read()
    res = kald(token, API + "/repository/commits", {
        "branch": BRANCH,
        "commit_message": a.besked,
        "actions": [{"action": "update", "file_path": STI, "content": indhold}],
    })
    print("commit på forken:", res.get("id"))

    # Pipelinen SKAL pinnes til den sha vi lige skrev. Et nøgent
    # `?ref=<branch>&per_page=1` giver den NYESTE pipeline paa grenen, og maalt
    # 2026-09-06 er den nye pipeline foerst oprettet 11-14 sekunder efter commiten.
    # Er GitLab langsommere end ventetiden, er `pls[0]` altsaa den FORRIGE pipeline -
    # som typisk er groen - og scriptet ville melde en forældet succes som sin egen.
    sha = res.get("id")
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
    return 0


def kald_raa_trace(token, job_id):
    req = urllib.request.Request(API + "/jobs/%d/trace" % job_id)
    req.add_header("PRIVATE-TOKEN", token)
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read().decode("utf-8", "replace")


if __name__ == "__main__":
    sys.exit(main())
