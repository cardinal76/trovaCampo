package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class LettoreProspectTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final LettoreProspect lettore = new LettoreProspect(mongo, "prospect");

    private static final Document ROMULEA =
            new Document("_id", "80000000001")
                    .append("denominazione", "A.S.D. ROMULEA")
                    .append("sede", new Document("comune", "ROMA").append("regione", "LAZIO"))
                    .append("organismiAffilianti", List.of("FIGC - Federazione Italiana Giuoco Calcio", "CSI"));
    private static final Document PALLAVOLO =
            new Document("_id", "80000000002")
                    .append("denominazione", "A.S.D. VOLLEY ARCE")
                    .append("sede", new Document("comune", "ARCE").append("regione", "LAZIO"))
                    .append("organismiAffilianti", List.of("FIPAV"));

    @Test
    void leggeICampiAncheAnnidati() {
        assertThat(LettoreProspect.leggi(ROMULEA))
                .isEqualTo(new LettoreProspect.SocietaProspect("80000000001", "A.S.D. ROMULEA", "ROMA", null));
        assertThat(LettoreProspect.leggi(new Document("comune", "ROMA"))).isNull();
    }

    @Test
    void soloLeSocietaDiCalcioDelLazio() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("prospect"))).thenReturn(ROMULEA);
        when(mongo.stream(any(Query.class), eq(Document.class), eq("prospect")))
                .thenReturn(Stream.of(ROMULEA, PALLAVOLO));

        LettoreProspect.Lettura lettura = lettore.societaDiCalcioDelLazio();

        assertThat(lettura.societa()).extracting(LettoreProspect.SocietaProspect::codiceFiscale)
                .containsExactly("80000000001");
        assertThat(lettura.filtroCalcio()).isTrue();
        assertThat(lettura.criterioLazio()).isEqualTo("sede.regione = Lazio");
    }

    @Test
    void senzaRegioneNeOrganismiValeIlComuneDelLazio() {
        Document lazio = new Document("codiceFiscale", "1").append("denominazione", "A").append("comune", "ARCE");
        Document fuori = new Document("codiceFiscale", "2").append("denominazione", "B").append("comune", "MILANO");
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("prospect"))).thenReturn(lazio);
        when(mongo.stream(any(Query.class), eq(Document.class), eq("prospect"))).thenReturn(Stream.of(lazio, fuori));

        LettoreProspect.Lettura lettura = lettore.societaDiCalcioDelLazio();

        assertThat(lettura.societa()).extracting(LettoreProspect.SocietaProspect::comune).containsExactly("ARCE");
        assertThat(lettura.filtroCalcio()).isFalse();
    }
}
