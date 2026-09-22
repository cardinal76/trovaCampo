/**
 * Build di produzione (`ng build`, configurazione predefinita).
 *
 * Indirizzo assoluto e non relativo: la stessa build serve sia il sito web,
 * dove l'API sta sullo stesso dominio, sia l'app Capacitor, dove l'origine è
 * `capacitor://localhost` e un percorso relativo finirebbe sul telefono.
 */
export const environment = {
  production: true,
  apiUrl: 'https://trovacampo.footballer.it',
};
