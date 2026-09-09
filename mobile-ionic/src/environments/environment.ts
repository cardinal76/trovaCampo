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
};
