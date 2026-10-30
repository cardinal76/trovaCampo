package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Un caso vero per ogni tipo di differenza fra il Registro RASD, i Comunicati
 * LND (via presenze) e il censimento degli impianti di Roma.
 */
class NomiSocietaTest {

    private static void stessaSocieta(String uno, String altro) {
        assertThat(NomiSocieta.somiglianza(uno, altro))
                .as("%s ~ %s", uno, altro)
                .isGreaterThanOrEqualTo(NomiSocieta.SOGLIA);
    }

    private static void societaDiverse(String uno, String altro) {
        assertThat(NomiSocieta.somiglianza(uno, altro))
                .as("%s != %s", uno, altro)
                .isLessThan(NomiSocieta.SOGLIA);
    }

    @Test
    void lAsdPuntataSparisce() {
        assertThat(NomiSocieta.parole("A.S.D. MONTE MARIO")).containsExactly("monte", "mario");
        assertThat(NomiSocieta.somiglianza("A.S.D. MONTE MARIO", "MONTE MARIO")).isEqualTo(1.0);
    }

    @Test
    void laSsdARlInTutteLeGrafie() {
        // Il RASD, i comunicati e i nomi incollati di presenze.
        assertThat(NomiSocieta.parole("VIS AURELIA S.S.D. A R.L.")).containsExactly("vis", "aurelia");
        assertThat(NomiSocieta.parole("VESTA SSD A R.L.")).containsExactly("vesta");
        assertThat(NomiSocieta.parole("S.S.D. VESTA A.R.L.")).containsExactly("vesta");
        assertThat(NomiSocieta.parole("L.C. UNITED SSDARL")).containsExactly("lc", "united");
        assertThat(NomiSocieta.parole("ELIS A.R.L.")).containsExactly("elis");
        assertThat(NomiSocieta.parole("SOCIETA' SPORTIVA DILETTANTISTICA VESTA A RESPONSABILITA' LIMITATA"))
                .containsExactly("vesta");
    }

    @Test
    void polisportivaPerEstesoOAbbreviata() {
        stessaSocieta("POLISPORTIVA DE ROSSI", "POL. DE ROSSI");
        stessaSocieta("ASDPOL ROCCASECCA DEI VOLSCI", "A.S.D. POLISPORTIVA ROCCASECCA DEI VOLSCI");
        stessaSocieta("POL.D. ISCHIA DI CASTRO", "ISCHIA DI CASTRO");
    }

    @Test
    void accentiApostrofiEPunteggiatura() {
        assertThat(NomiSocieta.parole("CITTA'DI OSTIA")).containsExactly("citta", "ostia");
        stessaSocieta("CITTA'DI OSTIA", "A.S.D. Città di Ostia");
        stessaSocieta("SETTEVILLE - CASE ROSSE", "A.S.D. SETTEVILLE CASEROSSE");
    }

    @Test
    void paroleAttaccateOStaccate() {
        stessaSocieta("A.S.D. TORBELLAMONACA FIDELIS", "TOR BELLA MONACA FIDELIS");
    }

    @Test
    void lAnnoDiFondazioneScrittoDaUnaParteSola() {
        stessaSocieta("LODIGIANI CALCIO 1972", "A.S.D. LODIGIANI");
        stessaSocieta("A.S.D. CINETO ROMANO 1974", "CINETO ROMANO");
        // Due anni diversi sono due società diverse.
        societaDiverse("ATLETICO 2000", "ATLETICO 1999");
    }

    @Test
    void unRefusoNonSeparaLeSocieta() {
        stessaSocieta("ASDPOL BORGHESIANA", "BORGHESANA");
    }

    @Test
    void sanAbbreviato() {
        stessaSocieta("A.S.D. S. LORENZO NUOVO", "SAN LORENZO NUOVO");
    }

    @Test
    void ilNomeCortoContenutoNelLungo() {
        stessaSocieta("A.S.D. CVN CASAL BERNOCCHI", "CASAL BERNOCCHI ONLUS");
    }

    @Test
    void parolaInComuneNonBasta() {
        societaDiverse("ACADEMY GRIFONE", "F.C. MASSIMINA GRIFONE");
        societaDiverse("REAL TIBERINUS", "TIBERINUS");
        societaDiverse("A.S.D. ROMULEA", "A.S.D. ROMA CITY");
    }

    @Test
    void calcioEFootballClubNonDistinguono() {
        stessaSocieta("SORA CALCIO", "A.S.D. SORA");
        stessaSocieta("FOOTBALL CLUB NEXT GEN", "NEXT GEN F.C.");
        // Senza altre parole restano quelle generiche, piuttosto che niente.
        assertThat(NomiSocieta.parole("A.S.D. CALCIO")).containsExactly("calcio");
    }

    @Test
    void ilNomeDiUnImpiantoSenzaLeParoleDaImpianto() {
        assertThat(NomiSocieta.paroleImpianto("Centro Sportivo Kolbe Ponte Mammolo"))
                .containsExactly("kolbe", "ponte", "mammolo");
        assertThat(NomiSocieta.somiglianza(
                        NomiSocieta.parole("C.S. KOLBE PONTE MAMMOLO A.S.D"), java.util.List.of(),
                        NomiSocieta.paroleImpianto("Centro Sportivo Kolbe Ponte Mammolo"), java.util.List.of()))
                .isEqualTo(1.0);
    }

    @Test
    void comuniDiFontiDiverse() {
        assertThat(Comuni.stesso("Roma (RM)", "ROMA")).isTrue();
        assertThat(Comuni.stesso("Lido di Ostia", "Roma")).isTrue();
        assertThat(Comuni.stesso("SANT'ORESTE", "Sant Oreste")).isTrue();
        assertThat(Comuni.stesso("ROMA", "RIETI")).isFalse();
        assertThat(Comuni.stesso(null, "")).isFalse();
    }
}
