package it.trovacampo.api.prospect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** I prospect di calcio del Lazio prendono il campo dalle società che TrovaCampo ha già. */
class CollegamentoCampiTest {

    private final ProspectRepository prospect = mock(ProspectRepository.class);
    private final SocietaRepository societa = mock(SocietaRepository.class);
    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final CollegamentoCampi collegamento = new CollegamentoCampi(prospect, societa, anagrafica);

    private static Prospect lazio(String cf, String denominazione, String comune) {
        return new Prospect()
                .setCodiceFiscale(cf)
                .setDenominazione(denominazione)
                .setComune(comune)
                .setRegione("Lazio")
                .setCalcio(true);
    }

    private static Societa campo(String id, String nome, String impianto, String indirizzo, String comune, double lat, double lng) {
        return new Societa()
                .setId(id)
                .setNomeSocieta(nome)
                .setNomeImpianto(impianto)
                .setIndirizzoImpianto(indirizzo)
                .setLocalitaImpianto(comune)
                .setLat(lat)
                .setLng(lng);
    }

    @Test
    void collegaPerNomeNormalizzatoEPrendeIndirizzoECoordinate() {
        Prospect subiaco = lazio("CF1", "ASS.SPORTIVA DILETTANT.VIS SUBIACO", "Subiaco");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(subiaco));
        when(societa.tuttiICampi())
                .thenReturn(List.of(campo("s1", "A.S.D. VIS SUBIACO", "COMUNALE", "VIA DEI MONASTERI 1", "SUBIACO", 41.92, 13.09)));
        when(anagrafica.impianti()).thenReturn(List.of());

        CollegamentoCampi.Esito esito = collegamento.collega();

        assertThat(subiaco.getIndirizzo()).isEqualTo("VIA DEI MONASTERI 1");
        assertThat(subiaco.getNomeImpianto()).isEqualTo("COMUNALE");
        assertThat(subiaco.getLat()).isEqualTo(41.92);
        assertThat(subiaco.getFonteIndirizzo()).isEqualTo(FonteIndirizzo.CAMPO_TROVACAMPO);
        assertThat(subiaco.getSocietaTrovaCampoId()).isEqualTo("s1");
        assertThat(esito.collegati()).isEqualTo(1);
        assertThat(esito.copertura()).isEqualTo(100.0);
        verify(prospect).saveAll(List.of(subiaco));
    }

    @Test
    void unNomeCheEUnComuneNonBastaDaSolo() {
        // "ACCADEMIA FROSINONE" e "CITTA DI FROSINONE": stessa città, società diverse.
        Prospect accademia = lazio("CF2", "S.S.D. ACCADEMIA FROSINONE S.C SRL", "Ripi");
        // "S.S.D.COLONNA" e "COLONNA": il nome è il comune, e il comune coincide.
        Prospect colonna = lazio("CF3", "S.S.D.COLONNA", "Colonna");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(accademia, colonna));
        when(societa.tuttiICampi())
                .thenReturn(
                        List.of(
                                campo("s2", "A.S.D. CITTA DI FROSINONE", "STIRPE", "VIA MARCO TULLIO CICERONE", "FROSINONE", 41.6, 13.3),
                                campo("s3", "COLONNA", "COMUNALE", "VIA CASILINA KM 25", "COLONNA", 41.83, 12.75)));
        when(anagrafica.impianti()).thenReturn(List.of());

        collegamento.collega();

        assertThat(accademia.getFonteIndirizzo()).isNull();
        assertThat(colonna.getSocietaTrovaCampoId()).isEqualTo("s3");
        assertThat(colonna.getCollegamento()).isEqualTo("nome+comune");
    }

    @Test
    void conPiuCandidatiDecideIlComune() {
        Prospect p = lazio("CF4", "ASD SPORTING CITY ROMA", "Roma");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(p));
        when(societa.tuttiICampi())
                .thenReturn(
                        List.of(
                                campo("a", "SPORTING CITY", "CAMPO A", "VIA A", "LATINA", 41.4, 12.9),
                                campo("b", "SPORTING CITY ROMA NORD", "CAMPO B", "VIA B", "ROMA", 41.9, 12.5)));
        when(anagrafica.impianti()).thenReturn(List.of());

        collegamento.collega();

        // Due candidati per parole: vince quello nello stesso comune della sede.
        assertThat(p.getSocietaTrovaCampoId()).isEqualTo("b");
        assertThat(p.getCollegamento()).isEqualTo("parole+comune");
    }

    @Test
    void senzaCampoInTrovaCampoUsaLAnagraficaDiPresenze() {
        Prospect p = lazio("CF5", "ASSOCIAZIONE SPORTIVA DILETTANTISTICA MENTANA CALCIO 1920", "Mentana");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(p));
        when(societa.tuttiICampi()).thenReturn(List.of());
        when(anagrafica.impianti())
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Impianto(
                                        241L, "12 APOSTOLI", "VIA SELVA DEI CAVALIERI", "FONTE NUOVA", 42.01, 12.63,
                                        List.of(new AnagraficaPresenze.Riferimento(337L, "MENTANA")))));

        collegamento.collega();

        assertThat(p.getFonteIndirizzo()).isEqualTo(FonteIndirizzo.CAMPO_PRESENZE);
        assertThat(p.getAnagraficaImpiantoId()).isEqualTo(241L);
        assertThat(p.getComuneCampo()).isEqualTo("FONTE NUOVA");
    }

    @Test
    void sePresenzeNonRispondeBastanoICampiDiTrovaCampo() {
        Prospect p = lazio("CF6", "A.S.D. VIS SUBIACO", "Subiaco");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(p));
        when(societa.tuttiICampi())
                .thenReturn(List.of(campo("s1", "VIS SUBIACO", "COMUNALE", "VIA X", "SUBIACO", 41.9, 13.1)));
        when(anagrafica.impianti()).thenThrow(new ResourceAccessException("presenze giù"));

        CollegamentoCampi.Esito esito = collegamento.collega();

        assertThat(esito.presenzeRaggiungibile()).isFalse();
        assertThat(p.getSocietaTrovaCampoId()).isEqualTo("s1");
    }

    @Test
    void nessunAbbinamentoNessunSalvataggio() {
        Prospect p = lazio("CF7", "GORILLA", "Roma");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(p));
        when(societa.tuttiICampi())
                .thenReturn(List.of(campo("s1", "A.S.D. ROMA CITY", "C", "VIA Y", "ROMA", 41.9, 12.5)));
        when(anagrafica.impianti()).thenReturn(List.of());

        CollegamentoCampi.Esito esito = collegamento.collega();

        assertThat(esito.collegati()).isZero();
        verify(prospect, never()).saveAll(any());
    }

    @Test
    void leParoleDistintiveTolgonoFormaGiuridicaEAnno() {
        assertThat(NomiSocieta.chiave("A.S.D. POLISPORTIVA SONNINO 1960")).isEqualTo("sonnino");
        assertThat(NomiSocieta.chiave("SOCIETA' SPORTIVA DILETTANTISTICA ROMA CALCIO FEMMINILE S.R.L."))
                .isEqualTo("roma femminile");
        assertThat(NomiSocieta.chiave("S.S.D.R.L. ORIONE OGNISSANTI")).isEqualTo("orione ognissanti");
    }
}
