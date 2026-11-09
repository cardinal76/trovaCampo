package it.trovacampo.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Ogni pacchetto sotto {@code it.trovacampo.api} e' un modulo: possono
 * dipendere l'uno dall'altro solo in un verso, senza cicli. Se questo test
 * fallisce, il messaggio dice quali moduli si guardano a vicenda: si risolve
 * mettendo il pezzo condiviso nel modulo da cui tutti gli altri dipendono,
 * non allentando il test.
 */
class ArchitetturaModuliTest {

    @Test
    void imoduliNonHannoCicli() {
        ApplicationModules.of(TrovaCampoApplication.class).verify();
    }
}
