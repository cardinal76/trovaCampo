/**
 * Gli ultimi risultati di una società, come li manda il backend di TrovaCampo
 * (vedi {@code RisultatiSocieta} nel backend), che a sua volta li legge
 * dall'anagrafica di presenze.
 *
 * Questo file e {@link SocietaService.risultati} sono l'unico posto dove sta
 * la forma della risposta: un campo che cambia nome si corregge qui.
 */

/** Dal punto di vista della squadra della riga: lo decide presenze. */
export type Esito = 'VITTORIA' | 'PAREGGIO' | 'SCONFITTA';

/**
 * Come va letto l'elenco:
 *
 * - `DISPONIBILI`: è quello che presenze ha, anche se vuoto (inizio stagione);
 * - `NON_DISPONIBILI`: presenze non ha i risultati di questa società — non la
 *   conosce, oppure è un presenze che non ha ancora l'endpoint. Riprovare non
 *   serve;
 * - `ERRORE`: presenze non ha risposto, e fra poco può andare.
 */
export type StatoRisultati = 'DISPONIBILI' | 'NON_DISPONIBILI' | 'ERRORE';

/**
 * L'ultima partita giocata da una squadra della società: **una riga per
 * squadra**, non una delle ultime N partite.
 *
 * Niente marcatori: il tabellino individuale dei campionati giovanili non
 * esce dall'area autenticata di presenze, perché i calciatori sono
 * minorenni, e una scheda di risultati non ha bisogno di nomi di ragazzi.
 */
export interface Risultato {
  /**
   * La squadra della riga: è la chiave con cui si disegna l'elenco, perché nel
   * derby fra la prima e la squadra B la stessa partita arriva due volte, una
   * per squadra.
   */
  squadraIscrittaId: number;
  /** ISO 8601 con il fuso, "2026-10-11T10:30:00+02:00"; manca se presenze non sa l'ora. */
  data?: string | null;
  campionato: string;
  /** "Regionali" per il Comitato, il nome della delegazione per i provinciali. */
  ente?: string | null;
  /** Assente finché i gironi non escono. */
  girone?: string | null;
  giornata?: number | null;
  /** Vuota per la prima squadra, "B" per la seconda. */
  squadra: string;
  casa: string;
  ospite: string;
  golCasa: number;
  golOspite: number;
  /** Da che parte giocava la squadra della riga. */
  inCasa: boolean;
  /** Non si ricalcola confrontando i gol: arriva già deciso da presenze. */
  esito: Esito;
}

export interface Risultati {
  stato: StatoRisultati;
  /**
   * Una riga per squadra -- l'ultima che ognuna ha giocato -- dalla più
   * recente, già in quest'ordine.
   */
  risultati: Risultato[];
}

/**
 * Sempre con la data italiana, anche per chi apre la pagina da un telefono
 * con un altro fuso: l'orario è quello del comunicato, come per le partite.
 */
const GIORNO = new Intl.DateTimeFormat('it-IT', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  timeZone: 'Europe/Rome',
});

/** "dom 11 ott", o null per una partita di cui presenze non sa la data. */
export function giornoRisultato(risultato: Risultato): string | null {
  if (!risultato.data) {
    return null;
  }
  return GIORNO.format(new Date(risultato.data)).replace('.', '');
}

/** "2 - 1", sempre casa prima di ospite, come lo scrive il comunicato. */
export function punteggio(risultato: Risultato): string {
  return `${risultato.golCasa} - ${risultato.golOspite}`;
}

/** "V", "P", "S": il bollino accanto alla riga. Il nome per intero è in {@link nomeEsito}. */
export function siglaEsito(risultato: Risultato): string {
  return risultato.esito.charAt(0);
}

/** "Vittoria", per chi legge con lo screen reader: una "V" da sola non dice niente. */
export function nomeEsito(risultato: Risultato): string {
  return risultato.esito.charAt(0) + risultato.esito.slice(1).toLowerCase();
}

/** "Vittoria in casa 2 - 1 contro ALTRA A.S.D., dom 11 ott": la riga letta per intero. */
export function risultatoPerEsteso(risultato: Risultato): string {
  const dove = risultato.inCasa ? 'in casa' : 'in trasferta';
  const avversario = risultato.inCasa ? risultato.ospite : risultato.casa;
  const quando = giornoRisultato(risultato);
  return (
    `${nomeEsito(risultato)} ${dove} ${punteggio(risultato)} contro ${avversario}` +
    (quando ? `, ${quando}` : '')
  );
}

/** "3ª giornata · Girone A · Roma · squadra B"; le parti che mancano si saltano. */
export function dettaglioRisultato(risultato: Risultato): string {
  const parti: string[] = [];
  if (risultato.giornata) {
    parti.push(`${risultato.giornata}ª giornata`);
  }
  if (risultato.girone) {
    parti.push(`Girone ${risultato.girone}`);
  }
  if (risultato.ente) {
    parti.push(risultato.ente);
  }
  if (risultato.squadra) {
    parti.push(`squadra ${risultato.squadra}`);
  }
  return parti.join(' · ');
}
