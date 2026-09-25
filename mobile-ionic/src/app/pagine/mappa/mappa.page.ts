import {
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  checkmarkCircle,
  close,
  locate,
  locationOutline,
  navigateOutline,
} from 'ionicons/icons';
import * as L from 'leaflet';
import {
  Societa,
  SocietaGeolocalizzata,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
  testoSicuro,
} from '../../modelli/societa';
import {
  GIORNI_PARTITE_MAPPA,
  PartitePerCampo,
  campionatoPartita,
  partiteDelCampo,
  quandoPartita,
  squadrePartita,
} from '../../modelli/partita';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
import { RiquadroDiagnosiComponent } from '../../componenti/riquadro-diagnosi/riquadro-diagnosi.component';
import { DiagnosiService } from '../../servizi/diagnosi.service';
import {
  nellaProvincia,
  opzioniProvincia,
  provinciaValida,
  testoProvincia,
} from '../../modelli/provincia';
import { NumeroViciniService } from '../../servizi/numero-vicini.service';
import { RichiestaPosizione } from '../../servizi/richiesta-posizione';
import { linkPercorso, suggerimentoPartenza } from '../../modelli/percorso';
import { CampiCambiatiService } from '../../servizi/campi-cambiati.service';
import { ProvinciaSceltaService } from '../../servizi/provincia-scelta.service';
import { SocietaService } from '../../servizi/societa.service';
import {
  ZOOM_ICONE,
  aggiornaNomiCampi,
  campoSuCanvas,
  iconaCampo,
  immagineCampo,
} from '../../mappa/icona-campo';
import {
  CampoVicino,
  Posizione,
  SCELTE_NUMERO_VICINI,
  piuVicini,
  testoDistanza,
} from '../../modelli/vicini';

/** L'Italia intera, finché non ci sono campi da inquadrare. */

const ITALIA = L.latLngBounds([36.6, 6.6], [47.1, 18.5]);

/**
 * Un popup si allunga verso l'alto: quando una riga nuova (la nota del
 * percorso, un errore) lo fa uscire dalla mappa, sul telefono finirebbe
 * sotto i filtri con la sua X. Si sposta la mappa quel tanto che basta.
 * Non con popup.update(), che riscriverebbe il contenuto e staccherebbe
 * gli eventi.
 */
function tieniDentro(elemento: HTMLElement, mappa: L.Map): void {
  const popup = elemento.closest<HTMLElement>('.leaflet-popup') ?? elemento;
  const margine = 10;
  const sopra =
    popup.getBoundingClientRect().top - mappa.getContainer().getBoundingClientRect().top - margine;
  if (sopra < 0) {
    mappa.panBy([0, sopra], { animate: true });
  }
}

/**
 * Tutti i campi con una posizione, su una mappa sola.
 *
 * Ogni campo ha l'icona di un campo da calcio a tutti gli zoom. Da lontano è
 * disegnata sul canvas e non come segnaposto HTML: con qualche migliaio di
 * campi, un elemento del DOM per ciascuno renderebbe lento ogni spostamento
 * della mappa, mentre il canvas li ridisegna tutti in un colpo.
 *
 * Da vicino (da ZOOM_ICONE) la stessa icona diventa un elemento HTML, solo
 * per i campi dentro la porzione visibile: a quello zoom sono pochi, e da
 * ZOOM_NOMI in su l'icona mostra anche il nome della società.
 *
 * Il filtro per provincia è lo stesso dell'elenco (stessa scelta, vedi
 * {@link ProvinciaSceltaService}): cambia i campi sulla mappa e la vista si
 * stringe su quelli rimasti, senza ricreare la mappa.
 *
 * "Vicino a me" chiede la posizione e lascia sulla mappa solo gli N campi
 * più vicini (N scelto da chi guarda e ricordato), cercati fra tutti i campi
 * con una posizione, di qualunque provincia: chi sta al confine fra Roma e
 * Latina vuole il campo più vicino, non quello della sua provincia. Per
 * questo scegliere una provincia chiude la ricerca dei vicini, e viceversa
 * la ricerca non cambia la provincia scelta.
 *
 * Le prossime partite di ogni campo arrivano a parte, con una chiamata sola
 * per tutta la mappa: finché non ci sono, o se presenze non risponde, la
 * mappa funziona lo stesso. Il popup di un campo mostra le prime della
 * settimana, e "Solo campi con partite" lascia quelli dove si gioca nei
 * prossimi giorni. Quest'ultimo vale anche per "Vicino a me", a differenza
 * della provincia: chi lo accende cerca una partita da andare a vedere, e i
 * campi più vicini dove non si gioca non gli servono.
 */
@Component({
  selector: 'pagina-mappa',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    RiquadroDiagnosiComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './mappa.page.html',
  styleUrl: './mappa.page.scss',
})
export class MappaPage implements OnDestroy {
  private readonly service = inject(SocietaService);
  private readonly router = inject(Router);
  private readonly zona = inject(NgZone);
  private readonly provinciaScelta = inject(ProvinciaSceltaService);
  private readonly numeroVicini = inject(NumeroViciniService);
  private readonly diagnosi = inject(DiagnosiService);
  private readonly cambiati = inject(CampiCambiatiService);
  /** La versione dell'archivio dei campi scaricati: se al rientro è salita, si ricarica. */
  private versioneCaricata = -1;

  private readonly contenitore = viewChild<ElementRef<HTMLElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private sulCanvas: L.LayerGroup | null = null;
  private icone: L.LayerGroup | null = null;
  private readonly segnaposto = new Map<string, L.Marker>();
  /** I campi sulla mappa adesso, quelli che guarda {@link aggiornaSegnaposto}. */
  private mostrati: SocietaGeolocalizzata[] = [];
  /** Le distanze dei campi vicini, per i popup; vuota fuori da "Vicino a me". */
  private distanze = new Map<string, number>();
  /** Il segnaposto di chi guarda e gli anelli attorno ai vicini. */
  private livelloVicini: L.LayerGroup | null = null;
  private distrutta = false;

  readonly stato = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly campi = signal<Societa[]>([]);

  /** Le prime partite della settimana, per id del campo in presenze; vuoto finché non arrivano. */
  readonly partite = signal<PartitePerCampo>({});
  readonly giorniPartite = GIORNI_PARTITE_MAPPA;
  /** L'interruttore si mostra solo se c'è almeno una partita: senza, spegnerebbe tutta la mappa. */
  readonly ciSonoPartite = computed(() => Object.keys(this.partite()).length > 0);
  readonly soloConPartite = signal(false);
  /** Vero se il campo passa il filtro delle partite (sempre, a filtro spento). */
  private readonly passaFiltroPartite = computed(() => {
    const partite = this.partite();
    const attivo = this.soloConPartite() && this.ciSonoPartite();
    return (campo: Societa) => !attivo || partiteDelCampo(partite, campo).length > 0;
  });

  /** Le province dei campi scaricati, con "Tutte" in testa. */
  readonly opzioniProvincia = computed(() => opzioniProvincia(this.campi()));
  readonly testoProvincia = computed(() => testoProvincia(this.provincia(), this.opzioniProvincia()));
  readonly provincia = computed(() =>
    provinciaValida(this.provinciaScelta.scelta(), this.opzioniProvincia()),
  );

  /** I campi della provincia scelta: anche il conteggio "senza posizione" parla di loro. */
  readonly campiNellaProvincia = computed(() => {
    const provincia = this.provincia();
    return this.campi().filter((campo) => nellaProvincia(campo, provincia));
  });
  readonly geolocalizzati = computed(() =>
    this.campiNellaProvincia().filter(haCoordinate).filter(this.passaFiltroPartite()),
  );
  /**
   * Il conto dei campi sulla mappa e di quelli ancora senza posizione è un
   * dato di lavoro per chi amministra (il link porta all'elenco da
   * correggere): al pubblico direbbe solo che mancano dei campi.
   */
  readonly amministratore = signal(amministratoreRicordato());
  readonly senzaPosizione = computed(
    () => this.campiNellaProvincia().filter((campo) => !haCoordinate(campo)).length,
  );

  readonly scelteNumeroVicini = SCELTE_NUMERO_VICINI;
  readonly numero = this.numeroVicini.numero;
  /** Dove si trova chi guarda; null finché non ha chiesto "Vicino a me". */
  readonly posizione = signal<Posizione | null>(null);
  /** La richiesta di "Vicino a me": in corso, errore e riquadro "come sbloccarla". */
  private readonly richiestaVicini = new RichiestaPosizione();
  readonly cercoPosizione = this.richiestaVicini.inCorso;
  readonly erroreVicini = this.richiestaVicini.errore;
  readonly riquadroVicini = this.richiestaVicini.riquadro;
  /**
   * La richiesta del percorso, dal popup di un campo: a parte, perché un
   * errore lì non deve aprire il riquadro di "Vicino a me".
   */
  private readonly richiestaPercorso = new RichiestaPosizione();
  /** L'ultima posizione avuta dal popup: vale per tutti i popup che si aprono dopo. */
  private posizionePercorso: Posizione | null = null;

  /** Gli N campi più vicini, fra tutti quelli con una posizione e senza guardare la provincia. */
  readonly vicini = computed<CampoVicino[]>(() => {
    const posizione = this.posizione();
    const candidati = this.campi().filter(haCoordinate).filter(this.passaFiltroPartite());
    return posizione ? piuVicini(candidati, posizione, this.numero()) : [];
  });

  /**
   * La riga sopra la mappa: sempre con "Vicino a me" o il filtro delle
   * partite, che raccontano cosa si sta guardando; il conto nudo dei campi
   * solo a chi amministra.
   */
  readonly conRiepilogo = computed(
    () =>
      this.posizione() !== null || (this.soloConPartite() && this.ciSonoPartite()) || this.amministratore(),
  );

  /** A ogni ingresso: tornando dal login (o dopo l'uscita) il riepilogo si adegua. */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
    // Di ritorno da una scheda eliminata, modificata o creata: il suo
    // segnaposto deve sparire o spostarsi.
    if (this.cambiati.versione() !== this.versioneCaricata) {
      this.carica();
    }
  }

  constructor() {
    addIcons({ calendarOutline, checkmarkCircle, close, locate, locationOutline, navigateOutline });
    this.carica();

    // Il contenitore esiste solo dopo che i dati sono arrivati: la mappa si
    // crea quando ci sono entrambi, e dopo, a ogni cambio di provincia, si
    // rifanno solo i segnaposto.
    effect(() => {
      const contenitore = this.contenitore()?.nativeElement;
      if (contenitore && this.stato() === 'pronto') {
        const posizione = this.posizione();
        const vicini = this.vicini();
        const campi = posizione ? vicini.map((v) => v.campo) : this.geolocalizzati();
        // L'icona per il canvas si carica una volta sola, di solito subito.
        immagineCampo().then((immagine) => {
          // La posizione può arrivare quando la pagina è già stata lasciata:
          // una mappa creata allora non verrebbe più tolta.
          if (this.distrutta || this.contenitore()?.nativeElement !== contenitore) {
            return;
          }
          if (this.mappa?.getContainer() !== contenitore) {
            this.creaMappa(contenitore);
          }
          this.mostra(campi, immagine, posizione ? { posizione, vicini } : null);
        });
      }
    });
  }

  cambiaProvincia(provincia: string): void {
    // Chi sceglie una provincia vuole vedere quella: la ricerca dei vicini,
    // che ignora le province, si chiude.
    this.tornaAllaMappa();
    this.provinciaScelta.scegli(provincia);
  }

  cambiaSoloConPartite(attivo: boolean): void {
    this.soloConPartite.set(attivo);
  }

  cambiaNumeroVicini(numero: number): void {
    this.numeroVicini.scegli(numero);
  }

  /** Chiede la posizione e, se arriva, mostra i campi più vicini. */
  async vicinoAMe(): Promise<void> {
    const posizione = await this.richiestaVicini.chiedi();
    if (posizione) {
      this.posizione.set(posizione);
    }
  }

  /** Di nuovo tutti i campi della provincia scelta. */
  tornaAllaMappa(): void {
    this.posizione.set(null);
    this.erroreVicini.set(null);
  }

  testoDistanza(km: number): string {
    return testoDistanza(km);
  }

  nomeCompleto(campo: Societa): string {
    return nomeCompleto(campo);
  }

  /**
   * Scarica i campi. Se la mappa c'è già li sostituisce senza spinner: la
   * mappa resta dov'era (stessa inquadratura, stessa provincia, stessi
   * vicini) e cambiano solo i segnaposto. Se il nuovo scaricamento fallisce
   * restano quelli di prima, e si riprova al prossimo rientro.
   */
  carica(): void {
    const giaPronta = this.stato() === 'pronto';
    this.versioneCaricata = this.cambiati.versione();
    if (!giaPronta) {
      this.stato.set('caricamento');
    }
    this.service.tutti().subscribe({
      next: (campi) => {
        this.campi.set(campi);
        this.stato.set('pronto');
      },
      error: () => {
        this.versioneCaricata = -1;
        if (!giaPronta) {
          this.stato.set('errore');
        }
      },
    });
    // A parte, e senza toccare lo stato della pagina: le partite sono un di
    // più, la mappa non le aspetta.
    this.service.partiteSuiCampi().subscribe({
      next: (partite) => this.partite.set(partite ?? {}),
      error: () => this.partite.set({}),
    });
  }

  ngOnDestroy(): void {
    this.distrutta = true;
    this.livelloVicini = null;
    this.mappa?.remove();
    this.mappa = null;
    this.segnaposto.clear();
  }

  private creaMappa(contenitore: HTMLElement): void {
    this.zona.runOutsideAngular(() => {
      this.mappa?.remove();
      const mappa = L.map(contenitore, { preferCanvas: true });
      this.mappa = mappa;
      this.sulCanvas = null;
      this.icone = null;

      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap',
      }).addTo(mappa);

      mappa.on('zoomend moveend', () => this.aggiornaSegnaposto(mappa));

      mappa.on('popupopen', (evento: L.PopupEvent) => {
        const elemento = evento.popup.getElement();
        const bottone = elemento?.querySelector<HTMLButtonElement>('[data-societa]');
        bottone?.addEventListener('click', () => {
          const id = bottone.dataset['societa'];
          if (id) {
            this.zona.run(() => this.router.navigate(['/societa', id]));
          }
        });
        const percorso = elemento?.querySelector<HTMLFormElement>('[data-percorso]');
        if (percorso) {
          this.collegaPercorso(percorso, mappa);
        }
      });

      // Le dimensioni definitive arrivano solo a transizione di pagina finita.
      // Se nel frattempo la pagina è stata lasciata la mappa non c'è più, e
      // Leaflet su una mappa tolta si rompe.
      setTimeout(() => {
        if (this.mappa === mappa) {
          mappa.invalidateSize();
        }
      }, 200);
    });
  }

  /**
   * Mette sulla mappa questi campi al posto di quelli di prima, e porta la
   * vista su di loro.
   */
  private mostra(
    campi: SocietaGeolocalizzata[],
    immagine: HTMLImageElement,
    vicini: { posizione: Posizione; vicini: CampoVicino[] } | null = null,
  ): void {
    const mappa = this.mappa;
    if (!mappa) {
      return;
    }
    this.zona.runOutsideAngular(() => {
      this.sulCanvas?.remove();
      this.icone?.remove();
      this.livelloVicini?.remove();
      this.livelloVicini = null;
      // I popup si creano con le distanze: vanno pronte prima dei segnaposto.
      this.distanze = new Map(vicini?.vicini.map((v) => [v.campo.id, v.distanzaKm]) ?? []);

      const sulCanvas = L.layerGroup();
      for (const campo of campi) {
        this.conDettagli(campoSuCanvas([campo.lat, campo.lng], immagine), campo).addTo(sulCanvas);
      }
      this.sulCanvas = sulCanvas;
      this.icone = L.layerGroup();
      this.segnaposto.clear();
      this.mostrati = campi;

      const punti = campi.map((c) => L.latLng(c.lat, c.lng));
      if (vicini) {
        this.livelloVicini = this.disegnaVicini(vicini.posizione, campi).addTo(mappa);
        // La vista tiene dentro anche chi guarda, non solo i campi.
        punti.push(L.latLng(vicini.posizione.lat, vicini.posizione.lng));
      }
      if (punti.length > 0) {
        mappa.fitBounds(L.latLngBounds(punti), {
          padding: [30, 30],
          maxZoom: 15,
        });
      } else {
        mappa.fitBounds(ITALIA);
      }
      this.aggiornaSegnaposto(mappa);
    });
  }

  /**
   * Un punto blu per chi guarda, come nelle app di navigazione, e un anello
   * arancione attorno a ciascun campo vicino, che resta visibile sia sopra
   * le icone sul canvas sia sopra quelle HTML.
   */
  private disegnaVicini(posizione: Posizione, campi: SocietaGeolocalizzata[]): L.LayerGroup {
    const livello = L.layerGroup();
    // In SVG e non sul canvas dei campi: sono al massimo una decina, e su un
    // livello loro restano sopra le icone, che si ridisegnano a ogni zoom.
    const renderer = L.svg();
    for (const campo of campi) {
      L.circleMarker([campo.lat, campo.lng], {
        renderer,
        radius: 22,
        color: '#e8590c',
        weight: 3,
        fill: false,
        interactive: false,
      }).addTo(livello);
    }
    L.circleMarker([posizione.lat, posizione.lng], {
      renderer,
      radius: 9,
      color: '#ffffff',
      weight: 3,
      fillColor: '#1c7ed6',
      fillOpacity: 1,
    })
      .bindTooltip('Sei qui')
      .addTo(livello);
    return livello;
  }

  /**
   * Icone sul canvas da lontano, icone HTML da vicino. Queste ultime si
   * creano solo per i campi visibili (con un po' di margine) e si riusano
   * tra uno spostamento e l'altro.
   */
  private aggiornaSegnaposto(mappa: L.Map): void {
    const sulCanvas = this.sulCanvas;
    const icone = this.icone;
    if (!sulCanvas || !icone) {
      return;
    }

    aggiornaNomiCampi(mappa);
    if (mappa.getZoom() < ZOOM_ICONE) {
      icone.remove();
      sulCanvas.addTo(mappa);
      return;
    }
    sulCanvas.remove();
    icone.addTo(mappa);

    const visibili = mappa.getBounds().pad(0.3);
    for (const campo of this.mostrati) {
      const dentro = visibili.contains([campo.lat, campo.lng]);
      let segnaposto = this.segnaposto.get(campo.id);
      if (dentro && !segnaposto) {
        segnaposto = this.conDettagli(
          L.marker([campo.lat, campo.lng], { icon: iconaCampo(campo) }),
          campo,
        );
        this.segnaposto.set(campo.id, segnaposto);
      }
      if (!segnaposto) {
        continue;
      }
      if (dentro) {
        icone.addLayer(segnaposto);
      } else if (!segnaposto.isPopupOpen()) {
        icone.removeLayer(segnaposto);
      }
    }
  }

  private conDettagli<T extends L.Layer>(livello: T, campo: SocietaGeolocalizzata): T {
    return livello
      .bindTooltip(testoSicuro(nomeCompleto(campo)))
      // Una funzione e non un testo: il popup si scrive quando si apre, e
      // così porta le partite anche se sono arrivate dopo i segnaposto.
      .bindPopup(() => this.popup(campo));
  }

  private popup(campo: SocietaGeolocalizzata): string {
    const distanza = this.distanze.get(campo.id);
    const riga =
      distanza === undefined ? '' : `<em>A ${testoDistanza(distanza)} da te, in linea d'aria</em><br />`;
    return `
      <strong>${testoSicuro(nomeCompleto(campo))}</strong><br />
      ${riga}
      ${testoSicuro(campo.nomeImpianto)}<br />
      ${testoSicuro(indirizzoCompleto(campo))}<br />
      ${this.partiteNelPopup(campo)}
      ${this.percorsoNelPopup(campo)}
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
  private percorsoNelPopup(campo: SocietaGeolocalizzata): string {
    const posizione = this.posizioneNota();
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
              placeholder="${testoSicuro(suggerimentoPartenza(this.diagnosi.posizione()))}"
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

  /** La posizione di chi guarda, se già nota: da "Vicino a me" o dal popup. */
  private posizioneNota(): Posizione | null {
    return this.posizione() ?? this.posizionePercorso;
  }

  /**
   * Dà vita al blocco del percorso di un popup appena aperto: il link segue
   * quello che si scrive, il pulsante chiede la posizione, e Invio apre il
   * percorso come il link.
   */
  private collegaPercorso(modulo: HTMLFormElement, mappa?: L.Map): void {
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
        this.zona.runOutsideAngular(() => tieniDentro(modulo, mappa));
      }
    };
    const aggiorna = () => {
      link.href = linkPercorso(destinazione, campo.value, this.posizioneNota());
      const dallaPosizione = campo.value.trim() === '' && this.posizioneNota() !== null;
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
      const posizione = await this.richiestaPercorso.chiedi();
      usaPosizione.disabled = false;
      if (posizione) {
        this.posizionePercorso = posizione;
      }
      aggiorna();
      // Nel popup basta il messaggio, che già dice cosa fare: il riquadro con
      // tutti i passi non ci starebbe. Il campo resta libero per scrivere
      // l'indirizzo, e il link parte comunque dalla posizione del dispositivo.
      const errore = this.richiestaPercorso.errore();
      if (errore) {
        scriviNota(errore, true);
      }
    });
  }

  /** Le prime partite della settimana su questo campo, o niente. */
  private partiteNelPopup(campo: Societa): string {
    const partite = partiteDelCampo(this.partite(), campo);
    if (partite.length === 0) {
      return '';
    }
    const righe = partite
      .map(
        (partita) => `
          <li>
            <span class="quando">${testoSicuro(quandoPartita(partita))}</span>
            ${testoSicuro(squadrePartita(partita))}
            <small>${testoSicuro(campionatoPartita(partita))}</small>
          </li>`,
      )
      .join('');
    return `<ul class="partite-popup" aria-label="Prossime partite">${righe}</ul>`;
  }
}
