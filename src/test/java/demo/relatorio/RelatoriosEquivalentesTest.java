package demo.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import demo.Cotacao;
import demo.Pedido;

/**
 * Garante que a otimização não mudou o resultado: as duas versões geram o mesmo texto.
 * Bom para mostrar no palco: "otimizar sem teste é só trocar um bug de lugar".
 */
class RelatoriosEquivalentesTest {

    @Test
    void asDuasVersoesGeramOMesmoTexto() {
        List<Pedido> pedidos = pedidosDeExemplo(400, 42);
        Cotacao cotacao = new Cotacao("USD", 5.37);

        String lento = new RelatorioLento().montar("Cliente 7", cotacao, pedidos);
        String otimizado = new RelatorioOtimizado().montar("Cliente 7", cotacao, pedidos);

        assertEquals(lento, otimizado);
    }

    @Test
    void codigosInvalidosFicamDeFora() {
        List<Pedido> pedidos = List.of(
                new Pedido(1, "PED-000001", "CAT-01", 1050),
                new Pedido(2, "inv-2", "CAT-02", 999),
                new Pedido(3, "PED-000003", "CAT-01", 5));

        String relatorio = new RelatorioOtimizado().montar("Cliente 1", new Cotacao("USD", 5.0), pedidos);

        assertTrue(relatorio.contains("PED-000001;Categoria 01;10,50\n"));
        assertTrue(relatorio.contains("PED-000003;Categoria 01;0,05\n"));
        assertTrue(relatorio.contains("Pedidos válidos: 2 | inválidos: 1"));
        assertTrue(relatorio.contains("Total: R$ 10,55"));
    }

    static List<Pedido> pedidosDeExemplo(int quantidade, long semente) {
        Random random = new Random(semente);
        List<Pedido> pedidos = new ArrayList<>(quantidade);
        for (int i = 1; i <= quantidade; i++) {
            String codigo = random.nextInt(50) == 0 ? "inv-" + i : String.format("PED-%06d", i);
            String categoria = String.format("CAT-%02d", random.nextInt(50));
            long valor = 100 + random.nextInt(500_000);
            pedidos.add(new Pedido(i, codigo, categoria, valor));
        }
        return pedidos;
    }
}
