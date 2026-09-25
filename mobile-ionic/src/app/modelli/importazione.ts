/** Una riga del file non importata, con il numero che si vede aprendo il file. */
export interface Scarto {
  riga: number;
  motivo: string;
}

/** Resoconto di POST /api/admin/importazione (vedi EsitoImportazione nel backend). */
export interface EsitoImportazione {
  prova: boolean;
  righeLette: number;
  inserite: number;
  aggiornate: number;
  invariate: number;
  scartate: Scarto[];
  daGeocodificare: number;
  colonneIgnorate: string[];
  /** Righe saltate perché il loro campo è stato eliminato da chi amministra. */
  escluse?: number;
}
