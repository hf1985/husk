#!/usr/bin/env python3
"""Den udadvendte gate for husets to F-Droid-værktøjer: længde og adversarisk kvittering.

ÉT KODESTED, ikke to kopier. `fdroid-fork-update.py --opret-mr` og
`fdroid-mr-comment.py` skriver begge tekst ud af huset til en offentlig,
frivilligdrevet anmelderkø, og de kan ikke kalde den tilbage. En vagt hver af dem
selv skulle huske, er en vagt der bliver glemt næste gang der kommer et værktøj til.

HVORFOR DEN FINDES.
`_styresystem/laering/2026-09-06-udadvendt-tekst-uden-for-mail-gaten.md` skrev hullet
ned: husets adversariske gate er formuleret om MAIL og håndhævet i ÉT værktøj
(`gmail.py --adversarisk`), mens en offentlig MR-tekst er lige så udadvendt og lige
så uigenkaldelig. Noten lod regel-udvidelsen stå som ejerens beslutning.

Ejeren afgjorde den 2026-09-19, verbatim: »Regel + tegn-lofter + udvid gaten til al
udadvendt tekst«.

Anledningen var målt af en ekstern part før huset selv målte den. linsui, F-Droid,
2026-09-19 kl. 07:58 UTC i MR !49350, verbatim: »Don't write so long description.
We can't read it.« Beskrivelsen var 6.193 tegn over 87 linjer.

TO VAGTER, OG DE DÆKKER IKKE HINANDEN.
- `doem_laengde` er en MÅLING. Den kan afgøre sit eget spørgsmål og fejler lukket.
- `doem_adversarisk` er en KVITTERING, ikke et bevis - samme klasse som
  `gmail.py`s. Den kan ikke måle AT en gennemgang fandt sted; den gør skridtet
  bevidst og skriver hvad afsenderen påstod, så påstanden står i terminalen og i et
  eventuelt joblog. En kort tekst kan være lige så forkert som en lang.
"""
import os
import sys

# Registret er husets ene kilde til tallene. Stien UDLEDES af denne fils egen
# placering - Google Drive monterer roden lokaliseret (`My Drive` / `Mit drev`), så en
# hardkodet rod er en TAVS no-op frem for en fejl.
#   .../<drev-rod>/10_PROJEKTER/P_app_husk/pc/udadvendt.py
#   parents:            [2]        [1]      [0]
REGISTER = os.environ.get("HUSK_KONSTANTER") or os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "_styresystem", "konstanter.tsv")

# `HUSK_KONSTANTER` findes FOR TESTENE, og det er ikke en bekvemmelighed.
# Registret bor i governance-træet, altså UDEN FOR det offentlige repo disse filer
# udgives i, og dets tal sættes af en anden plans spor. En testsuite der læser det
# rigtige register, måler derfor noget der kan ændre sig under den uden at koden gør:
# målt 2026-09-20 fejlede ti CLI-ben i en klon uden for porteføljen, og de ville gå røde
# igen den dag en række får en anden værdi (måleregel 382 - en fikstur skal pinnes).
# Sæt variablen i en test, aldrig i drift.

# Midlertidige defaults indtil spor K1 i
# `_styresystem/planer/2026-09-19-korthed-med-et-maalt-loft-plan.md` har lagt rækkerne
# i registret. De MELDES HØJLYDT hver gang de bruges (`laes_loft`), fordi en tavs
# fallback er den fælde registret findes for at lukke.
MIDLERTIDIGE = {"mr-beskrivelse-loft": 800, "mr-kommentar-loft": 400, "mr-titel-loft": 200}


class Afvist(Exception):
    """Noget nåede IKKE ud. Kalderen skal returnere en exitkode forskellig fra 0."""


def laes_loft(navn, sti=None, ud=None):
    """Slå et loft op i konstant-registret.

    Tre udfald, og de må ikke kunne forveksles (måleregel 116):
      1. Rækken findes      -> tallet.
      2. Registret findes ikke, eller kan ikke læses, eller rækkens værdi er ikke et
         tal -> `Afvist`. Der fejles LUKKET, og beskeden NAVNGIVER filen: kan
         måleredskabet ikke måle, er svaret ikke »så send bare«.
      3. Registret kan læses, men rækken mangler endnu -> den midlertidige default,
         meldt HØJLYDT med navnet på det spor der fjerner den.
    """
    sti = sti or REGISTER
    ud = ud if ud is not None else sys.stderr
    try:
        with open(sti, encoding="utf-8") as f:
            raa = f.read()
    except OSError as e:
        raise Afvist(
            "KAN IKKE LÆSE KONSTANT-REGISTRET, og så sendes der ingenting.\n"
            "  fil : %s\n"
            "  fejl: %s\n"
            "  Lofterne for udadvendt tekst bor DER og kun der. Et loft der ikke kan\n"
            "  måles, må ikke blive til »intet loft«." % (sti, e))

    for linje in raa.splitlines():
        if not linje.strip() or linje.lstrip().startswith("#"):
            continue
        felter = linje.split("\t")
        if len(felter) < 2 or felter[0].strip() != navn:
            continue
        vaerdi = felter[1].strip()
        if not vaerdi.isdigit():
            raise Afvist(
                "KONSTANTEN %r har værdien %r, som ikke er et tal.\n"
                "  fil: %s\n"
                "  Et loft der ikke kan læses som et tal, er ikke et loft." % (navn, vaerdi, sti))
        return int(vaerdi)

    if navn not in MIDLERTIDIGE:
        raise Afvist(
            "KONSTANTEN %r findes ikke i registret, og der er ingen midlertidig default.\n"
            "  fil: %s" % (navn, sti))
    standard = MIDLERTIDIGE[navn]
    print(
        "ADVARSEL: %r står endnu ikke i konstant-registret; bruger den MIDLERTIDIGE\n"
        "          default %d. Registret findes og kunne læses - rækken mangler.\n"
        "          fil : %s\n"
        "          kur : spor K1 i _styresystem/planer/2026-09-19-korthed-med-et-maalt-loft-plan.md\n"
        "          Når rækken er lagt ind, gælder registrets tal automatisk."
        % (navn, standard, sti), file=ud)
    return standard


def doem_laengde(tekst, loft, hvad):
    """Afvis en for lang udadvendt tekst. Fejlen BÆRER det målte tal.

    En vagt der siger »for lang« uden et tal, kan ikke bruges til at vide hvor meget
    der skal væk (samme grund som `check-claudemd-size.sh`s MARGEN-linje).
    """
    n = len((tekst or "").strip())
    if n > loft:
        raise Afvist(
            "%s ER FOR LANG: %d tegn, loftet er %d. Intet er sendt.\n"
            "  Skær %d tegn væk, eller flyt detaljen derhen hvor den hører til\n"
            "  (butiksteksten, changelogen, repoets docs).\n"
            "  Loftet står i %s." % (hvad, n, loft, n - loft, REGISTER))
    return n


def doem_adversarisk(note):
    """Ingen udadvendt tekst forlader huset uden en adversarisk gennemgang først.

    Ejerbeslutning 2026-09-19 (`B4` i
    `_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md`), verbatim:
    »Regel + tegn-lofter + udvid gaten til al udadvendt tekst«. Den udvider ejerens
    mail-ordre af 2026-09-02 til denne kanal.

    En BLANK tekst tæller ikke: et flag man kan opfylde med `--adversarisk ""` er
    ingen gate, kun en ekstra tast.
    """
    n = (note or "").strip()
    if not n:
        raise Afvist(
            "UDADVENDT TEKST KRÆVER EN ADVERSARISK GENNEMGANG FØRST (ejerbeslutning 2026-09-19).\n"
            "  Intet er sendt.\n"
            "  1. Send en FRISK sub-agent af sted med teksten OG de artefakter påstandene\n"
            "     hviler på. Bed den finde påstande uden dækning, forkerte tal og\n"
            "     tidspunkter, og alt der lover mere end målingen bærer.\n"
            "  2. Ret det den finder, og efterprøv hvert fund på disk.\n"
            "  3. Kald igen med --adversarisk <kort tekst om hvad der blev gennemgået\n"
            "     og hvad den fandt>.\n"
            "  Flaget er en KVITTERING, ikke et bevis: det gør skridtet bevidst og\n"
            "  skriver din påstand i outputtet.")
    return n


def kvitter(note, ud=None):
    """Skriv kvitteringen i outputtet, som `gmail.py` gør. Kaldes EFTER afsendelsen."""
    ud = ud if ud is not None else sys.stdout
    print("adversarisk: " + note, file=ud)
