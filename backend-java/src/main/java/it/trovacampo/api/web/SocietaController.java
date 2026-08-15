package it.trovacampo.api.web;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stesso contratto REST dell'API Node (backend/), così i client sono intercambiabili. */
@RestController
@RequestMapping("/api")
public class SocietaController {

    private final SocietaService service;

    public SocietaController(SocietaService service) {
        this.service = service;
    }

    @GetMapping("/societa")
    public List<Societa> cerca(@RequestParam(name = "nome", defaultValue = "") String nome) {
        return service.cerca(nome);
    }

    @GetMapping("/societa/{id}")
    public Societa dettaglio(@PathVariable String id) {
        return service.perId(id).orElseThrow(SocietaNonTrovataException::new);
    }

    @PostMapping("/societa")
    public ResponseEntity<Societa> inserisci(@Valid @RequestBody NuovoCampoRequest richiesta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inserisci(richiesta));
    }
}
