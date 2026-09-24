/* eslint-env serviceworker */

/**
 * Service worker di TrovaCampo: serve solo alle notifiche push.
 *
 * Di proposito non ha un gestore "fetch" e non apre nessuna cache: pagine,
 * bundle e chiamate all'API passano sempre dalla rete come se il worker non
 * ci fosse. Così un rilascio si vede al primo ricaricamento, senza versioni
 * vecchie servite da una cache che nessuno ricorda di svuotare. (Per rendere
 * il sito installabile basta il manifest: i browser di oggi non chiedono più
 * un gestore "fetch".)
 *
 * Il file sta alla radice del sito, non nei bundle: il suo indirizzo non deve
 * cambiare fra un rilascio e l'altro, e nginx lo serve con "no-cache" perché
 * il browser si accorga subito di una versione nuova.
 *
 * Il contenuto delle notifiche lo scrive il backend
 * (PianificatoreNotifiche.Notifica): {titolo, testo, url, tag}.
 */

// Un worker nuovo entra in servizio subito, senza aspettare che si chiudano
// tutte le schede: non ha niente in cache da mettere d'accordo con il vecchio.
self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (evento) => {
  evento.waitUntil(self.clients.claim());
});

self.addEventListener('push', (evento) => {
  let dati = {};
  try {
    dati = evento.data ? evento.data.json() : {};
  } catch {
    // Un messaggio non JSON (una prova dagli strumenti del browser): si
    // mostra il testo così com'è, invece di non mostrare niente. Chrome e
    // Safari vogliono comunque una notifica per ogni push.
    dati = { testo: evento.data ? evento.data.text() : '' };
  }
  const titolo = dati.titolo || 'TrovaCampo';
  evento.waitUntil(
    self.registration.showNotification(titolo, {
      body: dati.testo || '',
      icon: '/icona-192.png',
      badge: '/icona-maskable-192.png',
      lang: 'it',
      // Stesso tag, stessa notifica: se per qualche motivo arrivasse due
      // volte, la seconda sostituisce la prima invece di aggiungersi.
      tag: dati.tag || undefined,
      data: { url: indirizzoSicuro(dati.url) },
    }),
  );
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const indirizzo = new URL(indirizzoSicuro(evento.notification.data && evento.notification.data.url), self.location.origin).href;
  evento.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (finestre) => {
      // Se il sito è già aperto si porta in primo piano quella scheda e la si
      // manda alla pagina giusta, invece di aprirne un'altra.
      for (const finestra of finestre) {
        if (new URL(finestra.url).origin === self.location.origin && 'focus' in finestra) {
          const aperta = await finestra.focus();
          if ('navigate' in aperta) {
            try {
              await aperta.navigate(indirizzo);
            } catch {
              // navigate() vale solo per le schede controllate dal worker: una
              // aperta prima della sua attivazione resta dov'è, ma in primo piano.
            }
          }
          return;
        }
      }
      return self.clients.openWindow(indirizzo);
    }),
  );
});

/** Solo percorsi di questo sito: una notifica non deve poter aprire altro. */
function indirizzoSicuro(url) {
  return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
