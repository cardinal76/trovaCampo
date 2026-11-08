import { APP_GENERICA, Piattaforma } from '../sonde-browser';

/**
 * Un riquadro "cosa blocca e come sbloccarlo": titolo, due righe di
 * spiegazione e i passi, pensati per il dispositivo che si ha in mano.
 */
export interface Istruzioni {
  /** Il caso, per i test e per lo stile (data-caso). */
  caso: string;
  icona: string;
  titolo: string;
  spiegazione: string;
  passi: string[];
  /** Una riga dopo i passi, per il caso meno probabile. */
  nota?: string;
  /** "Riprova" serve solo dove qualcosa si può sbloccare da qui. */
  riprova: boolean;
}

/** Il nome del browser nei passi: "Impostazioni → App → Chrome". */
export function nomeBrowser(piattaforma: Piattaforma): string {
  switch (piattaforma.browser) {
    case 'chrome':
      return 'Chrome';
    case 'samsung':
      return 'Samsung Internet';
    case 'firefox':
      return 'Firefox';
    case 'edge':
      return 'Edge';
    case 'safari':
      return 'Safari';
    default:
      return 'il browser';
  }
}

/** Il browser vero in cui riaprire il sito, uscendo da quello di un'app. */
export function browserVero(piattaforma: Piattaforma): string {
  return piattaforma.sistema === 'ios' ? 'Safari' : 'Chrome';
}

/** Come uscire dal browser interno di un'app: vale per notifiche e posizione. */
export function passiAppInterna(piattaforma: Piattaforma): string[] {
  const browser = browserVero(piattaforma);
  return [
    piattaforma.sistema === 'ios'
      ? 'Tocca i tre puntini (…) o l’icona Condividi, in alto o in basso nella pagina.'
      : 'Tocca i tre puntini (⋮) in alto a destra.',
    `Scegli «Apri in ${browser}» (o «Apri nel browser»).`,
    piattaforma.sistema === 'ios'
      ? 'In Safari tocca Condividi → «Aggiungi alla schermata Home», e apri TrovaCampo da lì.'
      : `In ${browser} apri il menu → Notifiche, e accendi l’avviso da lì.`,
  ];
}

export function nomeApp(piattaforma: Piattaforma): string {
  return piattaforma.appInterna === APP_GENERICA
    ? 'un’altra app'
    : (piattaforma.appInterna ?? 'un’altra app');
}
