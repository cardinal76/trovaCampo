# TrovaCampo

App pubblica per trovare il campo di una società di calcio dilettantistico:
backend Spring Boot 3 / Java 21 / MongoDB in `backend-java`, frontend Ionic 8
/ Angular 20 standalone in `mobile-ionic`. Login solo per l'amministrazione,
sul Keycloak di presenze (realm `presenze`, client `trovacampo-frontend`,
ruolo di realm `trovacampo-admin`); ricerca, elenco e mappa restano pubblici.
Società, campi e squadre con i loro campionati arrivano anche
dall'anagrafica di presenze (Comunicati Ufficiali del Lazio), letta dalla
sua API pubblica `/api/pubblico/anagrafica/`: i campi si sincronizzano in
Mongo, le squadre si chiedono a presenze all'apertura della scheda.
Le notifiche push (anonime, dalla pagina `/notifiche`: squadre seguite e
partite vicine) sono spiegate in DEPLOY.md, con rilascio e importazione dei
campi.

## Macchine e tunnel

- **Server di produzione** (Hetzner, `46.225.239.144`, utente `marco`):
  ospita presenze e TrovaCampo (https://trovacampo.footballer.it) dietro lo
  stesso Caddy. TrovaCampo è il progetto compose `trovacampo`, in
  `~/trovacampo`, agganciato alla rete `presenze_default`.
- **server2** (Contabo, `ssh server2`, utente `marco`): solo i runner di
  GitHub Actions, `server2-trovacampo` (etichetta `trovacampo-build`) e
  `server2-presenze` (`presenze-build`). Il rilascio parte con un push sul
  branch `produzione`.
- Dal PC di Marco (WSL) i servizi interni del server si raggiungono con
  questi alias del suo `~/.bashrc`, che aprono e chiudono tunnel SSH in
  background. Quando serve un tunnel, suggerisci questi nomi invece del
  comando `ssh -N -L ...` completo:
  - `tunnel-kc-up` / `tunnel-kc-down`: console di Keycloak, la 8081 del
    server su `localhost:18081` (Host SSH `tunnel-presenze`);
  - `tunnel-db-up` / `tunnel-db-down`: PostgreSQL di presenze su
    `127.0.0.1:5433` (Host SSH `presenze-db`).

  Configurazione completa nel DEPLOY.md di presenze, "Scorciatoie: tunnel-kc
  e tunnel-db".
