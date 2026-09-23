/**
 * Se su questo browser è entrato di recente qualcuno con il ruolo
 * trovacampo-admin.
 *
 * Serve solo alla home, per mostrare il pulsante dell'importazione. La home è
 * pubblica e di proposito non carica keycloak-js, quindi non può chiedere a
 * Keycloak chi c'è: se lo fa dire dalla pagina di importazione, che il login
 * lo fa davvero. È un'indicazione per l'interfaccia, non una protezione: chi
 * la falsificasse vedrebbe un pulsante che porta al login, e il server
 * risponderebbe comunque 403 senza il ruolo.
 *
 * Sta in un file a parte, senza import di keycloak-js, così la home può
 * leggerla senza trascinarsi dietro la libreria.
 */
const CHIAVE = 'trovacampo.amministratore';

export function amministratoreRicordato(): boolean {
  try {
    return localStorage.getItem(CHIAVE) === 'si';
  } catch {
    // Navigazione privata o archivio bloccato: niente pulsante, si entra
    // scrivendo l'indirizzo.
    return false;
  }
}

export function ricordaAmministratore(amministratore: boolean): void {
  try {
    if (amministratore) {
      localStorage.setItem(CHIAVE, 'si');
    } else {
      localStorage.removeItem(CHIAVE);
    }
  } catch {
    // Come sopra: il pulsante semplicemente non comparirà.
  }
}
