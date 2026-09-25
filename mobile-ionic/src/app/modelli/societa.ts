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
  /**
   * Il segnaposto l'ha messo la geocodifica senza il civico: sta sulla via,
   * non per forza davanti al campo. Assente quando è preciso.
   */
  posizioneApprossimata?: boolean;

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

  /** La stessa società nell'anagrafica di presenze: da lì arrivano squadre e campionati. */
  anagraficaSocietaId?: number;
  /**
   * Il campo nell'anagrafica di presenze: la chiave con cui gli si abbinano
   * le partite in calendario. Assente per i campi da file o inseriti a mano.
   */
  anagraficaImpiantoId?: number;
  /**
   * Lo stemma sul portale LND (https), portato dalla sincronizzazione con
   * presenze. Assente quando non ce n'è uno: si mostra l'iniziale del nome.
   */
  logoUrl?: string;
}

/** La scheda come la manda la pagina di modifica: tutto tranne l'id. */
export type ModificaSocieta = Omit<Societa, 'id'>;

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

/**
 * Da correggere a mano: senza coordinate, o con un segnaposto che la
 * geocodifica ha trovato solo togliendo il civico. È il filtro che chi
 * amministra usa nell'elenco.
 */
export function senzaPosizionePrecisa(societa: Societa): boolean {
  return !haCoordinate(societa) || societa.posizioneApprossimata === true;
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

const ENTITA_HTML: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

/**
 * Il contenuto dei popup di Leaflet è HTML: i dati che ci finiscono dentro
 * arrivano dall'API (comprese le segnalazioni degli utenti) e vanno quindi
 * neutralizzati.
 */
export function testoSicuro(valore: string): string {
  return valore.replace(/[&<>"']/g, (carattere) => ENTITA_HTML[carattere]);
}
