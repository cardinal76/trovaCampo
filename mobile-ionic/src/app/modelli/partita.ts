/**
 * Una partita in calendario sul campo dove si gioca: arriva dal calendario
 * di presenze (vedi PartiteSuiCampi.Partita nel backend). Il campo è quello
 * di casa della squadra che ospita.
 */
export interface Partita {
  /** ISO 8601 con il fuso, "2026-09-06T11:00:00+02:00". */
  dataOra: string;
  casa: string;
  ospite: string;
  campionato: string;
  /** "Regionali" per il Comitato, il nome della delegazione per i provinciali. */
  ente?: string;
  girone?: string;
  giornata: number;
}

/** Le prime partite della settimana, per campo: la chiave è l'id del campo in presenze. */
export type PartitePerCampo = Record<string, Partita[]>;

/** Quanti giorni guarda avanti la mappa: gli stessi del backend (PartiteSuiCampi.GIORNI_MAPPA). */
export const GIORNI_PARTITE_MAPPA = 7;
/** E quanti la scheda (PartiteSuiCampi.GIORNI_SCHEDA). */
export const GIORNI_PARTITE_SCHEDA = 14;

/**
 * Sempre con l'ora italiana, anche per chi apre la pagina da un telefono con
 * un altro fuso: l'orario è quello del comunicato, e al campo si va a Roma.
 */
const GIORNO = new Intl.DateTimeFormat('it-IT', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  timeZone: 'Europe/Rome',
});
const ORA = new Intl.DateTimeFormat('it-IT', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Europe/Rome',
});

/** "dom 6 set, 11:00" */
export function quandoPartita(partita: Partita): string {
  const data = new Date(partita.dataOra);
  return `${GIORNO.format(data)}, ${ORA.format(data)}`;
}

/** "BOREALE – VIGOR PERCONTI" */
export function squadrePartita(partita: Partita): string {
  return `${partita.casa} – ${partita.ospite}`;
}

/** "ECCELLENZA · Girone A · Regionali" */
export function campionatoPartita(partita: Partita): string {
  return [partita.campionato, partita.girone ? `Girone ${partita.girone}` : '', partita.ente ?? '']
    .filter(Boolean)
    .join(' · ');
}

/** Le partite di un campo, o nessuna se il campo non viene dall'anagrafica di presenze. */
export function partiteDelCampo(
  partite: PartitePerCampo,
  campo: { anagraficaImpiantoId?: number },
): Partita[] {
  return campo.anagraficaImpiantoId === undefined || campo.anagraficaImpiantoId === null
    ? []
    : (partite[String(campo.anagraficaImpiantoId)] ?? []);
}
