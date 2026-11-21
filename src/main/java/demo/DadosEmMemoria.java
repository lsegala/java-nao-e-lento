package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Massa de dados da demo, gerada na memória quando a aplicação sobe.
 *
 * Substitui o banco de dados de propósito: nenhum dos seis gargalos está no banco, e um
 * Postgres rodando na mesma máquina só disputaria CPU com a JVM e adicionaria ruído.
 * A semente é fixa, então os dados são sempre os mesmos: 2.000 clientes x 1.000 pedidos,
 * com cerca de 2% de códigos inválidos para a validação por regex ter trabalho de verdade.
 */
@Component
public class DadosEmMemoria {

    private static final long SEMENTE = 42;
    private static final int CATEGORIAS = 50;

    private final List<List<Pedido>> pedidosPorCliente;

    public DadosEmMemoria(@Value("${demo.clientes:2000}") int clientes,
                          @Value("${demo.pedidos-por-cliente:1000}") int pedidosPorCliente) {
        this.pedidosPorCliente = gerar(clientes, pedidosPorCliente);
    }

    public Optional<String> nomeDoCliente(long clienteId) {
        if (clienteId < 1 || clienteId > pedidosPorCliente.size()) {
            return Optional.empty();
        }
        return Optional.of("Cliente " + clienteId);
    }

    /** Lista imutável: pode ser lida por muitas threads ao mesmo tempo sem lock. */
    public List<Pedido> pedidosDoCliente(long clienteId) {
        return pedidosPorCliente.get((int) clienteId - 1);
    }

    public int quantidadeDeClientes() {
        return pedidosPorCliente.size();
    }

    static List<List<Pedido>> gerar(int clientes, int pedidosPorCliente) {
        Random random = new Random(SEMENTE);

        String[] categorias = new String[CATEGORIAS];
        for (int i = 0; i < CATEGORIAS; i++) {
            categorias[i] = String.format("CAT-%02d", i);
        }

        List<List<Pedido>> todos = new ArrayList<>(clientes);
        long id = 0;
        for (int cliente = 1; cliente <= clientes; cliente++) {
            List<Pedido> pedidos = new ArrayList<>(pedidosPorCliente);
            for (int n = 1; n <= pedidosPorCliente; n++) {
                id++;
                String codigo = random.nextInt(50) == 0
                        ? "inv-" + n
                        : "PED-" + String.format("%06d", id % 1_000_000);
                String categoria = categorias[random.nextInt(CATEGORIAS)];
                long valorCentavos = 100 + random.nextInt(500_000);
                pedidos.add(new Pedido(id, codigo, categoria, valorCentavos));
            }
            todos.add(List.copyOf(pedidos));
        }
        return List.copyOf(todos);
    }
}
