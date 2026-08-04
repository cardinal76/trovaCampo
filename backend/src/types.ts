export type TipoCampionato = "ScuolaCalcio" | "Agonistica";

export interface Campionato {
  descrizione: string;
  girone: string;
  comitato: string;
  tipo: TipoCampionato;
}

export interface Societa {
  id: string;
  siglaSocieta: string;
  nomeSocieta: string;
  comitatoRegionale: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
  localitaImpianto: string;
  provinciaImpianto: string;
  lat?: number;
  lng?: number;

  // Anagrafica (Funzione 2). Non sempre disponibile, es. per i campi
  // inseriti manualmente con la sola Funzione 1.
  matricola?: string;
  presidente?: string;
  indirizzoSede?: string;
  telefono?: string;
  fax?: string;
  email?: string;
  sitoWeb?: string;
  scuolaCalcio?: boolean;
  prezziScuolaCalcio?: string;

  // Campionati a cui la società partecipa (Funzione 3).
  campionati?: Campionato[];
}

export interface NuovoCampoInput {
  nomeSocieta: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
}
