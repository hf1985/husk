#!/usr/bin/env python3
"""Post en kommentar på fdroiddata-MR'en fra en fil, eller erstat dens beskrivelse.

Brug:
    export GL_TOKEN="$(bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt' \
        | grep -oE 'glpat-[A-Za-z0-9_.-]+' | head -1)"
    py -3.11 pc/fdroid-mr-comment.py svar.md --mr 49350 \
        --adversarisk "hvad blev gennemgået, og hvad fandt den"
    py -3.11 pc/fdroid-mr-comment.py beskrivelse.md --mr 49350 --beskrivelse \
        --titel "Update Husk to ..." --adversarisk "..."

⛔ `--beskrivelse` ligger HER og ikke i et script ved siden af (ejerbeslutning
2026-09-18: »løs et hul som et UDSNIT af hovedværktøjet, aldrig som et script ved
siden af«). En MR-beskrivelse er lige så udadvendt som en kommentar, og et
hjælpescript uden om værktøjet er en tredje kanal ingen vagt kender. Den eneste
forskel på de to tilstande er hvilket loft der gælder, og hvilket endpoint der
kaldes - gaten er den samme.

⛔ TEKSTEN ER UDADVENDT OG KAN IKKE KALDES TILBAGE, og gaten er nu MEKANISK.
`--adversarisk` er PÅKRÆVET, og en kommentar over `mr-kommentar-loft` tegn afvises.
Begge dele håndhæves i `pc/udadvendt.py`, som `fdroid-fork-update.py` deler.

Ejerbeslutning 2026-09-19, verbatim: »Regel + tegn-lofter + udvid gaten til al
udadvendt tekst«. Her stod indtil da at husets adversariske regel var skrevet om
MAIL og derfor ikke dækkede denne kanal. **Det er nu falsk** - reglen dækker al
udadvendt tekst, og dette værktøj håndhæver den.

De to målinger bag: 2026-09-06 bar udkastet til svaret på MR !40810 to falske
påstande, som begge blev fundet af en adversarisk gennemgang og ingen af dem af
den der skrev teksten. 2026-09-19 skrev F-Droid-maintaineren linsui om MR !49350:
»Don't write so long description. We can't read it.« Beskrivelsen var 6.193 tegn.

Tokenet læses KUN fra miljøet, aldrig fra argv (måleregel 185).
"""
import argparse
import io
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from udadvendt import Afvist, doem_adversarisk, doem_laengde, kvitter, laes_loft  # noqa: E402

# fdroid/fdroiddata har numerisk projekt-id 36528; MR'en for Husk er !40810.
URL = "https://gitlab.com/api/v4/projects/36528/merge_requests/{mr}/notes"
URL_MR = "https://gitlab.com/api/v4/projects/36528/merge_requests/{mr}"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("tekstfil", help="fil med kommentarens markdown")
    # --mr er PAAKRAEVET fra 2026-09-19. Defaulten var 40810, og den MR blev MERGET
    # 15-09-2026: et glemt flag ville have postet 1.1-kommentaren i en lukket traad,
    # hvor ingen anmelder laeser den, og kaldet ville have svaret 201 hele vejen.
    # En default der peger paa en lukket traad er en tavs fejl-adresse, ikke en bekvemmelighed.
    p.add_argument("--mr", required=True, help="MR-nummer (paakraevet; 40810 er MERGET)")
    # PAAKRAEVET fra 2026-09-19. Se docstring og pc/udadvendt.py.
    p.add_argument("--adversarisk", metavar="TEKST", default="",
                   help="PAAKRAEVET: hvad den adversariske gennemgang saa, og hvad den fandt")
    p.add_argument("--beskrivelse", action="store_true",
                   help="ERSTAT MR'ens beskrivelse med tekstfilen i stedet for at poste en kommentar")
    p.add_argument("--titel", metavar="TEKST", default=None,
                   help="saet ogsaa MR'ens titel (kun sammen med --beskrivelse)")
    a = p.parse_args()

    if a.titel is not None and not a.beskrivelse:
        print("--titel giver kun mening sammen med --beskrivelse", file=sys.stderr)
        return 2

    krop = io.open(a.tekstfil, encoding="utf-8").read()
    if not krop.strip():
        print("tekstfilen er tom - sender intet", file=sys.stderr)
        return 2

    # DE UDADVENDTE VAGTER FOERST, FOER tokenet hentes og FOER der kaldes ud af huset.
    # Raekkefoelgen er ikke kosmetik: et afvist kald maa ikke have naaet at hente en
    # hemmelighed, og en bruger uden token skal stadig faa laengde-dommen at se.
    # De to tilstande deler vagterne og adskiller sig KUN i loft og endpoint.
    hvad = "MR-BESKRIVELSEN" if a.beskrivelse else "KOMMENTAREN"
    navn = "mr-beskrivelse-loft" if a.beskrivelse else "mr-kommentar-loft"
    try:
        note = doem_adversarisk(a.adversarisk)
        loft = laes_loft(navn)
        n = doem_laengde(krop, loft, hvad)
    except Afvist as e:
        print(e, file=sys.stderr)
        return 2
    print("laengde  : %d tegn (loft %d)" % (n, loft))

    token = os.environ.get("GL_TOKEN", "").strip()
    if not token:
        print("GL_TOKEN mangler i miljøet - se docstring", file=sys.stderr)
        return 2

    if a.beskrivelse:
        nyttelast = {"description": krop.strip()}
        if a.titel:
            nyttelast["title"] = a.titel
        req = urllib.request.Request(URL_MR.format(mr=a.mr), method="PUT")
    else:
        nyttelast = {"body": krop}
        req = urllib.request.Request(URL.format(mr=a.mr), method="POST")
    req.add_header("PRIVATE-TOKEN", token)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(
                req, json.dumps(nyttelast).encode("utf-8"), timeout=90) as r:
            res = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print("HTTP %s: %s" % (e.code, e.read().decode("utf-8", "replace")[:800]))
        return 1

    if a.beskrivelse:
        # TILBAGELAESNING, ikke et 200: et skrivende kald er foerst bevist naar feltet
        # STAAR som det blev sendt (husets regel om skrivende API-kald, punkt 1).
        tilbage = (res.get("description") or "").strip()
        print("MR       : !%s  %s" % (res.get("iid"), res.get("web_url")))
        print("titel    :", res.get("title"))
        print("tegn     :", len(tilbage))
        print("identisk :", tilbage == krop.strip())
        if tilbage != krop.strip():
            print("ADVARSEL: den tilbagelaeste beskrivelse er IKKE den sendte", file=sys.stderr)
            kvitter(note)
            return 1
    else:
        print("note id  :", res.get("id"))
        print("forfatter:", (res.get("author") or {}).get("username"))
        print("oprettet :", res.get("created_at"))
        print("tegn     :", len(krop))
    kvitter(note)
    return 0


if __name__ == "__main__":
    sys.exit(main())
