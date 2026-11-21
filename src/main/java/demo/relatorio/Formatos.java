package demo.relatorio;

/** Formatação compartilhada pelas duas versões, para que o texto final seja idêntico. */
final class Formatos {

    static final String REGEX_CODIGO_VALIDO = "^[A-Z]{3}-\\d{6}$";

    private Formatos() {
    }

    /** "CAT-07" vira "Categoria 07". */
    static String descreverCategoria(String codigo) {
        return "Categoria " + codigo.substring(codigo.indexOf('-') + 1);
    }

    /** 123456 centavos vira "1234,56". */
    static String reais(long centavos) {
        StringBuilder sb = new StringBuilder(16);
        reais(sb, centavos);
        return sb.toString();
    }

    static void reais(StringBuilder sb, long centavos) {
        sb.append(centavos / 100).append(',');
        long resto = centavos % 100;
        if (resto < 10) {
            sb.append('0');
        }
        sb.append(resto);
    }
}
