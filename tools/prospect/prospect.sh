#!/usr/bin/env bash
# Comandi dell'importazione delle ASD/SSD dal Registro nazionale (RASD), da
# terminale invece che dalla pagina /admin/prospect. Vedi README.md qui accanto.
#
#   TOKEN=... ./prospect.sh avvia [regione ...]   # vuoto = tutta Italia, 12 = Lazio
#   TOKEN=... ./prospect.sh prova                 # Lazio, due pagine sole
#   TOKEN=... ./prospect.sh stato
#   TOKEN=... ./prospect.sh ferma
#   TOKEN=... ./prospect.sh collega               # collega il calcio laziale ai campi
#   TOKEN=... ./prospect.sh export [file.xlsx]    # prospect_italia.xlsx
#
# TOKEN e' un access token del Keycloak di presenze (client trovacampo-frontend)
# di un utente con il ruolo trovacampo-admin. TROVACAMPO_URL di default e' la
# produzione.
set -euo pipefail

URL="${TROVACAMPO_URL:-https://trovacampo.footballer.it}/api/admin/prospect"
: "${TOKEN:?Serve TOKEN: un access token di Keycloak con il ruolo trovacampo-admin}"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

comando="${1:-stato}"
shift || true

case "$comando" in
  avvia)
    parametri=""
    for regione in "$@"; do parametri+="&regioni=${regione}"; done
    curl -fsS -X POST "${AUTH[@]}" "${URL}/importazione?dettagli=true${parametri}"
    ;;
  prova)
    curl -fsS -X POST "${AUTH[@]}" "${URL}/importazione?regioni=12&maxPagine=2"
    ;;
  stato)
    curl -fsS "${AUTH[@]}" "${URL}/importazione"
    ;;
  ferma)
    curl -fsS -X POST "${AUTH[@]}" "${URL}/importazione/ferma"
    ;;
  collega)
    curl -fsS -X POST "${AUTH[@]}" "${URL}/collegamento"
    ;;
  export)
    file="${1:-prospect_italia.xlsx}"
    curl -fsS "${AUTH[@]}" -o "$file" "${URL}/export.xlsx"
    echo "Scritto $file"
    ;;
  *)
    sed -n '2,15p' "$0"
    exit 1
    ;;
esac
echo
