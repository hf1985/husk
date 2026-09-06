#!/usr/bin/env python3
"""Post en kommentar på fdroiddata-MR'en fra en fil.

Brug:
    export GL_TOKEN="$(bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt' \
        | grep -oE 'glpat-[A-Za-z0-9_.-]+' | head -1)"
    py -3.11 pc/fdroid-mr-comment.py svar.md

⛔ TEKSTEN ER UDADVENDT OG KAN IKKE KALDES TILBAGE. Husets regel om at en
udadvendt MAIL først sendes efter en adversarisk gennemgang (`10_PROJEKTER/CLAUDE.md`,
håndhævet i `gmail.py --adversarisk`) er skrevet om mail og dækker derfor IKKE
denne kanal - men risikoen er den samme. Målt 2026-09-06: udkastet til svaret på
MR !40810 bar to falske påstande, og begge blev fundet af en adversarisk
gennemgang, ingen af dem af den der skrev teksten. Kør en frisk sub-agent over
teksten OG det bevis den hviler på, før du kalder dette script.

Tokenet læses KUN fra miljøet, aldrig fra argv (måleregel 185).
"""
import argparse
import io
import json
import os
import sys
import urllib.error
import urllib.request

# fdroid/fdroiddata har numerisk projekt-id 36528; MR'en for Husk er !40810.
URL = "https://gitlab.com/api/v4/projects/36528/merge_requests/{mr}/notes"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("tekstfil", help="fil med kommentarens markdown")
    p.add_argument("--mr", default="40810", help="MR-nummer (default 40810)")
    a = p.parse_args()

    token = os.environ.get("GL_TOKEN", "").strip()
    if not token:
        print("GL_TOKEN mangler i miljøet - se docstring", file=sys.stderr)
        return 2

    krop = io.open(a.tekstfil, encoding="utf-8").read()
    if not krop.strip():
        print("tekstfilen er tom - poster intet", file=sys.stderr)
        return 2

    req = urllib.request.Request(URL.format(mr=a.mr), method="POST")
    req.add_header("PRIVATE-TOKEN", token)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(
                req, json.dumps({"body": krop}).encode("utf-8"), timeout=90) as r:
            res = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print("HTTP %s: %s" % (e.code, e.read().decode("utf-8", "replace")[:800]))
        return 1

    print("note id  :", res.get("id"))
    print("forfatter:", (res.get("author") or {}).get("username"))
    print("oprettet :", res.get("created_at"))
    print("tegn     :", len(krop))
    return 0


if __name__ == "__main__":
    sys.exit(main())
