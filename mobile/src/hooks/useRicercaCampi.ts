import { useCallback, useEffect, useState } from "react";
import { cercaSocieta } from "../api/societa";
import { Societa } from "../types";

export type StatoRicerca = "caricamento" | "completata" | "errore";

export interface RisultatoRicerca {
  risultati: Societa[];
  stato: StatoRicerca;
  /** Rilancia la stessa ricerca, es. dopo un errore di rete. */
  riprova: () => void;
}

/**
 * Esegue la ricerca dei campi per il termine indicato e la ripete a ogni
 * cambio di termine. Le risposte delle ricerche superate vengono scartate,
 * così una richiesta lenta non sovrascrive i risultati di quella successiva.
 */
export function useRicercaCampi(termine: string): RisultatoRicerca {
  const [risultati, setRisultati] = useState<Societa[]>([]);
  const [stato, setStato] = useState<StatoRicerca>("caricamento");
  const [tentativo, setTentativo] = useState(0);

  const riprova = useCallback(() => setTentativo((precedente) => precedente + 1), []);

  useEffect(() => {
    const query = termine.trim();

    if (query.length === 0) {
      setRisultati([]);
      setStato("completata");
      return;
    }

    let attivo = true;
    setStato("caricamento");

    cercaSocieta(query)
      .then((dati) => {
        if (attivo) {
          setRisultati(dati);
          setStato("completata");
        }
      })
      .catch(() => {
        if (attivo) {
          setRisultati([]);
          setStato("errore");
        }
      });

    return () => {
      attivo = false;
    };
  }, [termine, tentativo]);

  return { risultati, stato, riprova };
}
