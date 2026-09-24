package it.trovacampo.api;

import it.trovacampo.api.web.SocietaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica che tutti i bean dell'applicazione si costruiscano davvero.
 * Dati di esempio, geocodifica automatica, completamento delle province e
 * giro delle notifiche sono disattivati perché sono gli unici punti che
 * aprirebbero una connessione a MongoDB all'avvio.
 */
@SpringBootTest(
        properties = {
            "trovacampo.seed.abilitato=false",
            "trovacampo.geocodifica.automatica=false",
            "trovacampo.province.completamento=false",
            "trovacampo.notifiche.attive=false"
        })
class TrovaCampoApplicationTest {

    @Autowired private SocietaController controller;

    @Test
    void ilContestoSiAvvia() {
        assertThat(controller).isNotNull();
    }
}
