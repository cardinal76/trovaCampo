import * as L from 'leaflet';
import {
  Societa,
  SocietaGeolocalizzata,
  indirizzoCompleto,
  nomeCompleto,
  testoSicuro,
} from '../modelli/societa';
import {
  PartitePerCampo,
  campionatoPartita,
  partiteDelCampo,
  punteggioLive,
  quandoPartita,
  squadrePartita,
} from '../modelli/partita';
import { linkPercorso } from '../modelli/percorso';
import { Posizione, testoDistanza } from '../modelli/vicini';

/**
 * Il contenuto HTML dei popup della mappa e il loro collegamento al DOM.
 * Funzioni senza stato di pagina: tutto quello che serve arriva dai
 * parametri, così si provano senza montare Leaflet.
 */

/** Cosa serve a un popup per scriversi: niente che sia della pagina. */
export interface ContestoPopup {
  /** Distanza in km dalla posizione di chi guarda, se è uno dei vicini. */
  distanza?: number;
  partite: PartitePerCampo;
  /** La posizione di chi guarda, se già nota. */
  posizione: Posizione | null;
  /** Segnaposto del campo «Partenza»: la zona di chi guarda. */
  suggerimento: string;
}

/** Quel che serve a {@link collegaPercorso} per parlare con la pagina. */
export interface DipendenzePercorso {
  mappa?: L.Map;
  /** Esegue fuori dalla zona di Angular (i movimenti della mappa non devono innescare cambi). */
  fuoriZona: (azione: () => void) => void;
  posizioneNota: () => Posizione | null;
  chiediPosizione: () => Promise<Posizione | null>;
  erroreRichiesta: () => string | null;
  ricordaPosizione: (posizione: Posizione) => void;
}

/**
 * Un popup si allunga verso l'alto: quando una riga nuova (la nota del
 * percorso, un errore) lo fa uscire dalla mappa, sul telefono finirebbe
 * sotto i filtri con la sua X. Si sposta la mappa quel tanto che basta.
 * Non con popup.update(), che riscriverebbe il contenuto e staccherebbe
 * gli eventi.
 */
export function tieniDentro(elemento: HTMLElement, mappa: L.Map): void {
  const popup = elemento.closest<HTMLElement>('.leaflet-popup') ?? elemento;
  const margine = 10;
  const sopra =
    popup.getBoundingClientRect().top - mappa.getContainer().getBoundingClientRect().top - margine;
  if (sopra < 0) {
    mappa.panBy([0, sopra], { animate: true });
  }
}

/** Il popup di un campo: nome, indirizzo, partite, percorso e link alla scheda. */
export function popupCampo(campo: SocietaGeolocalizzata, contesto: ContestoPopup): string {
  const distanza = contesto.distanza;
  const riga =
    distanza === undefined
      ? ''
      : `<em>A ${testoDistanza(distanza)} da te, in linea d'aria</em><br />`;
  return `
    <strong>${testoSicuro(nomeCompleto(campo))}</strong><br />
    ${riga}
    ${testoSicuro(campo.nomeImpianto)}<br />
    ${testoSicuro(indirizzoCompleto(campo))}<br />
    ${partiteNelPopup(campo, contesto.partite)}
    ${percorsoNelPopup(campo, contesto.posizione, contesto.suggerimento)}
    <button type="button" class="collegamento-scheda" data-societa="${testoSicuro(campo.id)}">
      Vedi scheda società ›
    </button>
  `;
}

/**
 * "Partenza" e "Percorso": lo stesso blocco della scheda, scritto a mano
 * perché il popup è HTML di Leaflet e non un template. Il link è già
 * pronto per il campo vuoto; lo aggiorna {@link collegaPercorso}.
 *
 * La posizione qui non si chiede: aprire un popup è toccare un campo per
 * curiosità, e un permesso a ogni tocco sarebbe un fastidio. La chiede il
 * pulsante accanto alla partenza; se non la si chiede, il campo vuoto
 * lascia che sia Google a partire dalla posizione del dispositivo.
 */
function percorsoNelPopup(
  campo: SocietaGeolocalizzata,
  posizione: Posizione | null,
  suggerimento: string,
): string {
  const link = linkPercorso(campo, '', posizione);
  const nota = posizione ? 'Parti dalla tua posizione attuale.' : '';
  return `
    <form class="percorso-popup" data-percorso data-lat="${campo.lat}" data-lng="${campo.lng}">
      <label>
        <span class="etichetta">Partenza</span>
        <span class="riga-partenza">
          <input
            type="text"
            name="partenza"
            placeholder="${testoSicuro(suggerimento)}"
            autocomplete="street-address"
            enterkeyhint="go"
          />
          <button type="button" class="usa-posizione" data-usa-posizione
            aria-label="Parti dalla mia posizione" title="Parti dalla mia posizione">
            <ion-icon name="locate" aria-hidden="true"></ion-icon>
          </button>
        </span>
      </label>
      <p class="nota-percorso" aria-live="polite">${nota}</p>
      <a class="apri-percorso" href="${testoSicuro(link)}" target="_blank" rel="noopener">
        <ion-icon name="navigate-outline" aria-hidden="true"></ion-icon>
        Percorso
      </a>
    </form>`;
}

/** Le prime partite della settimana su questo campo, o niente. */
export function partiteNelPopup(campo: Societa, tutte: PartitePerCampo): string {
  const partite = partiteDelCampo(tutte, campo);
  if (partite.length === 0) {
    return '';
  }
  const righe = partite
    .map(
      (partita) => `
        <li>
          ${partita.live ? `<span class="live">LIVE ${testoSicuro(punteggioLive(partita) ?? '')}</span>` : ''}
          <span class="quando">${testoSicuro(quandoPartita(partita))}</span>
          ${testoSicuro(squadrePartita(partita))}
          <small>${testoSicuro(campionatoPartita(partita))}</small>
        </li>`,
    )
    .join('');
  return `<ul class="partite-popup" aria-label="Prossime partite">${righe}</ul>`;
}

/**
 * Dà vita al blocco del percorso di un popup appena aperto: il link segue
 * quello che si scrive, il pulsante chiede la posizione, e Invio apre il
 * percorso come il link.
 */
export function collegaPercorso(modulo: HTMLFormElement, dip: DipendenzePercorso): void {
  const { mappa } = dip;
  const destinazione = { lat: Number(modulo.dataset['lat']), lng: Number(modulo.dataset['lng']) };
  const campo = modulo.querySelector<HTMLInputElement>('input[name="partenza"]');
  const link = modulo.querySelector<HTMLAnchorElement>('.apri-percorso');
  const nota = modulo.querySelector<HTMLElement>('.nota-percorso');
  const usaPosizione = modulo.querySelector<HTMLButtonElement>('[data-usa-posizione]');
  if (!campo || !link || !nota || !usaPosizione) {
    return;
  }
  const scriviNota = (testo: string, errore = false) => {
    nota.textContent = testo;
    nota.classList.toggle('errore', errore);
    if (mappa && modulo.isConnected) {
      dip.fuoriZona(() => tieniDentro(modulo, mappa));
    }
  };
  const aggiorna = () => {
    link.href = linkPercorso(destinazione, campo.value, dip.posizioneNota());
    const dallaPosizione = campo.value.trim() === '' && dip.posizioneNota() !== null;
    scriviNota(dallaPosizione ? 'Parti dalla tua posizione attuale.' : '');
  };
  campo.addEventListener('input', aggiorna);
  // Invio sulla tastiera del telefono ("Vai") apre il percorso come il
  // link: dentro il gesto dell'utente, quindi in una nuova scheda e senza
  // che il blocco dei popup lo fermi.
  modulo.addEventListener('submit', (evento) => {
    evento.preventDefault();
    aggiorna();
    link.click();
  });
  usaPosizione.addEventListener('click', async () => {
    campo.value = '';
    usaPosizione.disabled = true;
    scriviNota('Cerco dove sei…');
    const posizione = await dip.chiediPosizione();
    usaPosizione.disabled = false;
    if (posizione) {
      dip.ricordaPosizione(posizione);
    }
    aggiorna();
    // Nel popup basta il messaggio, che già dice cosa fare: il riquadro con
    // tutti i passi non ci starebbe. Il campo resta libero per scrivere
    // l'indirizzo, e il link parte comunque dalla posizione del dispositivo.
    const errore = dip.erroreRichiesta();
    if (errore) {
      scriviNota(errore, true);
    }
  });
}
