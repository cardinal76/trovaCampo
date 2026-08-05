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

  matricola?: string;
  presidente?: string;
  indirizzoSede?: string;
  telefono?: string;
  fax?: string;
  email?: string;
  sitoWeb?: string;
  scuolaCalcio?: boolean;
  prezziScuolaCalcio?: string;

  campionati?: Campionato[];
}

export interface NuovoCampoInput {
  nomeSocieta: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
}
