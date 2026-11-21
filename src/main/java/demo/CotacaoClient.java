package demo;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Chama o serviço externo de cotação, que demora ~150 ms por resposta.
 *
 * GARGALO 5: a chamada é bloqueante. Com platform threads, cada request segura uma das
 * 200 threads do Tomcat parada durante a espera. Pela Lei de Little, o teto fica em
 * 200 threads / 0,15 s ≈ 1.333 req/s, com a CPU ociosa.
 * Correção: virtual threads (spring.threads.virtual.enabled=true). Este código não muda.
 */
@Component
public class CotacaoClient {

    private final RestClient http;

    public CotacaoClient(RestClient.Builder builder, @Value("${demo.externo.url}") String urlBase) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(Duration.ofSeconds(5));

        this.http = builder
                .baseUrl(urlBase)
                .requestFactory(fabrica)
                .build();
    }

    public Cotacao buscar(long clienteId) {
        return http.get()
                .uri("/cotacao/{id}", clienteId)
                .retrieve()
                .body(Cotacao.class);
    }
}
