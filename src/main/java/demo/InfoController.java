package demo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /info mostra, ao vivo, em que configuração a aplicação está rodando.
 * Útil no palco para provar que a rodada mudou de fato.
 */
@RestController
public class InfoController {

    private final RelatorioService relatorios;

    public InfoController(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Runtime runtime = Runtime.getRuntime();
        List<String> coletores = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName)
                .toList();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("modo", relatorios.modo());
        info.put("threadVirtual", Thread.currentThread().isVirtual());
        info.put("thread", Thread.currentThread().toString());
        info.put("coletores", coletores);
        info.put("java", Runtime.version().toString());
        info.put("processadores", runtime.availableProcessors());
        info.put("heapMaximoMb", runtime.maxMemory() / (1024 * 1024));
        return info;
    }
}
