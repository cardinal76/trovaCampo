package it.trovacampo.api.prospect;

import java.util.List;
import java.util.stream.Collectors;

/** Risposte del registro con la stessa forma di quelle vere (provate a mano, poche pagine). */
final class RispostaRasd {

    static final String LEGALE_RAPPRESENTANTE = "MARIO ROSSINI";

    private RispostaRasd() {}

    record Riga(long id, String cf, String denominazione, String regione, String comune, List<String> os) {}

    static Riga riga(long id, String cf, String denominazione, String comune, String... os) {
        return new Riga(id, cf, denominazione, "Lazio", comune, List.of(os));
    }

    static String elenco(int count, Riga... righe) {
        String dati =
                List.of(righe).stream()
                        .map(
                                r ->
                                        """
                                        {"id":%d,"id_pad":"00%d","tipoIstruttoria":1,
                                         "presentazione_data":"2011-11-22T01:00:00+01:00",
                                         "approvazione_data":"2011-12-22T01:00:00+01:00",
                                         "societa__codiceFiscale":"%s",
                                         "societa__iconaVcf":"static/icons/age_9.png",
                                         "societa__denominazione":"%s",
                                         "societa__sedeLegale__regione__denominazione":"%s",
                                         "societa__sedeLegale__comune__denominazione":"%s",
                                         "organismi_affiliazioni_attive":[%s],
                                         "statoIstruttoria__descrizione":"Domanda accolta",
                                         "utenza":"",
                                         "societa__natura_giuridica":"Associazione senza personalità giuridica",
                                         "affiliazione_scaduta_organismo_icona":""}
                                        """
                                                .formatted(
                                                        r.id(), r.id(), r.cf(), r.denominazione(),
                                                        r.regione(), r.comune(),
                                                        r.os().stream()
                                                                .map(o -> "\"static/os/" + o + ".png\"")
                                                                .collect(Collectors.joining(","))))
                        .collect(Collectors.joining(","));
        return """
                {"stato":1,"payload":{"count":%d,"data":[%s]},"errori":[]}
                """
                .formatted(count, dati);
    }

    static String dettaglio(String cf, String discipline, int tesserati) {
        return """
                {"stato":1,"payload":{"testata":{"testo":"%s","icona":"","bgcolor":"","color":""},
                 "immagine":{"path":""},
                 "corpo":{"dati":[
                  {"label":"Codice Fiscale","tipo":"input","value":"%s"},
                  {"label":"Denominazione","tipo":"input","value":"A.S.D. ESEMPIO"},
                  {"label":"Legale rappresentante","tipo":"input","value":"%s"},
                  {"label":"Regione","tipo":"input","value":"Lazio"},
                  {"label":"Comune","tipo":"input","value":"Roma"},
                  {"label":"Organismi sportivi attivi","tipo":"input","value":"FIGC, CSI"},
                  {"label":"Data presentazione","tipo":"date","value":"2011-11-22T01:00:00+01:00"},
                  {"label":"Discipline attive","tipo":"textarea","value":"%s"},
                  {"label":"Nr. attività sportive organizzate nell'ultimo anno","tipo":"number","value":3},
                  {"label":"Nr. partecipazioni attività sportive nell'ultimo anno","tipo":"number","value":120},
                  {"label":"Nr. attività didattiche organizzate nell'ultimo anno","tipo":"number","value":1},
                  {"label":"Nr. tesserati attivi","tipo":"number","value":%d},
                  {"label":"Personalità Giuridica","tipo":"input","value":"Associazione senza personalità giuridica"}
                 ]}},"errori":[]}
                """
                .formatted(cf, cf, LEGALE_RAPPRESENTANTE, discipline, tesserati);
    }
}
