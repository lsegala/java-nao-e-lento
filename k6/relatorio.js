// Carga da demo: taxa CONSTANTE de requests (constant-arrival-rate).
// Se o servidor não acompanhar, o k6 não "espera": a latência sobe e aparecem
// dropped_iterations. Isso evita coordinated omission (slide 14).
//
// Variáveis (todas opcionais):
//   RATE=1500 DURATION=60s CLIENTES=2000 BASE_URL=http://localhost:8080 VUS=500 MAX_VUS=3000
import http from 'k6/http';
import { check } from 'k6';

const RATE = Number(__ENV.RATE || 1500);
const DURATION = __ENV.DURATION || '60s';
const CLIENTES = Number(__ENV.CLIENTES || 2000);
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(99)', 'max'],
  scenarios: {
    relatorios: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.VUS || 500),
      maxVUs: Number(__ENV.MAX_VUS || 3000),
    },
  },
};

export default function () {
  const cliente = Math.floor(Math.random() * CLIENTES) + 1;
  const resposta = http.get(`${BASE_URL}/relatorios/${cliente}`, { tags: { name: 'GET /relatorios/{cliente}' } });
  check(resposta, { 'status 200': (r) => r.status === 200 });
}
