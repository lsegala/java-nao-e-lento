package demo;

/** Uma linha da tabela pedido. O valor é guardado em centavos para evitar ponto flutuante. */
public record Pedido(long id, String codigo, String categoria, long valorCentavos) {
}
