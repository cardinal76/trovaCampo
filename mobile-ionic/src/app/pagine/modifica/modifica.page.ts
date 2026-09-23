import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AlertController,
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonNote,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonTitle,
  IonToggle,
  IonToolbar,
  ModalController,
  NavController,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, location, logOut, trash, warning } from 'ionicons/icons';
import { SelettoreMappaComponent } from '../../componenti/selettore-mappa/selettore-mappa.component';
import { ModificaSocieta, Societa, TipoCampionato } from '../../modelli/societa';
import { AmministrazioneService } from '../../servizi/amministrazione.service';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/autenticazione.service';
import { SocietaService } from '../../servizi/societa.service';

/** Una riga dei campionati nel modulo. */
interface RigaCampionato {
  descrizione: string;
  girone: string;
  comitato: string;
  tipo: TipoCampionato;
}

/**
 * Tutti i campi del modulo come testo, così come li scrive chi compila:
 * latitudine e longitudine comprese, che si convertono solo al salvataggio.
 */
class Modulo {
  siglaSocieta = '';
  nomeSocieta = '';
  comitatoRegionale = '';
  nomeImpianto = '';
  indirizzoImpianto = '';
  localitaImpianto = '';
  provinciaImpianto = '';
  lat = '';
  lng = '';
  matricola = '';
  presidente = '';
  indirizzoSede = '';
  telefono = '';
  fax = '';
  email = '';
  sitoWeb = '';
  scuolaCalcio = false;
  prezziScuolaCalcio = '';
  campionati: RigaCampionato[] = [];

  static da(societa: Societa): Modulo {
    const modulo = new Modulo();
    modulo.siglaSocieta = societa.siglaSocieta ?? '';
    modulo.nomeSocieta = societa.nomeSocieta ?? '';
    modulo.comitatoRegionale = societa.comitatoRegionale ?? '';
    modulo.nomeImpianto = societa.nomeImpianto ?? '';
    modulo.indirizzoImpianto = societa.indirizzoImpianto ?? '';
    modulo.localitaImpianto = societa.localitaImpianto ?? '';
    modulo.provinciaImpianto = societa.provinciaImpianto ?? '';
    modulo.lat = societa.lat?.toString() ?? '';
    modulo.lng = societa.lng?.toString() ?? '';
    modulo.matricola = societa.matricola ?? '';
    modulo.presidente = societa.presidente ?? '';
    modulo.indirizzoSede = societa.indirizzoSede ?? '';
    modulo.telefono = societa.telefono ?? '';
    modulo.fax = societa.fax ?? '';
    modulo.email = societa.email ?? '';
    modulo.sitoWeb = societa.sitoWeb ?? '';
    modulo.scuolaCalcio = societa.scuolaCalcio ?? false;
    modulo.prezziScuolaCalcio = societa.prezziScuolaCalcio ?? '';
    modulo.campionati = (societa.campionati ?? []).map((c) => ({ ...c }));
    return modulo;
  }
}

/** "41,89" e "41.89" valgono uguale; vuoto vuol dire "da ricalcolare". */
function coordinata(testo: string): number | undefined | null {
  const pulito = testo.trim().replace(',', '.');
  if (pulito === '') {
    return undefined;
  }
  const numero = Number(pulito);
  return Number.isFinite(numero) ? numero : null;
}

/**
 * Scheda di una società da creare o da modificare, per chi ha il ruolo
 * trovacampo-admin. Lo stesso modulo serve ai due casi: /admin/societa/nuova
 * parte vuoto, /admin/societa/:id dai dati salvati e in fondo ha anche
 * "Elimina società".
 *
 * Ci si arriva dai pulsanti "Nuova società" in home e "Modifica" nella scheda,
 * che compaiono solo all'amministratore; aprirla porta al login del Keycloak
 * di presenze come la pagina di importazione. Salvando si va alla scheda.
 */
@Component({
  selector: 'pagina-modifica',
  imports: [
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCard,
    IonCardContent,
    IonContent,
    IonHeader,
    IonIcon,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonNote,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonTitle,
    IonToggle,
    IonToolbar,
  ],
  templateUrl: './modifica.page.html',
  styleUrl: './modifica.page.scss',
})
export class ModificaPage {
  private readonly rotta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastController);
  private readonly avvisi = inject(AlertController);
  private readonly navigazione = inject(NavController);
  private readonly modaleControllo = inject(ModalController);
  private readonly societa = inject(SocietaService);
  private readonly amministrazione = inject(AmministrazioneService);
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;

  /** Vero su /admin/societa/nuova: modulo vuoto, niente da eliminare. */
  readonly nuova = this.rotta.snapshot.data['nuova'] === true;
  private readonly id = this.rotta.snapshot.paramMap.get('id') ?? '';

  readonly stato = signal<'accesso' | 'caricamento' | 'pronto' | 'errore'>('accesso');
  readonly modulo = signal<Modulo | null>(null);
  readonly salvataggio = signal(false);
  readonly errore = signal<string | null>(null);

  constructor() {
    addIcons({ add, location, logOut, trash, warning });
    this.entra();
  }

  entra(): void {
    this.stato.set('accesso');
    this.autenticazione.accedi().then(
      () => this.carica(),
      () => this.stato.set('errore'),
    );
  }

  esci(): void {
    void this.autenticazione.esci();
  }

  aggiungiCampionato(modulo: Modulo): void {
    modulo.campionati.push({ descrizione: '', girone: '', comitato: '', tipo: 'Agonistica' });
  }

  togliCampionato(modulo: Modulo, indice: number): void {
    modulo.campionati.splice(indice, 1);
  }

  /** Apre la mappa a schermo intero per scegliere le coordinate a tocco, invece di scriverle a mano. */
  async posizionaSullaMappa(modulo: Modulo): Promise<void> {
    const lat = coordinata(modulo.lat);
    const lng = coordinata(modulo.lng);
    const modale = await this.modaleControllo.create({
      component: SelettoreMappaComponent,
      componentProps: {
        lat: typeof lat === 'number' ? lat : undefined,
        lng: typeof lng === 'number' ? lng : undefined,
      },
    });
    await modale.present();

    const { data, role } = await modale.onDidDismiss<{ lat: number; lng: number }>();
    if (role === 'confirm' && data) {
      modulo.lat = data.lat.toFixed(6);
      modulo.lng = data.lng.toFixed(6);
    }
  }

  salva(): void {
    const modulo = this.modulo();
    if (!modulo || this.salvataggio()) {
      return;
    }

    const scheda = this.scheda(modulo);
    if (typeof scheda === 'string') {
      this.errore.set(scheda);
      return;
    }

    this.salvataggio.set(true);
    this.errore.set(null);
    const salvataggio = this.nuova
      ? this.amministrazione.crea(scheda)
      : this.amministrazione.modifica(this.id, scheda);

    salvataggio.subscribe({
      next: async (salvata) => {
        this.salvataggio.set(false);
        await this.avvisa(this.nuova ? 'Società creata.' : 'Scheda salvata.');
        await this.router.navigate(['/societa', salvata.id], { replaceUrl: true });
      },
      error: (errore: Error) => {
        this.salvataggio.set(false);
        this.errore.set(errore.message);
      },
    });
  }

  async elimina(): Promise<void> {
    const modulo = this.modulo();
    const conferma = await this.avvisi.create({
      header: 'Eliminare la società?',
      message: `${modulo?.nomeSocieta ?? 'La società'} e il suo campo spariranno da ricerca, elenco e mappa. Non si può annullare.`,
      buttons: [
        { text: 'Annulla', role: 'cancel' },
        { text: 'Elimina', role: 'destructive' },
      ],
    });
    await conferma.present();
    const { role } = await conferma.onDidDismiss();
    if (role !== 'destructive') {
      return;
    }

    this.salvataggio.set(true);
    this.errore.set(null);
    this.amministrazione.elimina(this.id).subscribe({
      next: async () => {
        this.salvataggio.set(false);
        await this.avvisa('Società eliminata.');
        // All'elenco, ripartendo da capo: tornando indietro si finirebbe
        // sulla scheda, che non esiste più.
        await this.navigazione.navigateRoot('/campi');
      },
      error: (errore: Error) => {
        this.salvataggio.set(false);
        this.errore.set(errore.message);
      },
    });
  }

  private async avvisa(messaggio: string): Promise<void> {
    const avviso = await this.toast.create({ message: messaggio, duration: 2000, color: 'success' });
    await avviso.present();
  }

  private carica(): void {
    if (this.nuova) {
      this.modulo.set(new Modulo());
      this.stato.set('pronto');
      return;
    }
    this.stato.set('caricamento');
    this.societa.perId(this.id).subscribe({
      next: (societa) => {
        this.modulo.set(Modulo.da(societa));
        this.stato.set('pronto');
      },
      error: () => this.stato.set('errore'),
    });
  }

  /** La scheda da mandare, o il motivo per cui non si può ancora mandare. */
  private scheda(modulo: Modulo): ModificaSocieta | string {
    const obbligatori: [string, string][] = [
      [modulo.nomeSocieta, 'il nome della società'],
      [modulo.nomeImpianto, "il nome dell'impianto"],
      [modulo.indirizzoImpianto, "l'indirizzo del campo"],
    ];
    const mancanti = obbligatori.filter(([valore]) => !valore.trim()).map(([, nome]) => nome);
    if (mancanti.length > 0) {
      return `Manca ${mancanti.join(', ')}.`;
    }

    const lat = coordinata(modulo.lat);
    const lng = coordinata(modulo.lng);
    if (lat === null || lng === null) {
      return 'Latitudine e longitudine devono essere numeri, es. 41,8919.';
    }
    if ((lat === undefined) !== (lng === undefined)) {
      return 'Latitudine e longitudine vanno date insieme, oppure lasciate vuote tutte e due.';
    }

    return {
      siglaSocieta: modulo.siglaSocieta,
      nomeSocieta: modulo.nomeSocieta,
      comitatoRegionale: modulo.comitatoRegionale,
      nomeImpianto: modulo.nomeImpianto,
      indirizzoImpianto: modulo.indirizzoImpianto,
      localitaImpianto: modulo.localitaImpianto,
      provinciaImpianto: modulo.provinciaImpianto,
      lat,
      lng,
      matricola: modulo.matricola,
      presidente: modulo.presidente,
      indirizzoSede: modulo.indirizzoSede,
      telefono: modulo.telefono,
      fax: modulo.fax,
      email: modulo.email,
      sitoWeb: modulo.sitoWeb,
      scuolaCalcio: modulo.scuolaCalcio,
      prezziScuolaCalcio: modulo.prezziScuolaCalcio,
      campionati: modulo.campionati,
    };
  }
}
