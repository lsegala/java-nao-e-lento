package demo.relatorio;

import java.util.List;

import demo.Cotacao;
import demo.Pedido;

/**
 * Monta o texto do relatório a partir de dados já carregados.
 * É uma função pura (sem banco, sem rede): dá para testar e medir com JMH isoladamente.
 * As duas implementações geram exatamente o mesmo texto; só muda o desempenho.
 */
public interface MontadorDeRelatorio {

    String montar(String cliente, Cotacao cotacao, List<Pedido> pedidos);
}
