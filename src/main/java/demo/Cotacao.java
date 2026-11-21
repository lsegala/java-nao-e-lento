package demo;

/** Resposta do serviço externo: quantos reais vale uma unidade da moeda. */
public record Cotacao(String moeda, double taxa) {
}
