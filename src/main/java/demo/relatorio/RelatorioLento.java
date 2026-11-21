package demo.relatorio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import demo.Cotacao;
import demo.Pedido;

/**
 * Versão com gargalos plantados (rodada 0).
 * Nada aqui é absurdo: é o tipo de código que passa em code review.
 */
public class RelatorioLento implements MontadorDeRelatorio {

    private final Map<String, String> cacheDeCategorias = new HashMap<>();

    @Override
    public String montar(String cliente, Cotacao cotacao, List<Pedido> pedidos) {
        String relatorio = "Relatório do cliente " + cliente + "\n";
        relatorio += "Cotação: 1 " + cotacao.moeda() + " = " + cotacao.taxa() + " BRL\n";
        relatorio += "codigo;categoria;valor_brl\n";

        List<Long> valores = new ArrayList<>();
        int invalidos = 0;

        for (Pedido pedido : pedidos) {
            // GARGALO 3: a expressão regular é compilada de novo para cada pedido.
            // (String.matches faria a mesma coisa por baixo dos panos.)
            if (!Pattern.compile(Formatos.REGEX_CODIGO_VALIDO).matcher(pedido.codigo()).matches()) {
                invalidos++;
                continue;
            }

            // GARGALO 2: todas as threads disputam o mesmo lock, centenas de vezes por request.
            String categoria = nomeDaCategoria(pedido.categoria());

            // GARGALO 4: cada long vira um objeto Long no heap (autoboxing).
            valores.add(pedido.valorCentavos());

            // GARGALO 1: String é imutável; cada += copia o relatório inteiro até aqui.
            relatorio += pedido.codigo() + ";" + categoria + ";" + Formatos.reais(pedido.valorCentavos()) + "\n";
        }

        long total = 0;
        for (Long valor : valores) {        // unboxing a cada iteração
            total += valor;
        }
        Collections.sort(valores);          // ordena objetos, não primitivos
        long mediana = valores.isEmpty() ? 0 : valores.get(valores.size() / 2);
        long maior = valores.isEmpty() ? 0 : valores.get(valores.size() - 1);
        long totalNaMoeda = Math.round(total / cotacao.taxa());

        relatorio += "Pedidos válidos: " + valores.size() + " | inválidos: " + invalidos + "\n";
        relatorio += "Total: R$ " + Formatos.reais(total)
                + " | Total em " + cotacao.moeda() + ": " + Formatos.reais(totalNaMoeda)
                + " | Mediana: R$ " + Formatos.reais(mediana)
                + " | Maior: R$ " + Formatos.reais(maior) + "\n";
        return relatorio;
    }

    /** GARGALO 2: um método synchronized protegendo um HashMap compartilhado. */
    private synchronized String nomeDaCategoria(String codigo) {
        String nome = cacheDeCategorias.get(codigo);
        if (nome == null) {
            nome = Formatos.descreverCategoria(codigo);
            cacheDeCategorias.put(codigo, nome);
        }
        return nome;
    }
}
