package demo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoRepository {

    private final JdbcClient jdbc;

    public PedidoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> nomeDoCliente(long clienteId) {
        return jdbc.sql("select nome from cliente where id = :id")
                .param("id", clienteId)
                .query(String.class)
                .optional();
    }

    public List<Pedido> pedidosDoCliente(long clienteId) {
        return jdbc.sql("""
                        select id, codigo, categoria, valor_centavos
                          from pedido
                         where cliente_id = :id
                         order by id
                        """)
                .param("id", clienteId)
                .query(Pedido.class)
                .list();
    }
}
