import { Societa } from "./types";

/**
 * Formattazioni condivise fra mappa, lista dei risultati e scheda società,
 * così che un campo inserito a mano (senza sigla, località e provincia)
 * non venga mostrato come "Via Roma 1,  ()".
 */

export function nomeCompleto(societa: Societa): string {
  return [societa.siglaSocieta, societa.nomeSocieta].filter(Boolean).join(" ").trim();
}

export function indirizzoCompleto(societa: Societa): string {
  const localita = societa.localitaImpianto ? `, ${societa.localitaImpianto}` : "";
  const provincia = societa.provinciaImpianto ? ` (${societa.provinciaImpianto})` : "";
  return `${societa.indirizzoImpianto}${localita}${provincia}`;
}
