package demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RelatorioController {

    private final RelatorioService relatorios;

    public RelatorioController(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    @GetMapping(value = "/relatorios/{clienteId}", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> relatorio(@PathVariable long clienteId) {
        return relatorios.gerar(clienteId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
