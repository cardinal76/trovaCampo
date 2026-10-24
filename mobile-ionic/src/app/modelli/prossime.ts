/**
 * Le prossime partite con un avviso, come le manda il backend
 * (POST /api/notifiche/prossime, vedi ProssimePartite): le stesse che il
 * pianificatore delle notifiche sceglie, con l'ora a cui parte l'avviso.
 */
export interface ElencoProssime {
  /** L'ultimo giorno guardato, compreso: "2026-09-12". */
  fino: string;
  partite: ProssimaPartita[];
  /** Vero se erano troppe e l'elenco si ferma prima. */
  altre: boolean;
}

export interface ProssimaPartita {
  /** ISO 8601 con il fuso di Roma. */
  dataOra: string;
  casa: string;
  ospite: string;
  campionatoId?: number | null;
  campionato: string;
  ente?: string | null;
  gironeId?: number | null;
  girone?: string | null;
  giornata: number;
  /** Il campo su TrovaCampo; null se non c'è. */
  campo: CampoPartita | null;
  /** Uno, o due se la squadra è seguita e la partita è anche vicina. */
  avvisi: AvvisoPrevisto[];
}

export interface CampoPartita {
  id: string;
  nomeSocieta?: string | null;
  nomeImpianto?: string | null;
  indirizzo?: string | null;
  localita?: string | null;
  provincia?: string | null;
}

export type TipoAvviso = 'squadra' | 'vicino';

/**
 * - `previsto`: l'avviso deve ancora partire;
 * - `inviato`: è partito verso questo browser (lo dice il registro del server);
 * - `passato`: l'ora è passata e a questo browser non risulta arrivato.
 */
export type StatoAvviso = 'previsto' | 'inviato' | 'passato';

export interface AvvisoPrevisto {
  tipo: TipoAvviso;
  /** Per `squadra`: le squadre della partita che si seguono, come le scrive la partita. */
  seguite: string[];
  /** Per `vicino`: dalla posizione salvata, a un decimale. */
  distanzaKm: number | null;
  /** ISO 8601: il giro del server che manda l'avviso. */
  alle: string;
  stato: StatoAvviso;
}

/** Un giorno dell'elenco, con le sue partite in ordine di orario. */
export interface GiornoPartite {
  /** "2026-09-06", per il track. */
  data: string;
  /** "Oggi", "Domani", o null. */
  vicino: string | null;
  /** "domenica 6 settembre" */
  titolo: string;
  partite: ProssimaPartita[];
}

/** Sempre l'ora e il giorno italiani: l'orario è quello del comunicato. */
const DATA = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: 'Europe/Rome',
});
const GIORNO = new Intl.DateTimeFormat('it-IT', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  timeZone: 'Europe/Rome',
});
const ORA = new Intl.DateTimeFormat('it-IT', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Europe/Rome',
});

/** "2026-09-06": il giorno a Roma. */
export function giornoDi(istante: Date): string {
  return DATA.format(istante);
}

/** "15:00" */
export function oraDi(iso: string): string {
  return ORA.format(new Date(iso));
}

/**
 * Le partite per giorno, nell'ordine in cui arrivano (il server le manda
 * già per orario), con "Oggi" e "Domani" in evidenza.
 */
export function perGiorno(partite: ProssimaPartita[], adesso: Date = new Date()): GiornoPartite[] {
  const oggi = giornoDi(adesso);
  // Da mezzogiorno di oggi: con il cambio dell'ora un giorno ha 23 o 25 ore.
  const domani = giornoDi(new Date(Date.parse(`${oggi}T12:00:00Z`) + 86_400_000));
  const giorni: GiornoPartite[] = [];
  for (const partita of partite) {
    const quando = new Date(partita.dataOra);
    const data = giornoDi(quando);
    let giorno = giorni.at(-1);
    if (!giorno || giorno.data !== data) {
      giorno = {
        data,
        vicino: data === oggi ? 'Oggi' : data === domani ? 'Domani' : null,
        titolo: GIORNO.format(quando),
        partite: [],
      };
      giorni.push(giorno);
    }
    giorno.partite.push(partita);
  }
  return giorni;
}

/** "Segui LODIGIANI CALCIO 1972", "A 3,2 km". */
export function motivo(avviso: AvvisoPrevisto): string {
  if (avviso.tipo === 'vicino') {
    const km = (avviso.distanzaKm ?? 0).toLocaleString('it-IT', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return `A ${km} km`;
  }
  return avviso.seguite.length > 0 ? `Segui ${avviso.seguite.join(' e ')}` : 'Squadra seguita';
}

/**
 * Cosa succede con l'avviso, per questo browser:
 *
 * - "Avviso inviato alle 14:00", se il server dice che è partito;
 * - "Avviso alle 14:00", se partirà e qui le notifiche sono accese;
 * - "Avviso delle 14:00 non arrivato", se l'ora è passata senza invio;
 * - "Avviso spento", se qui quell'avviso non è acceso (o le notifiche non
 *   arrivano): la partita resta in elenco perché la squadra si segue.
 */
export function statoAvviso(avviso: AvvisoPrevisto, acceso: boolean): string {
  const ora = oraDi(avviso.alle);
  if (avviso.stato === 'inviato') {
    return `Avviso inviato alle ${ora}`;
  }
  if (!acceso) {
    return 'Avviso spento';
  }
  return avviso.stato === 'passato' ? `Avviso delle ${ora} non arrivato` : `Avviso alle ${ora}`;
}

/** "La Borghesiana "C" · Via di Vermicino 105, Roma" */
export function doveSiGioca(campo: CampoPartita | null): string | null {
  if (!campo) {
    return null;
  }
  const indirizzo = [campo.indirizzo, campo.localita].filter((parte) => !!parte?.trim()).join(', ');
  const parti = [campo.nomeImpianto ?? campo.nomeSocieta, indirizzo].filter((parte) => !!parte?.trim());
  return parti.length > 0 ? parti.join(' · ') : null;
}
