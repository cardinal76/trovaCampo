/**
 * Build dell'ambiente di test (`ng build --configuration test`), quello di
 * server2. Si apre da `http://localhost:8088` con un tunnel SSH, e l'API sta
 * sulla stessa origine: nginx del container la gira al backend di test.
 *
 * Il login e' il Keycloak di produzione (stesso realm, stesso client): per
 * questo l'indirizzo del tunnel deve essere fra i redirect URI di
 * `trovacampo-frontend` (DEPLOY.md, "Ambiente di test").
 */
export const environment = {
  production: true,
  apiUrl: 'http://localhost:8088',
  keycloak: {
    url: 'https://presenze.footballer.it/auth',
    realm: 'presenze',
    clientId: 'trovacampo-frontend',
  },
};
