package it.trovacampo.api.importazione;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.service.SocietaService;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Importa società, impianti e indirizzi da un file Excel o dal PDF di un
 * Comunicato Ufficiale LND (programma gare): il formato si riconosce dal
 * contenuto, non dal nome del file.
 *
 * <p>Una riga corrisponde a una società già presente quando coincidono nome
 * della società e nome dell'impianto (senza badare a maiuscole, accenti e
 * punteggiatura): in quel caso la aggiorna invece di duplicarla. Così lo
 * stesso file, ricaricato con qualche correzione, non raddoppia l'archivio.
 *
 * <p>Una cella vuota non cancella un valore già presente: un file con meno
 * colonne non deve impoverire quello che c'è.
 */
@Service
public class ImportazioneService {

    private static final Logger log = LoggerFactory.getLogger(ImportazioneService.class);
    private static final byte[] FIRMA_PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final SocietaRepository repository;
    private final LettoreExcel lettoreExcel = new LettoreExcel();
    private final LettoreComunicato lettoreComunicato = new LettoreComunicato();

    public ImportazioneService(SocietaRepository repository) {
        this.repository = repository;
    }

    public EsitoImportazione importa(InputStream file, boolean prova) {
        return importaRighe(leggi(file), prova);
    }

    /**
     * Le righe già lette, da un file o dall'anagrafica di presenze: stessa
     * chiave (società e impianto), stesse regole su indirizzi e coordinate.
     */
    EsitoImportazione importaRighe(LettoreExcel.Lettura lettura, boolean prova) {
        List<Scarto> scarti = new ArrayList<>(lettura.scarti());

        Map<String, Societa> esistenti = new HashMap<>();
        for (Societa societa : repository.findAll()) {
            esistenti.putIfAbsent(chiave(societa.getNomeSocieta(), societa.getNomeImpianto()), societa);
        }

        Map<String, Integer> giaNelFile = new HashMap<>();
        List<Societa> daSalvare = new ArrayList<>();
        int inserite = 0;
        int aggiornate = 0;
        int invariate = 0;

        for (RigaExcel riga : lettura.righe()) {
            String chiave = chiave(riga.nomeSocieta(), riga.nomeImpianto());

            Integer precedente = giaNelFile.putIfAbsent(chiave, riga.numero());
            if (precedente != null) {
                scarti.add(new Scarto(riga.numero(), "stessa società e impianto della riga " + precedente));
                continue;
            }

            Societa societa = esistenti.get(chiave);
            if (societa == null) {
                societa = new Societa().setSiglaSocieta("").setComitatoRegionale("");
                applica(societa, riga);
                inserite++;
            } else if (applica(societa, riga)) {
                aggiornate++;
            } else {
                invariate++;
                continue;
            }
            daSalvare.add(SocietaService.aggiornaTestoRicerca(societa));
        }

        if (!prova && !daSalvare.isEmpty()) {
            repository.saveAll(daSalvare);
            log.info("Importazione: {} inserite, {} aggiornate", inserite, aggiornate);
        }

        scarti.sort(Comparator.comparingInt(Scarto::riga));
        int daGeocodificare = (int) daSalvare.stream().filter(s -> s.getLat() == null).count();

        return new EsitoImportazione(
                prova,
                lettura.righe().size() + lettura.scarti().size(),
                inserite,
                aggiornate,
                invariate,
                scarti,
                daGeocodificare,
                lettura.colonneIgnorate());
    }

    /** Un PDF comincia sempre con "%PDF"; tutto il resto lo prova Excel. */
    private LettoreExcel.Lettura leggi(InputStream file) {
        BufferedInputStream flusso = new BufferedInputStream(file);
        try {
            flusso.mark(FIRMA_PDF.length);
            byte[] inizio = flusso.readNBytes(FIRMA_PDF.length);
            flusso.reset();
            return Arrays.equals(inizio, FIRMA_PDF)
                    ? lettoreComunicato.leggi(flusso)
                    : lettoreExcel.leggi(flusso);
        } catch (IOException eccezione) {
            throw new UncheckedIOException(eccezione);
        }
    }

    /**
     * Copia la riga sulla società. Vero se qualcosa è cambiato.
     *
     * <p>Se cambia l'indirizzo e il file non porta coordinate, quelle vecchie
     * vengono tolte: indicherebbero il posto sbagliato. Le ricalcola la
     * geocodifica automatica.
     */
    private static boolean applica(Societa societa, RigaExcel riga) {
        boolean cambiata = false;
        boolean spostata = false;

        if (!riga.nomeSocieta().equals(societa.getNomeSocieta())) {
            societa.setNomeSocieta(riga.nomeSocieta());
            cambiata = true;
        }
        if (!riga.nomeImpianto().equals(societa.getNomeImpianto())) {
            societa.setNomeImpianto(riga.nomeImpianto());
            cambiata = true;
        }
        if (!riga.indirizzo().equals(societa.getIndirizzoImpianto())) {
            societa.setIndirizzoImpianto(riga.indirizzo());
            cambiata = spostata = true;
        }
        if (!riga.localita().isEmpty() && !riga.localita().equals(societa.getLocalitaImpianto())) {
            societa.setLocalitaImpianto(riga.localita());
            cambiata = spostata = true;
        }
        if (!riga.provincia().isEmpty() && !riga.provincia().equals(societa.getProvinciaImpianto())) {
            societa.setProvinciaImpianto(riga.provincia());
            cambiata = spostata = true;
        }
        if (riga.anagraficaSocietaId() != null
                && !riga.anagraficaSocietaId().equals(societa.getAnagraficaSocietaId())) {
            societa.setAnagraficaSocietaId(riga.anagraficaSocietaId());
            cambiata = true;
        }
        // Una società nuova senza località resta con "" come quelle inserite
        // dall'app, non con null.
        if (societa.getLocalitaImpianto() == null) {
            societa.setLocalitaImpianto("");
        }
        if (societa.getProvinciaImpianto() == null) {
            societa.setProvinciaImpianto("");
        }

        if (riga.lat() != null) {
            if (!Objects.equals(riga.lat(), societa.getLat())
                    || !Objects.equals(riga.lng(), societa.getLng())) {
                societa.setLat(riga.lat())
                        .setLng(riga.lng())
                        .setPosizioneApprossimata(null)
                        .setGeocodificaFallitaVersione(null);
                cambiata = true;
            }
        } else if (spostata) {
            // Anche un tentativo fallito sull'indirizzo vecchio non conta più.
            societa.setLat(null)
                    .setLng(null)
                    .setPosizioneApprossimata(null)
                    .setGeocodificaFallitaVersione(null);
        }

        return cambiata;
    }

    /** "A.S.D. Certosa  Calcio" e "asd certosa calcio" sono la stessa società. */
    static String chiave(String nomeSocieta, String nomeImpianto) {
        return compatta(nomeSocieta) + "|" + compatta(nomeImpianto);
    }

    private static String compatta(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]+", "");
    }
}
