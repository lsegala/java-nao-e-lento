package demo;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import demo.relatorio.MontadorDeRelatorio;
import demo.relatorio.RelatorioLento;
import demo.relatorio.RelatorioOtimizado;

/**
 * Busca os dados e monta o relatório.
 * A versão do montador vem da propriedade demo.modo (lento | otimizado), então trocar de
 * rodada não exige recompilar: basta reiniciar com outro argumento.
 */
@Service
public class RelatorioService {

    private final PedidoRepository repositorio;
    private final CotacaoClient cotacoes;
    private final MontadorDeRelatorio montador;
    private final String modo;

    public RelatorioService(PedidoRepository repositorio,
                            CotacaoClient cotacoes,
                            @Value("${demo.modo:lento}") String modo) {
        this.repositorio = repositorio;
        this.cotacoes = cotacoes;
        this.modo = modo;
        this.montador = switch (modo) {
            case "lento" -> new RelatorioLento();
            case "otimizado" -> new RelatorioOtimizado();
            default -> throw new IllegalArgumentException("demo.modo deve ser 'lento' ou 'otimizado', veio: " + modo);
        };
    }

    public Optional<String> gerar(long clienteId) {
        Optional<String> cliente = repositorio.nomeDoCliente(clienteId);
        if (cliente.isEmpty()) {
            return Optional.empty();
        }
        List<Pedido> pedidos = repositorio.pedidosDoCliente(clienteId);
        Cotacao cotacao = cotacoes.buscar(clienteId);   // ~150 ms esperando a rede (gargalo 5)
        return Optional.of(montador.montar(cliente.get(), cotacao, pedidos));
    }

    public String modo() {
        return modo;
    }
}
