package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DadosEmMemoriaTest {

    @Test
    void geraSempreOsMesmosDados() {
        List<List<Pedido>> primeira = DadosEmMemoria.gerar(10, 400);
        List<List<Pedido>> segunda = DadosEmMemoria.gerar(10, 400);

        assertEquals(primeira, segunda);
        assertEquals(10, primeira.size());
        assertEquals(400, primeira.get(0).size());
    }

    @Test
    void clienteInexistenteNaoTemNome() {
        DadosEmMemoria dados = new DadosEmMemoria(5, 10);

        assertTrue(dados.nomeDoCliente(0).isEmpty());
        assertTrue(dados.nomeDoCliente(6).isEmpty());
        assertEquals("Cliente 5", dados.nomeDoCliente(5).orElseThrow());
    }
}
