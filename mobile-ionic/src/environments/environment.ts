/**
 * Indirizzo dell'API TrovaCampo (backend Spring Boot in ../backend-java
 * oppure quello Node in ../backend: espongono lo stesso contratto).
 *
 * Su dispositivo o emulatore "localhost" è il telefono stesso: va messo
 * l'IP della macchina che esegue il backend, es. http://192.168.1.10:3000.
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:3000',
  /**
   * Il Keycloak dello stack di sviluppo di presenze: sulla 8081, senza /auth.
   * Serve solo alla pagina /admin/importazione, il resto dell'app è pubblico.
   */
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'presenze',
    clientId: 'trovacampo-frontend',
  },
};
