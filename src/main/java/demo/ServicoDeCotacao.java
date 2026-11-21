package demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simula a chamada a um serviço externo de cotação que demora ~150 ms para responder.
 *
 * A espera é um Thread.sleep, e não uma chamada de rede de verdade, para a demo rodar
 * num notebook sem Docker nem outro processo disputando CPU. Para a JVM o efeito é o mesmo
 * de esperar um socket: uma platform thread fica presa durante a espera; uma virtual thread
 * desmonta e devolve o carrier para outra.
 *
 * GARGALO 5: a espera é bloqueante. Com platform threads, cada request segura uma das
 * threads do Tomcat parada. Pela Lei de Little, com 200 threads e 150 ms o teto fica em
 * 200 / 0,15 s ≈ 1.333 req/s, com a CPU ociosa.
 * Correção: virtual threads (spring.threads.virtual.enabled=true). Este código não muda.
 */
@Component
public class ServicoDeCotacao {

    private final long latenciaMs;

    public ServicoDeCotacao(@Value("${demo.latencia-externa-ms:150}") long latenciaMs) {
        this.latenciaMs = latenciaMs;
    }

    public Cotacao buscar(long clienteId) {
        try {
            Thread.sleep(latenciaMs);   // "esperando a rede"
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido esperando a cotação", e);
        }
        double taxa = 5.0 + Math.floorMod(Long.hashCode(clienteId), 100) / 100.0;
        return new Cotacao("USD", taxa);
    }

    public long latenciaMs() {
        return latenciaMs;
    }
}
