package it.trovacampo.api.indirizzi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fonte 2: gli impianti sportivi dei censimenti open data
 * ({@code impianto_open_data}), sempre dentro il comune della sede.
 *
 * <ul>
 *   <li>il nome dell'impianto, tolte le parole da impianto ("Centro
 *       Sportivo", "Stadio Comunale"), somiglia a quello della società:
 *       "C.S. KOLBE PONTE MAMMOLO A.S.D." e "Centro Sportivo Kolbe Ponte
 *       Mammolo". MEDIA da 0,9, BASSA fra la soglia e 0,9;
 *   <li>la società porta il nome del paese ("A.S.D. CERVETERI", "PONTINIA")
 *       e nel paese c'è un solo impianto con un campo a 11: è quasi sempre il
 *       comunale dove gioca. BASSA, perché resta un'ipotesi.
 * </ul>
 */
@Component
public class FonteImpiantiOpenData {

    private static final Duration VALIDITA = Duration.ofMinutes(10);
    static final double SOGLIA_MEDIA = 0.9;
    static final double PUNTEGGIO_CAMPO_DEL_PAESE = 0.5;

    private final ImpiantiOpenDataRepository repository;
    private final Clock orologio;
    private Map<String, List<ImpiantoOpenData>> perComune;
    private Instant lettiIl;

    @Autowired
    public FonteImpiantiOpenData(ImpiantiOpenDataRepository repository) {
        this(repository, Clock.systemUTC());
    }

    FonteImpiantiOpenData(ImpiantiOpenDataRepository repository, Clock orologio) {
        this.repository = repository;
        this.orologio = orologio;
    }

    public Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa) {
        return cerca(societa, perComune().getOrDefault(Comuni.chiave(societa.comune()), List.of()));
    }

    synchronized Map<String, List<ImpiantoOpenData>> perComune() {
        Instant adesso = orologio.instant();
        if (perComune == null || lettiIl.plus(VALIDITA).isBefore(adesso)) {
            perComune =
                    repository.findAll().stream()
                            .collect(Collectors.groupingBy(impianto -> Comuni.chiave(impianto.comune())));
            lettiIl = adesso;
        }
        return perComune;
    }

    /** @param impianti gli impianti del comune della sede */
    static Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa, List<ImpiantoOpenData> impianti) {
        List<String> parole = NomiSocieta.parole(societa.denominazione());
        List<String> anni = NomiSocieta.anni(societa.denominazione());

        record Punteggio(ImpiantoOpenData impianto, double valore) {}
        Optional<Punteggio> perNome =
                impianti.stream()
                        .filter(impianto -> impianto.indirizzo() != null && !impianto.indirizzo().isBlank())
                        .map(
                                impianto ->
                                        new Punteggio(
                                                impianto,
                                                NomiSocieta.somiglianza(
                                                        parole, anni,
                                                        NomiSocieta.paroleImpianto(impianto.nome()), List.of())))
                        .filter(punteggio -> punteggio.valore() >= NomiSocieta.SOGLIA)
                        .max(Comparator.comparingDouble(Punteggio::valore));
        if (perNome.isPresent()) {
            Punteggio migliore = perNome.get();
            return Optional.of(
                    indirizzo(
                            migliore.impianto(),
                            migliore.valore() >= SOGLIA_MEDIA ? Confidenza.MEDIA : Confidenza.BASSA,
                            migliore.valore()));
        }
        return campoDelPaese(societa, parole, impianti);
    }

    private static Optional<IndirizzoMigliore> campoDelPaese(
            SocietaDaLocalizzare societa, List<String> parole, List<ImpiantoOpenData> impianti) {
        List<String> paroleComune = Arrays.asList(Comuni.chiave(societa.comune()).split(" "));
        if (parole.isEmpty() || !paroleComune.containsAll(parole) || Comuni.chiave(societa.comune()).equals("roma")) {
            return Optional.empty();
        }
        List<ImpiantoOpenData> a11 =
                impianti.stream()
                        .filter(ImpiantoOpenData::haCalcioA11)
                        .filter(impianto -> impianto.indirizzo() != null && !impianto.indirizzo().isBlank())
                        .toList();
        if (a11.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(indirizzo(a11.get(0), Confidenza.BASSA, PUNTEGGIO_CAMPO_DEL_PAESE));
    }

    private static IndirizzoMigliore indirizzo(
            ImpiantoOpenData impianto, Confidenza confidenza, double punteggio) {
        return new IndirizzoMigliore(
                impianto.indirizzo(),
                impianto.comune(),
                impianto.lat(),
                impianto.lng(),
                Fonte.OPEN_DATA,
                confidenza,
                punteggio,
                impianto.nome(),
                impianto.nome(),
                Fonte.OPEN_DATA.attribuzione());
    }
}
