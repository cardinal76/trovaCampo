#!/bin/bash
# Ferma i "git push" verso il branch produzione, che fanno partire il deploy.
#
# Su questo repository lavorano piu' sessioni di Claude insieme, e il deploy
# deve partire da una sola, quella che coordina merge e rilasci: le altre
# uniscono le loro PR in master e si fermano li'. La sessione che coordina ha nel
# suo contenitore il file ~/.deploy-produzione-autorizzato e passa; tutte le
# altre ricevono un rifiuto con la spiegazione.
#
# E' una regola di coordinamento, non una barriera di sicurezza: tutte le
# sessioni pushano con lo stesso account GitHub, e una sessione decisa a farlo
# potrebbe aggirarla. Serve a evitare il deploy per sbaglio, non per malizia.
set -uo pipefail

AUTORIZZAZIONE="$HOME/.deploy-produzione-autorizzato"

# Il comando si guarda un pezzo alla volta (righe e comandi separati da ;, &&,
# || o |): altrimenti un messaggio di commit che parla di produzione, nello
# stesso comando di un push qualsiasi, basterebbe a bloccarlo. Conta solo un
# pezzo che e' un git push e nomina produzione come destinazione: "origin
# produzione", "dev:produzione", "HEAD:refs/heads/produzione",
# "--force-with-lease=refs/heads/produzione:...". Esce 2 solo in quel caso;
# con qualsiasi altro esito, input illeggibile compreso, il comando passa.
python3 -c '
import json, re, sys
comando = json.load(sys.stdin).get("tool_input", {}).get("command", "") or ""
push = re.compile(r"(^|[^\w-])git(\s+(-C\s+\S+|-\S+))*\s+push(\s|$)")
prod = re.compile(r"(^|[\s:/=])produzione([\s:;&|)]|$)")
for pezzo in re.split(r"\n|;|&&|\|\||\|", comando):
    if push.search(pezzo) and prod.search(pezzo):
        sys.exit(2)
' 2>/dev/null
[ $? -eq 2 ] || exit 0

if [ -f "$AUTORIZZAZIONE" ]; then
  exit 0
fi

cat <<'FINE'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Il push su produzione fa partire il deploy, e il deploy lo fa solo la sessione che coordina merge e rilasci (vedi CLAUDE.md, \"Rilascio: lo fa una sola sessione\"). Unisci la tua PR in master e fermati li': avvisa l'utente che il lavoro e' pronto per il rilascio."}}
FINE
exit 0
