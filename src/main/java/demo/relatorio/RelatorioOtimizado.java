package demo.relatorio;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import demo.Cotacao;
import demo.Pedido;

/**
 * Versão corrigida (rodadas 1 em diante). Gera exatamente o mesmo texto que RelatorioLento.
 */
public class RelatorioOtimizado implements MontadorDeRelatorio {

    // CORREÇÃO 3: compila a expressão regular uma única vez.
    private static final Pattern CODIGO_VALIDO = Pattern.compile(Formatos.REGEX_CODIGO_VALIDO);

    // CORREÇÃO 2: mapa feito para acesso concorrente; leituras não bloqueiam.
    private final ConcurrentHashMap<String, String> cacheDeCategorias = new ConcurrentHashMap<>();

    @Override
    public String montar(String cliente, Cotacao cotacao, List<Pedido> pedidos) {
        // CORREÇÃO 1: um StringBuilder com capacidade estimada; nada é copiado a cada linha.
        StringBuilder relatorio = new StringBuilder(256 + pedidos.size() * 40);
        relatorio.append("Relatório do cliente ").append(cliente).append('\n')
                .append("Cotação: 1 ").append(cotacao.moeda()).append(" = ").append(cotacao.taxa()).append(" BRL\n")
                .append("codigo;categoria;valor_brl\n");

        // CORREÇÃO 4: array de primitivos; nenhum objeto por valor.
        long[] valores = new long[pedidos.size()];
        int validos = 0;
        int invalidos = 0;
        long total = 0;

        for (Pedido pedido : pedidos) {
            if (!CODIGO_VALIDO.matcher(pedido.codigo()).matches()) {
                invalidos++;
                continue;
            }
            String categoria = cacheDeCategorias.computeIfAbsent(pedido.categoria(), Formatos::descreverCategoria);
            long valor = pedido.valorCentavos();
            valores[validos++] = valor;
            total += valor;

            relatorio.append(pedido.codigo()).append(';').append(categoria).append(';');
            Formatos.reais(relatorio, valor);
            relatorio.append('\n');
        }

        Arrays.sort(valores, 0, validos);
        long mediana = validos == 0 ? 0 : valores[validos / 2];
        long maior = validos == 0 ? 0 : valores[validos - 1];
        long totalNaMoeda = Math.round(total / cotacao.taxa());

        relatorio.append("Pedidos válidos: ").append(validos).append(" | inválidos: ").append(invalidos).append('\n')
                .append("Total: R$ ");
        Formatos.reais(relatorio, total);
        relatorio.append(" | Total em ").append(cotacao.moeda()).append(": ");
        Formatos.reais(relatorio, totalNaMoeda);
        relatorio.append(" | Mediana: R$ ");
        Formatos.reais(relatorio, mediana);
        relatorio.append(" | Maior: R$ ");
        Formatos.reais(relatorio, maior);
        relatorio.append('\n');
        return relatorio.toString();
    }
}
