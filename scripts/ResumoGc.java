import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * Resume uma gravação JFR nas colunas "Alocação" e "Pausas GC" do slide 22.
 *
 * Rode direto do código-fonte, sem compilar (JDK 21+), em Windows, Linux ou macOS:
 *   java scripts/ResumoGc.java resultados/rodada-0.jfr
 *   java scripts/ResumoGc.java resultados/*.jfr        (vários de uma vez, no bash)
 *
 * De onde vêm os números:
 *   - Alocação: soma dos pesos dos eventos jdk.ObjectAllocationSample, que é a estimativa
 *     do próprio JFR para o total de bytes alocados, dividida pela duração da gravação.
 *     Para conferir, também mostra a estimativa pelo heap (antes de cada GC menos depois do anterior).
 *   - Pausas: eventos jdk.GarbageCollection (campos sumOfPauses e longestPause), que existem
 *     tanto no G1 quanto no ZGC. No ZGC também conta os eventos jdk.ZAllocationStall.
 */
public class ResumoGc {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java scripts/ResumoGc.java <arquivo.jfr> [outros.jfr ...]");
            System.exit(1);
        }
        for (String arquivo : args) {
            resumir(Path.of(arquivo));
        }
    }

    static void resumir(Path arquivo) throws Exception {
        Instant inicio = null;
        Instant fim = null;
        long bytesAmostrados = 0;
        String coletor = "?";
        int coletas = 0;
        int travamentos = 0;
        Duration pausaTotal = Duration.ZERO;
        Duration maiorPausa = Duration.ZERO;
        Map<Integer, Long> heapAntes = new HashMap<>();
        Map<Integer, Long> heapDepois = new HashMap<>();

        try (RecordingFile gravacao = new RecordingFile(arquivo)) {
            while (gravacao.hasMoreEvents()) {
                RecordedEvent evento = gravacao.readEvent();
                if (inicio == null || evento.getStartTime().isBefore(inicio)) inicio = evento.getStartTime();
                if (fim == null || evento.getEndTime().isAfter(fim)) fim = evento.getEndTime();

                switch (evento.getEventType().getName()) {
                    case "jdk.ObjectAllocationSample" -> bytesAmostrados += evento.getLong("weight");
                    case "jdk.GarbageCollection" -> {
                        coletas++;
                        coletor = evento.getString("name");
                        Duration soma = evento.getDuration("sumOfPauses");
                        Duration maior = evento.getDuration("longestPause");
                        pausaTotal = pausaTotal.plus(soma);
                        if (maior.compareTo(maiorPausa) > 0) maiorPausa = maior;
                    }
                    case "jdk.ZAllocationStall" -> travamentos++;
                    case "jdk.GCHeapSummary" -> {
                        int gcId = evento.getInt("gcId");
                        long usado = evento.getLong("heapUsed");
                        if ("Before GC".equals(evento.getString("when"))) heapAntes.put(gcId, usado);
                        else heapDepois.put(gcId, usado);
                    }
                    default -> { }
                }
            }
        }

        double segundos = Duration.between(inicio, fim).toMillis() / 1000.0;
        double mbPorSegundo = bytesAmostrados / 1_048_576.0 / segundos;

        // Estimativa pelo heap: o que cresceu entre o fim de uma coleta e o início da seguinte.
        long bytesPeloHeap = 0;
        for (Map.Entry<Integer, Long> antes : heapAntes.entrySet()) {
            Long depoisDaAnterior = heapDepois.get(antes.getKey() - 1);
            if (depoisDaAnterior != null && antes.getValue() > depoisDaAnterior) {
                bytesPeloHeap += antes.getValue() - depoisDaAnterior;
            }
        }

        System.out.println("== " + arquivo.getFileName() + " (" + formatar("%.0f", segundos) + " s gravados)");
        System.out.println("   Alocação:   " + formatar("%,.0f", mbPorSegundo) + " MB/s"
                + "   (pelo heap: " + formatar("%,.0f", bytesPeloHeap / 1_048_576.0 / segundos) + " MB/s)");
        System.out.println("   Pausas GC:  " + ms(pausaTotal) + " no total, "
                + coletas + " coletas, maior pausa " + ms(maiorPausa) + "   [" + coletor + "]");
        if ("Z".equals(coletor) || travamentos > 0) {
            // Allocation stall: a aplicação alocou mais rápido do que o ZGC liberou e a thread teve de esperar.
            System.out.println("   Allocation stalls (ZGC): " + travamentos);
        }
        System.out.println("   Slide 22 -> Alocação: " + formatar("%,.0f", mbPorSegundo) + " MB/s | Pausas GC: "
                + ms(pausaTotal));
    }

    /** Milissegundos com casas decimais suficientes: pausas do ZGC ficam em centésimos de ms. */
    static String ms(Duration duracao) {
        double valor = duracao.toNanos() / 1e6;
        String formato = valor >= 100 ? "%,.0f" : valor >= 1 ? "%.1f" : "%.3f";
        return formatar(formato, valor) + " ms";
    }

    static String formatar(String formato, double valor) {
        return String.format(Locale.of("pt", "BR"), formato, valor);
    }
}
