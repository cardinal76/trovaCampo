package it.trovacampo.api.prospect;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Trasforma le voci di una pagina del registro in prospect salvati: crea i
 * nuovi, aggiorna quelli che ci sono (senza toccare quello che sappiamo gia',
 * come il campo) e, per chi l'{@link AmbitoDettagli} sceglie, chiede il
 * dettaglio (discipline, tesserati).
 *
 * <p>Non sa niente del giro: se si puo' fare un'altra richiesta al registro lo
 * decide chi chiama, con una {@link Richiesta} (ritmo, finestra, tetto
 * giornaliero e il «Ferma» di chi amministra). Qui si sa solo che, se la
 * risposta e' no, si salva quello che si ha e si dice che la pagina non e'
 * finita.
 */
class AggiornamentoDaRegistro {

    /** Se si puo' fare un'altra richiesta al registro: aspetta il ritmo, falso se nel frattempo e' arrivato un «Ferma». */
    @FunctionalInterface
    interface Richiesta {
        boolean prossima() throws InterruptedException;
    }

    private final RegistroRasd registro;
    private final ProspectRepository prospect;
    private final Duration validitaDettaglio;
    private final Supplier<Instant> adesso;

    AggiornamentoDaRegistro(
            RegistroRasd registro, ProspectRepository prospect, Duration validitaDettaglio, Supplier<Instant> adesso) {
        this.registro = registro;
        this.prospect = prospect;
        this.validitaDettaglio = validitaDettaglio;
        this.adesso = adesso;
    }

    /** Un dettaglio letto da meno di {@code validitaDettaglio} non si richiede. */
    private boolean recente(Prospect p) {
        return p.getDettaglioLetto() != null && p.getDettaglioLetto().isAfter(adesso.get().minus(validitaDettaglio));
    }

    /** Falso se ci si è fermati prima di leggere tutti i dettagli della pagina. */
    boolean aggiorna(ContatoriGiro c, List<RegistroRasd.Voce> voci, AmbitoDettagli ambito, Richiesta richiesta)
            throws InterruptedException {
        if (voci.isEmpty()) {
            return true;
        }
        boolean completa = true;
        Map<String, Prospect> esistenti =
                prospect.findAllById(voci.stream().map(RegistroRasd.Voce::codiceFiscale).toList())
                        .stream()
                        .collect(Collectors.toMap(Prospect::getCodiceFiscale, Function.identity(), (a, b) -> a));
        List<Prospect> daSalvare = new ArrayList<>();
        try {
            for (RegistroRasd.Voce voce : voci) {
                Prospect p = esistenti.get(voce.codiceFiscale());
                if (p == null) {
                    p = new Prospect().setCodiceFiscale(voce.codiceFiscale());
                    c.nuove++;
                }
                p.setRasdId(voce.id())
                        .setDenominazione(voce.denominazione())
                        .setNaturaGiuridica(voce.naturaGiuridica())
                        .setRegione(voce.regione())
                        .setComune(voce.comune())
                        .setOrganismi(voce.organismi())
                        .setDataIscrizione(voce.dataIscrizione())
                        .setCalcio(calcio(voce.organismi(), p.getDiscipline()))
                        .setAggiornato(adesso.get());
                if (completa && ambito.vuole(voce, p)) {
                    if (recente(p)) {
                        c.saltati++;
                    } else if (!richiesta.prossima()) {
                        completa = false;
                    } else {
                        RegistroRasd.Dettaglio dettaglio = registro.dettaglio(voce.id());
                        c.dettagli++;
                        p.setDiscipline(dettaglio.discipline())
                                .setTesseratiAttivi(dettaglio.tesseratiAttivi())
                                .setAttivitaSportiveUltimoAnno(dettaglio.attivitaSportiveUltimoAnno())
                                .setPartecipazioniUltimoAnno(dettaglio.partecipazioniUltimoAnno())
                                .setDettaglioLetto(adesso.get())
                                .setCalcio(calcio(voce.organismi(), dettaglio.discipline()));
                    }
                }
                daSalvare.add(p);
            }
        } finally {
            // Anche se il registro blocca a metà pagina: i dettagli già letti non si richiedono più.
            c.lette += daSalvare.size();
            prospect.saveAll(daSalvare);
        }
        return completa;
    }


    /** Affiliata FIGC, o con una disciplina che comincia per «Calcio» (calcio a 5, a 11…). */
    static boolean calcio(List<String> organismi, List<String> discipline) {
        if (organismi != null && organismi.stream().anyMatch("FIGC"::equalsIgnoreCase)) {
            return true;
        }
        return discipline != null
                && discipline.stream().anyMatch(d -> d.toLowerCase().startsWith("calcio"));
    }
}
