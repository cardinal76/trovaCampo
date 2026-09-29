/**
 * Una squadra della società, con il campionato a cui partecipa: arriva
 * dall'anagrafica di presenze, letta dai Comunicati Ufficiali (vedi
 * SquadreSocieta.Squadra nel backend).
 */
export interface Squadra {
  campionato: string;
  /** "Regionali" per il Comitato, il nome della delegazione per i provinciali. */
  ente?: string;
  stagione: string;
  /** Assente finché i gironi non escono. */
  girone?: string;
  /** Vuota per la prima squadra, "B" per la seconda. */
  squadra: string;
  fuoriClassifica: boolean;
  /** Il campo dove gioca in casa, se un programma gare l'ha detto. */
  campo?: string;
  /**
   * Come la riconoscono le notifiche di chi la segue (ChiaveSquadra nel
   * backend): società di presenze, campionato, ente e lettera. Manca da un
   * backend vecchio, e allora la squadra non si può seguire.
   */
  chiave?: string;
  /**
   * A quale Comunicato Ufficiale sono aggiornati i risultati del girone. Lo
   * manda presenze solo mentre il portale LND non gli risponde: allora i
   * risultati arrivano soltanto col comunicato del giovedì o del venerdì, e
   * nel fine settimana restano fermi. Manca con il portale disponibile, e da
   * un presenze che non lo manda ancora: in entrambi i casi nessun avviso.
   */
  risultatiDaComunicato?: RisultatiDaComunicato | null;
}

export interface RisultatiDaComunicato {
  /** Assente se nessun comunicato ha ancora portato risultati nel girone. */
  numero?: number | null;
  /** "2026-09-24" */
  data?: string | null;
  /** Chi l'ha pubblicato: i numeri ripartono per ogni ente. */
  ente?: string | null;
  portaleNonDisponibileDal?: string | null;
}

/** "Risultati aggiornati al Comunicato Ufficiale n. 55 del 24/09"; null se non c'è niente da dire. */
export function aggiornamentoRisultati(squadra: Squadra): string | null {
  const avviso = squadra.risultatiDaComunicato;
  if (!avviso || avviso.numero === undefined || avviso.numero === null) {
    return null;
  }
  const [, mese, giorno] = (avviso.data ?? '').split('-');
  const quando = giorno && mese ? ` del ${giorno}/${mese}` : '';
  return `Risultati aggiornati al Comunicato Ufficiale n. ${avviso.numero}${quando}`;
}

/**
 * Se presenze avvisa che il portale LND non risponde, per almeno una
 * squadra: la scheda lo dice una volta, in cima ai campionati.
 */
export function portaleNonRaggiungibile(squadre: Squadra[]): boolean {
  return squadre.some((squadra) => Boolean(squadra.risultatiDaComunicato));
}

/** "Girone A · Regionali · squadra B, fuori classifica" */
export function dettaglioSquadra(squadra: Squadra): string {
  const parti: string[] = [];
  parti.push(squadra.girone ? `Girone ${squadra.girone}` : 'Gironi non ancora pubblicati');
  if (squadra.ente) {
    parti.push(squadra.ente);
  }
  if (squadra.squadra) {
    parti.push(
      squadra.fuoriClassifica ? `squadra ${squadra.squadra}, fuori classifica` : `squadra ${squadra.squadra}`,
    );
  } else if (squadra.fuoriClassifica) {
    parti.push('fuori classifica');
  }
  return parti.join(' · ');
}
