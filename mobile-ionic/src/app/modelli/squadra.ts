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
