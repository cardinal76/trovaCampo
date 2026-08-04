import { Societa } from "../types";

/**
 * Dati di esempio (seed) usati in sviluppo, in attesa dell'importazione
 * della banca dati reale dai siti calcistici istituzionali (vedi
 * documenti/analisi/analisi_0.1.docx). Non rappresentano un elenco reale
 * e completo di società dilettantistiche.
 */
export const societa: Societa[] = [
  {
    id: "1",
    siglaSocieta: "A.S.D.",
    nomeSocieta: "Certosa Calcio",
    comitatoRegionale: "LAZIO",
    nomeImpianto: "Campo Certosa",
    indirizzoImpianto: "Via della Certosa 12",
    localitaImpianto: "Roma",
    provinciaImpianto: "RM",
    lat: 41.8919,
    lng: 12.4863,
  },
  {
    id: "2",
    siglaSocieta: "A.S.D.",
    nomeSocieta: "Atletico 400 Tor di Pippo",
    comitatoRegionale: "LAZIO",
    nomeImpianto: "Campo Tor di Pippo",
    indirizzoImpianto: "Via Tor di Pippo 40",
    localitaImpianto: "Roma",
    provinciaImpianto: "RM",
    lat: 41.8567,
    lng: 12.5764,
  },
  {
    id: "3",
    siglaSocieta: "S.R.L.",
    nomeSocieta: "Almas Roma",
    comitatoRegionale: "LAZIO",
    nomeImpianto: "Sant'Anna \"A\" (erba)",
    indirizzoImpianto: "Via Demetriade 78",
    localitaImpianto: "Roma (Tuscolano)",
    provinciaImpianto: "RM",
    lat: 41.8697,
    lng: 12.5514,
  },
  {
    id: "4",
    siglaSocieta: "A.S.D.",
    nomeSocieta: "Virtus Ostia",
    comitatoRegionale: "LAZIO",
    nomeImpianto: "Campo Vittorio Paolucci",
    indirizzoImpianto: "Via delle Baleniere 5",
    localitaImpianto: "Ostia (Roma)",
    provinciaImpianto: "RM",
    lat: 41.7328,
    lng: 12.2836,
  },
  {
    id: "5",
    siglaSocieta: "A.S.D.",
    nomeSocieta: "Nuova Tor Sapienza",
    comitatoRegionale: "LAZIO",
    nomeImpianto: "Campo Tor Sapienza",
    indirizzoImpianto: "Via Ludovico Pavoni 21",
    localitaImpianto: "Roma",
    provinciaImpianto: "RM",
    lat: 41.8895,
    lng: 12.5867,
  },
];
