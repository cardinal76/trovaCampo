import { archivioLocale } from '../archivio-locale';

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
  // Navigazione privata o archivio bloccato: niente pulsante, si entra
  // scrivendo l'indirizzo.
  return archivioLocale.leggi(CHIAVE) === 'si';
}

/** Il nome da mostrare nel menu utente, accanto a "Entrato come". */
export function nomeRicordato(): string | null {
  return amministratoreRicordato() ? archivioLocale.leggi(CHIAVE_NOME) : null;
}

export function ricordaAmministratore(amministratore: boolean, nome?: string | null): void {
  // Con l'archivio bloccato il pulsante semplicemente non comparirà.
  if (amministratore) {
    archivioLocale.scrivi(CHIAVE, 'si');
    if (nome) {
      archivioLocale.scrivi(CHIAVE_NOME, nome);
    }
  } else {
    archivioLocale.togli(CHIAVE);
    archivioLocale.togli(CHIAVE_NOME);
  }
}
