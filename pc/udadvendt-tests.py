#!/usr/bin/env python3
"""Testben for den udadvendte gate (`pc/udadvendt.py`) og de to værktøjer der bruger den.

Kør:  py -3.11 pc/udadvendt-tests.py

ET FORVENTET BENTAL, ikke bare »alle grønne« (måleregel 232): en kastet fejl i en
tæller kan ellers melde »N af N« om et afkortet løb, så suiten erklærer selv hvor
mange ben den har, og fejler hvis den kørte færre.

BENENE MÅ ALDRIG KALDE UD AF HUSET. De to værktøjer testes derfor via deres CLI med
et TOMT `GL_TOKEN`: de udadvendte vagter ligger FØR tokenet hentes, så en afvisning
måles på vagten, mens et kald der slipper igennem vagten stopper på det manglende
token. Det skel er selve pointen, og et ben asserterer det eksplicit.
"""
import io
import os
import subprocess
import sys
import tempfile

HER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HER)
import udadvendt  # noqa: E402

# Talt i HAANDEN fra kilden (7 om laes_loft, 6 om doem_laengde/doem_adversarisk,
# 5 om fdroid-mr-comment.py's kommentar-CLI, 7 om dens --beskrivelse- og titel-tilstand,
# 6 om fdroid-fork-update.py's CLI). Tallet maa IKKE afledes af en koersel - saa
# kunne et ben der falder ud ikke skelnes fra et ben der aldrig fandtes
# (maaleregel 232).
FORVENTEDE_BEN = 31
resultater = []


def ben(navn, betingelse, detalje=""):
    resultater.append((navn, bool(betingelse), detalje))
    print("%-6s %s%s" % ("OK" if betingelse else "FEJL", navn,
                         ("  <- " + detalje) if detalje and not betingelse else ""))


def skriv(mappe, navn, tekst):
    sti = os.path.join(mappe, navn)
    with io.open(sti, "w", encoding="utf-8", newline="\n") as f:
        f.write(tekst)
    return sti


REGISTER_FIKSTUR = None   # sættes i main(); se noten dér


def koer(script, *args, token=""):
    """Kald et af værktøjerne via CLI'en. Returnerer (rc, stdout+stderr)."""
    miljoe = dict(os.environ)
    miljoe["GL_TOKEN"] = token
    miljoe["PYTHONIOENCODING"] = "utf-8"
    # FIKSTUREN ER PINNET (måleregel 382). Uden den læste CLI-benene husets rigtige
    # `konstanter.tsv`, som (a) ligger uden for det offentlige repo filerne udgives i, så
    # ti ben fejlede i en klon, og (b) får sine tal sat af et spor i en ANDEN plan, så
    # benet »500 > 400« ville gå rødt den dag tallet ændres, uden at koden var forkert.
    if REGISTER_FIKSTUR:
        miljoe["HUSK_KONSTANTER"] = REGISTER_FIKSTUR
    p = subprocess.run([sys.executable, os.path.join(HER, script)] + list(args),
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace", env=miljoe)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def main():
    global REGISTER_FIKSTUR
    with tempfile.TemporaryDirectory() as td:

        # ---- laes_loft -----------------------------------------------------------
        reg_ok = skriv(td, "reg-ok.tsv",
                       "# kommentar\nnavn\tvaerdi\tmoenster\tforklaring\n"
                       "mr-beskrivelse-loft\t800\tx\tfoo\n"
                       "mr-kommentar-loft\t400\tx\tbar\n"
                       "mr-titel-loft\t200\tx\tbaz\n")
        REGISTER_FIKSTUR = reg_ok
        ben("laes_loft laeser beskrivelses-loftet",
            udadvendt.laes_loft("mr-beskrivelse-loft", sti=reg_ok) == 800)
        ben("laes_loft laeser kommentar-loftet",
            udadvendt.laes_loft("mr-kommentar-loft", sti=reg_ok) == 400)

        # Registret findes IKKE -> fejl LUKKET, og beskeden navngiver filen.
        mangler = os.path.join(td, "findes-ikke.tsv")
        try:
            udadvendt.laes_loft("mr-kommentar-loft", sti=mangler)
            ben("ulaeseligt register fejler lukket", False, "kastede ikke")
            ben("ulaeseligt register navngiver filen", False, "kastede ikke")
        except udadvendt.Afvist as e:
            ben("ulaeseligt register fejler lukket", True)
            ben("ulaeseligt register navngiver filen", "findes-ikke.tsv" in str(e), str(e)[:80])

        # Raekken mangler -> fejl LUKKET (ingen default, hverken tavs eller hoejlydt).
        reg_tom = skriv(td, "reg-tom.tsv", "navn\tvaerdi\tmoenster\tforklaring\n")
        try:
            udadvendt.laes_loft("mr-kommentar-loft", sti=reg_tom)
            ben("manglende raekke fejler lukket", False, "kastede ikke")
            ben("manglende raekke navngiver konstanten", False, "kastede ikke")
        except udadvendt.Afvist as e:
            ben("manglende raekke fejler lukket", True)
            ben("manglende raekke navngiver konstanten", "mr-kommentar-loft" in str(e), str(e)[:80])

        # En vaerdi der ikke er et tal -> fejl LUKKET (ikke en tavs default).
        reg_skrald = skriv(td, "reg-skrald.tsv",
                           "navn\tvaerdi\tmoenster\tforklaring\nmr-kommentar-loft\tfire\tx\ty\n")
        try:
            udadvendt.laes_loft("mr-kommentar-loft", sti=reg_skrald)
            ben("ikke-numerisk vaerdi fejler lukket", False, "kastede ikke")
        except udadvendt.Afvist:
            ben("ikke-numerisk vaerdi fejler lukket", True)

        # ---- doem_laengde --------------------------------------------------------
        ben("under loftet slipper igennem", udadvendt.doem_laengde("a" * 399, 400, "X") == 399)
        ben("praecis paa loftet slipper igennem", udadvendt.doem_laengde("a" * 400, 400, "X") == 400)
        try:
            udadvendt.doem_laengde("a" * 401, 400, "KOMMENTAREN")
            ben("over loftet afvises", False, "kastede ikke")
            ben("afvisningen baerer det MAALTE tal", False, "kastede ikke")
        except udadvendt.Afvist as e:
            ben("over loftet afvises", True)
            ben("afvisningen baerer det MAALTE tal", "401" in str(e) and "400" in str(e), str(e)[:80])

        # ---- doem_adversarisk ----------------------------------------------------
        ben("en kvittering slipper igennem", udadvendt.doem_adversarisk(" fandt to fejl ") == "fandt to fejl")
        afvist = 0
        for tom in ("", "   ", None):
            try:
                udadvendt.doem_adversarisk(tom)
            except udadvendt.Afvist:
                afvist += 1
        ben("blank/manglende kvittering afvises (3 former)", afvist == 3, "afviste %d af 3" % afvist)

        # ---- CLI: fdroid-mr-comment.py -------------------------------------------
        kort = skriv(td, "kort.md", "Thanks. Rebased onto master.\n")
        lang = skriv(td, "lang.md", "x" * 900 + "\n")

        rc, ud1 = koer("fdroid-mr-comment.py", kort, "--mr", "49350")
        ben("kommentar UDEN --adversarisk afvises", rc != 0, "rc=%d" % rc)
        ben("afvisningen naevner --adversarisk", "--adversarisk" in ud1, ud1[:100])

        rc, ud2 = koer("fdroid-mr-comment.py", lang, "--mr", "49350",
                       "--adversarisk", "gennemgik teksten, fandt intet")
        ben("kommentar over loftet afvises", rc != 0, "rc=%d" % rc)
        ben("afvisningen baerer 900", "900" in ud2, ud2[:120])

        rc, ud3 = koer("fdroid-mr-comment.py", kort, "--mr", "49350",
                       "--adversarisk", "gennemgik teksten, fandt intet")
        ben("kort kommentar MED kvittering naar forbi vagterne", "GL_TOKEN mangler" in ud3, ud3[:120])

        # ---- CLI: --beskrivelse deler vagterne, men har SIT EGET loft ---------------
        # 500 tegn er OVER kommentar-loftet (400) og UNDER beskrivelses-loftet (800).
        # Benet kan derfor kun bestaa hvis tilstanden vaelger det RIGTIGE loft: et
        # faelles loft ville fejle i den ene eller den anden retning.
        mellem = skriv(td, "mellem.md", "z" * 500 + "\n")
        rc, ud7 = koer("fdroid-mr-comment.py", mellem, "--mr", "49350", "--beskrivelse",
                       "--adversarisk", "gennemgik udkastet")
        ben("--beskrivelse bruger beskrivelses-loftet, ikke kommentar-loftet",
            "GL_TOKEN mangler" in ud7, ud7[:160])
        rc, ud8 = koer("fdroid-mr-comment.py", mellem, "--mr", "49350",
                       "--adversarisk", "gennemgik udkastet")
        ben("SAMME tekst afvises som KOMMENTAR (500 > 400)", rc != 0 and "500" in ud8, ud8[:160])
        rc, ud9 = koer("fdroid-mr-comment.py", lang, "--mr", "49350", "--beskrivelse",
                       "--adversarisk", "gennemgik udkastet")
        ben("--beskrivelse over 800 afvises", rc != 0 and "900" in ud9, ud9[:160])
        rc, ud10 = koer("fdroid-mr-comment.py", mellem, "--mr", "49350", "--beskrivelse")
        ben("--beskrivelse UDEN --adversarisk afvises", rc != 0, "rc=%d" % rc)
        rc, ud11 = koer("fdroid-mr-comment.py", kort, "--mr", "49350", "--titel", "T",
                        "--adversarisk", "x")
        ben("--titel uden --beskrivelse afvises", rc != 0, "rc=%d" % rc)

        # ---- TITLEN er ogsaa gatet (hullet fundet af Trin 3 den 2026-09-20) --------
        lang_titel = "T" * 250
        rc, ud12 = koer("fdroid-mr-comment.py", mellem, "--mr", "49350", "--beskrivelse",
                        "--titel", lang_titel, "--adversarisk", "x")
        ben("--titel over titel-loftet afvises", rc != 0 and "250" in ud12, ud12[:160])
        rc, ud13 = koer("fdroid-mr-comment.py", mellem, "--mr", "49350", "--beskrivelse",
                        "--titel", "Update Husk to 1.2 (53)", "--adversarisk", "x")
        ben("en normal titel slipper igennem", "GL_TOKEN mangler" in ud13, ud13[:160])

        # ---- CLI: fdroid-fork-update.py ------------------------------------------
        recipe = skriv(td, "recipe.yml", "AutoName: Husk\n")
        mr_kort = skriv(td, "mr-kort.md", "Titel\n\n" + "y" * 400 + "\n")
        mr_lang = skriv(td, "mr-lang.md", "Titel\n\n" + "y" * 900 + "\n")

        rc, ud4 = koer("fdroid-fork-update.py", recipe, "-m", "b", "--opret-mr", mr_kort)
        ben("--opret-mr UDEN --adversarisk afvises", rc != 0, "rc=%d" % rc)
        ben("fork-update naevner --adversarisk", "--adversarisk" in ud4, ud4[:100])

        rc, ud5 = koer("fdroid-fork-update.py", recipe, "-m", "b", "--opret-mr", mr_lang,
                       "--adversarisk", "gennemgik udkastet, rettede et tal")
        ben("--opret-mr over loftet afvises", rc != 0, "rc=%d" % rc)
        ben("fork-update baerer 900", "900" in ud5, ud5[:120])

        rc, ud6 = koer("fdroid-fork-update.py", recipe, "-m", "b", "--opret-mr", mr_kort,
                       "--adversarisk", "gennemgik udkastet, rettede et tal")
        ben("kort MR-tekst MED kvittering naar forbi vagterne", "GL_TOKEN mangler" in ud6, ud6[:120])

        # Filens FOERSTE linje er MR-titlen, og den skal gates som titel, ikke som krop.
        mr_langtitel = skriv(td, "mr-langtitel.md", "T" * 250 + "\n\n" + "y" * 400 + "\n")
        rc, ud14 = koer("fdroid-fork-update.py", recipe, "-m", "b", "--opret-mr", mr_langtitel,
                        "--adversarisk", "x")
        ben("--opret-mr med for lang FOERSTE linje afvises", rc != 0 and "250" in ud14, ud14[:160])

    gode = sum(1 for _, ok, _ in resultater if ok)
    print("\n%d af %d ben groenne (forventet %d)" % (gode, len(resultater), FORVENTEDE_BEN))
    if len(resultater) != FORVENTEDE_BEN:
        print("FEJL: suiten koerte %d ben, ikke %d - et ben er faldet ud eller kom til."
              % (len(resultater), FORVENTEDE_BEN))
        return 1
    return 0 if gode == len(resultater) else 1


if __name__ == "__main__":
    sys.exit(main())
