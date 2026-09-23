/**
 * Se su questo browser è entrato di recente qualcuno con il ruolo
 * trovacampo-admin.
 *
 * Serve al menu utente e alla scheda, per mostrare le funzioni di
 * amministrazione. Le pagine pubbliche di proposito non caricano keycloak-js,
 * quindi non possono chiedere a Keycloak chi c'è: se lo fanno dire dalle
 * pagine che il login lo fanno davvero. È un'indicazione per l'interfaccia, non una protezione: chi
 * la falsificasse vedrebbe un pulsante che porta al login, e il server
 * risponderebbe comunque 403 senza il ruolo.
 *
 * Sta in un file a parte, senza import di keycloak-js, così le pagine
 * pubbliche possono leggerla senza trascinarsi dietro la libreria.
 */
const CHIAVE = 'trovacampo.amministratore';
const CHIAVE_NOME = 'trovacampo.amministratore.nome';

export function amministratoreRicordato(): boolean {
  try {
    return localStorage.getItem(CHIAVE) === 'si';
  } catch {
    // Navigazione privata o archivio bloccato: niente pulsante, si entra
    // scrivendo l'indirizzo.
    return false;
  }
}

/** Il nome da mostrare nel menu utente, accanto a "Entrato come". */
export function nomeRicordato(): string | null {
  try {
    return amministratoreRicordato() ? localStorage.getItem(CHIAVE_NOME) : null;
  } catch {
    return null;
  }
}

export function ricordaAmministratore(amministratore: boolean, nome?: string | null): void {
  try {
    if (amministratore) {
      localStorage.setItem(CHIAVE, 'si');
      if (nome) {
        localStorage.setItem(CHIAVE_NOME, nome);
      }
    } else {
      localStorage.removeItem(CHIAVE);
      localStorage.removeItem(CHIAVE_NOME);
    }
  } catch {
    // Come sopra: il pulsante semplicemente non comparirà.
  }
}
