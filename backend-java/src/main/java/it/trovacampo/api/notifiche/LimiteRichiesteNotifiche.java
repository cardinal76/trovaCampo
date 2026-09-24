package it.trovacampo.api.notifiche;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le iscrizioni alle notifiche sono pubbliche e senza login: una richiesta
 * vera sta sotto i 5 KB anche con trenta squadre seguite, e una più grande
 * si rifiuta prima di leggerla, invece di lasciare a Jackson qualche mega da
 * smontare. Senza lunghezza dichiarata (a pezzi) non si accetta: il sito
 * la dichiara sempre.
 */
@Component
public class LimiteRichiesteNotifiche extends OncePerRequestFilter {

    static final long MASSIMO_RICHIESTA = 16 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest richiesta) {
        String metodo = richiesta.getMethod();
        return !richiesta.getRequestURI().startsWith("/api/notifiche/")
                || !("PUT".equals(metodo) || "POST".equals(metodo) || "DELETE".equals(metodo));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest richiesta, HttpServletResponse risposta, FilterChain catena)
            throws ServletException, IOException {
        long lunghezza = richiesta.getContentLengthLong();
        if (lunghezza > MASSIMO_RICHIESTA) {
            risposta.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }
        if (lunghezza < 0) {
            risposta.sendError(HttpStatus.LENGTH_REQUIRED.value());
            return;
        }
        catena.doFilter(richiesta, risposta);
    }
}
