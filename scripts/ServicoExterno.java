import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Serviço externo "lento" da demo: responde GET /cotacao/{id} depois de um atraso fixo.
 *
 * Rode direto do código-fonte, sem compilar (JDK 21+):
 *   java scripts/ServicoExterno.java            # porta 9090, 150 ms
 *   java scripts/ServicoExterno.java 9090 200   # porta e atraso customizados
 *
 * Detalhe para contar no palco: ele aguenta milhares de requests simultâneos
 * porque cada request roda numa virtual thread. São só algumas linhas de Java.
 */
public class ServicoExterno {

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        long atrasoMs = args.length > 1 ? Long.parseLong(args[1]) : 150;

        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 4096);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        servidor.createContext("/cotacao/", troca -> {
            try (troca) {
                Thread.sleep(atrasoMs);
                String id = troca.getRequestURI().getPath().substring("/cotacao/".length());
                double taxa = 5.0 + Math.floorMod(id.hashCode(), 100) / 100.0;
                byte[] corpo = ("{\"moeda\":\"USD\",\"taxa\":" + taxa + "}").getBytes(StandardCharsets.UTF_8);

                troca.getResponseHeaders().set("Content-Type", "application/json");
                troca.sendResponseHeaders(200, corpo.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        servidor.start();
        System.out.printf("Serviço externo em http://localhost:%d/cotacao/{id} com atraso de %d ms%n", porta, atrasoMs);
    }
}
