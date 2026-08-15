export type TipoCampionato = 'ScuolaCalcio' | 'Agonistica';

export interface Campionato {
  descrizione: string;
  girone: string;
  comitato: string;
  tipo: TipoCampionato;
}

/** Società con il suo campo. Anagrafica e campionati non sono sempre disponibili. */
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

export interface NuovoCampo {
  nomeSocieta: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
}

/** Società di cui conosciamo la posizione, quindi mostrabile sulla mappa. */
export type SocietaGeolocalizzata = Societa & { lat: number; lng: number };

export function haCoordinate(societa: Societa): societa is SocietaGeolocalizzata {
  return typeof societa.lat === 'number' && typeof societa.lng === 'number';
}

export function nomeCompleto(societa: Societa): string {
  return [societa.siglaSocieta, societa.nomeSocieta].filter(Boolean).join(' ').trim();
}

/** Evita gli indirizzi tipo "Via Roma 1,  ()" per i campi inseriti a mano. */
export function indirizzoCompleto(societa: Societa): string {
  const localita = societa.localitaImpianto ? `, ${societa.localitaImpianto}` : '';
  const provincia = societa.provinciaImpianto ? ` (${societa.provinciaImpianto})` : '';
  return `${societa.indirizzoImpianto}${localita}${provincia}`;
}
