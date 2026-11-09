/**
 * Il {@code localStorage} (o il {@code sessionStorage}) senza sorprese.
 *
 * In navigazione privata, con i dati del sito bloccati o a quota piena, il
 * browser non risponde con un valore ma lancia un'eccezione, e in certi casi
 * lo fa già all'accesso a {@code localStorage}. Nessuna scelta di chi usa
 * l'app vale un errore a schermo: se l'archivio non c'è, si legge «niente» e
 * le scelte valgono finché la pagina resta aperta.
 *
 * Il {@code Storage} si cerca a ogni chiamata e non una volta sola: così
 * vale quello che c'è in quel momento (e i test possono bloccarlo).
 */
export class ArchivioLocale {
  constructor(private readonly dove: () => Storage) {}

  /** Il testo salvato, o null se non c'è o l'archivio è bloccato. */
  leggi(chiave: string): string | null {
    try {
      return this.dove().getItem(chiave);
    } catch {
      return null;
    }
  }

  /** Falso se l'archivio è bloccato: chi chiama di solito non deve fare niente. */
  scrivi(chiave: string, valore: string): boolean {
    try {
      this.dove().setItem(chiave, valore);
      return true;
    } catch {
      return false;
    }
  }

  togli(chiave: string): void {
    try {
      this.dove().removeItem(chiave);
    } catch {
      // Archivio bloccato: non c'è niente da togliere.
    }
  }

  /**
   * Il valore salvato come JSON, o null se manca, non è JSON o l'archivio è
   * bloccato. Quello che c'è l'ha scritto una versione dell'app, forse vecchia,
   * forse toccata a mano: chi legge ne controlla la forma.
   */
  leggiJson(chiave: string): unknown {
    const testo = this.leggi(chiave);
    if (testo === null) {
      return null;
    }
    try {
      return JSON.parse(testo) as unknown;
    } catch {
      return null;
    }
  }

  scriviJson(chiave: string, valore: unknown): boolean {
    return this.scrivi(chiave, JSON.stringify(valore));
  }
}

/** Le scelte che restano fra una visita e l'altra. */
export const archivioLocale = new ArchivioLocale(() => localStorage);

/** Quello che serve solo finché la scheda del browser resta aperta. */
export const archivioSessione = new ArchivioLocale(() => sessionStorage);
