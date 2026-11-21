-- Massa de dados da demo: 2.000 clientes x 400 pedidos = 800 mil pedidos.
-- Cerca de 2% dos códigos são inválidos, para a validação por regex ter trabalho de verdade.

CREATE TABLE cliente (
    id   BIGINT PRIMARY KEY,
    nome TEXT NOT NULL
);

CREATE TABLE pedido (
    id             BIGSERIAL PRIMARY KEY,
    cliente_id     BIGINT NOT NULL REFERENCES cliente (id),
    codigo         TEXT   NOT NULL,
    categoria      TEXT   NOT NULL,
    valor_centavos BIGINT NOT NULL
);

INSERT INTO cliente (id, nome)
SELECT g, 'Cliente ' || g
FROM generate_series(1, 2000) AS g;

INSERT INTO pedido (cliente_id, codigo, categoria, valor_centavos)
SELECT c,
       CASE WHEN random() < 0.02
            THEN 'inv-' || n
            ELSE 'PED-' || lpad(((c - 1) * 400 + n)::text, 6, '0')
       END,
       'CAT-' || lpad(floor(random() * 50)::int::text, 2, '0'),
       100 + floor(random() * 500000)::bigint
FROM generate_series(1, 2000) AS c,
     generate_series(1, 400)  AS n;

CREATE INDEX idx_pedido_cliente ON pedido (cliente_id);
ANALYZE;
