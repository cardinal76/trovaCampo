package it.trovacampo.api.prospect;

import it.trovacampo.api.indirizzi.FonteIndirizzo;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * La lista commerciale delle ASD/SSD: importazione dal registro, collegamento
 * ai campi, mappa ed export. Tutto sotto {@code /api/admin}, quindi solo con
 * il ruolo trovacampo-admin (vedi
 * {@link it.trovacampo.api.config.ConfigurazioneSicurezza}); niente di
 * questo esce dalle API pubbliche.
 */
@RestController
@RequestMapping("/api/admin/prospect")
public class ProspectController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportazioneProspect importazione;
    private final CollegamentoCampi collegamento;
    private final EsportazioneProspect esportazione;
    private final ProspectRepository repository;
    private final ArricchimentoProspect arricchimento;

    public ProspectController(
            ImportazioneProspect importazione,
            CollegamentoCampi collegamento,
            EsportazioneProspect esportazione,
            ProspectRepository repository,
            ArricchimentoProspect arricchimento) {
        this.importazione = importazione;
        this.collegamento = collegamento;
        this.esportazione = esportazione;
        this.repository = repository;
        this.arricchimento = arricchimento;
    }

    /** I prospect con una posizione, per la mappa: solo i dati che la mappa mostra. */
    public record SullaMappa(
            String codiceFiscale,
            String denominazione,
            String regione,
            String comune,
            List<String> organismi,
            List<String> discipline,
            Integer tesseratiAttivi,
            boolean calcio,
            String nomeImpianto,
            String indirizzo,
            String comuneCampo,
            double lat,
            double lng,
            FonteIndirizzo fonteIndirizzo) {

        static SullaMappa di(Prospect p) {
            return new SullaMappa(
                    p.getCodiceFiscale(), p.getDenominazione(), p.getRegione(), p.getComune(),
                    p.getOrganismi(), p.getDiscipline(), p.getTesseratiAttivi(), p.isCalcio(),
                    p.getNomeImpianto(), p.getIndirizzo(), p.getComuneCampo(), p.getLat(), p.getLng(),
                    p.getFonteIndirizzo());
        }
    }

    @GetMapping
    public List<SullaMappa> sullaMappa() {
        return repository.conCoordinate().stream().map(SullaMappa::di).toList();
    }

    @GetMapping("/importazione")
    public ImportazioneProspect.Stato stato() {
        return importazione.stato();
    }

    /**
     * Avvia l'importazione in sottofondo e risponde subito (202); lo stato si
     * legge con la GET. Con un'importazione già in corso risponde 409.
     * {@code dettagli} è per chi leggere il dettaglio: {@code nessuno},
     * {@code figc}, {@code calcio} (il default) o {@code tutte}; {@code true}
     * e {@code false} valgono come prima, calcio o niente.
     */
    @PostMapping("/importazione")
    public ResponseEntity<ImportazioneProspect.Stato> avvia(
            @RequestParam(name = "regioni", required = false) List<Integer> regioni,
            @RequestParam(name = "maxPagine", required = false) Integer maxPagine,
            @RequestParam(name = "ricomincia", defaultValue = "false") boolean ricomincia,
            @RequestParam(name = "dettagli", defaultValue = "calcio") String dettagli,
            Authentication chi) {
        if (regioni != null && !RegistroRasd.REGIONI.keySet().containsAll(regioni)) {
            return ResponseEntity.badRequest().build();
        }
        AmbitoDettagli ambito;
        try {
            ambito = AmbitoDettagli.da(dettagli);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        boolean avviata =
                importazione.avvia(
                        new ImportazioneProspect.Opzioni(regioni, maxPagine, ricomincia, ambito),
                        chi.getName());
        return ResponseEntity.status(avviata ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                .body(importazione.stato());
    }

    /**
     * Riprende l'ultimo giro con le sue opzioni, dalla posizione salvata: dopo
     * un «Ferma», un riavvio o un blocco del registro (che da solo non si
     * riprova). 202 se riparte, 409 se ce n'è già uno, 404 se non c'è niente
     * da riprendere.
     */
    @PostMapping("/importazione/riprendi")
    public ResponseEntity<ImportazioneProspect.Stato> riprendi(Authentication chi) {
        return importazione
                .riprendi(chi.getName())
                .map(avviata -> ResponseEntity.status(avviata ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                        .body(importazione.stato()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(importazione.stato()));
    }

    /** Si ferma dopo la richiesta (o durante la pausa) in corso; «riprendi» riparte da lì. */
    @PostMapping("/importazione/ferma")
    public ImportazioneProspect.Stato ferma() {
        importazione.ferma();
        return importazione.stato();
    }

    /**
     * Il collegamento ai campi del Lazio, adesso: campi LND e censimento open
     * data, senza chiamare Nominatim. Per esempio dopo aver importato nuovi
     * campi o il censimento.
     */
    @PostMapping("/collegamento")
    public CollegamentoCampi.Esito collega() {
        return collegamento.collega();
    }

    /** Quante società di calcio del Lazio hanno l'indirizzo, per fonte e confidenza, senza cercare niente. */
    @GetMapping("/copertura")
    public CollegamentoCampi.Esito copertura() {
        return collegamento.copertura();
    }

    /**
     * La ricerca con la rete (geocodifica e OpenStreetMap) per chi è ancora
     * senza indirizzo o senza posizione, in sottofondo: 202 se parte, 409 se
     * ne gira già una, 200 con il motivo se non c'è niente da cercare
     * (il registro non è ancora stato importato).
     */
    @PostMapping("/geocodifica")
    public ResponseEntity<ArricchimentoProspect.Avvio> geocodifica(
            @RequestParam(name = "ritenta", defaultValue = "false") boolean ritenta,
            @RequestParam(name = "limite", required = false) Integer limite) {
        ArricchimentoProspect.Avvio avvio = arricchimento.avvia(new ArricchimentoProspect.Richiesta(ritenta, limite));
        if (avvio.avviato()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(avvio);
        }
        return ResponseEntity.status(avvio.stato().inCorso() ? HttpStatus.CONFLICT : HttpStatus.OK).body(avvio);
    }

    @GetMapping("/geocodifica")
    public ArricchimentoProspect.Stato statoGeocodifica() {
        return arricchimento.stato();
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<StreamingResponseBody> esporta() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(EsportazioneProspect.NOME_FILE).build().toString())
                .body(esportazione::scrivi);
    }
}
