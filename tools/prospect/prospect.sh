#!/usr/bin/env bash
# Comandi dell'importazione delle ASD/SSD dal Registro nazionale (RASD), da
# terminale invece che dalla pagina /admin/prospect. Vedi README.md qui accanto.
#
#   TOKEN=... ./prospect.sh avvia [regione ...]   # vuoto = tutta Italia, 12 = Lazio
#   TOKEN=... ./prospect.sh prova                 # Lazio, due pagine sole
#   TOKEN=... ./prospect.sh stato
#   TOKEN=... ./prospect.sh ferma
#   TOKEN=... ./prospect.sh collega               # campi LND e open data, subito
#   TOKEN=... ./prospect.sh opendata              # (re)importa il censimento impianti open data
#   TOKEN=... ./prospect.sh geocodifica [limite]  # OpenStreetMap per chi manca, in sottofondo
#   TOKEN=... ./prospect.sh geostato              # a che punto e' la geocodifica
#   TOKEN=... ./prospect.sh copertura             # quanti hanno l'indirizzo, per fonte
#   TOKEN=... ./prospect.sh export [file.xlsx]    # prospect_italia.xlsx
#
# TOKEN e' un access token del Keycloak di presenze (client trovacampo-frontend)
# di un utente con il ruolo trovacampo-admin. TROVACAMPO_URL di default e' la
# produzione.
set -euo pipefail

BASE="${TROVACAMPO_URL:-https://trovacampo.footballer.it}/api/admin"
URL="${BASE}/prospect"
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
  opendata)
    curl -fsS -X POST "${AUTH[@]}" "${BASE}/indirizzi/impianti-open-data"
    ;;
  geocodifica)
    limite="${1:+?limite=$1}"
    curl -fsS -X POST "${AUTH[@]}" "${URL}/geocodifica${limite}"
    ;;
  geostato)
    curl -fsS "${AUTH[@]}" "${URL}/geocodifica"
    ;;
  copertura)
    curl -fsS "${AUTH[@]}" "${URL}/copertura"
    ;;
  export)
    file="${1:-prospect_italia.xlsx}"
    curl -fsS "${AUTH[@]}" -o "$file" "${URL}/export.xlsx"
    echo "Scritto $file"
    ;;
  *)
    sed -n '2,19p' "$0"
    exit 1
    ;;
esac
echo
