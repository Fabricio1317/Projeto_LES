// Modo apresentação: deixa os testes mais lentos para que quem assiste acompanhe cada ação.
//
// A velocidade é controlada pela variável "lentidao" (em milissegundos de pausa após cada
// clique, digitação, seleção ou marcação). O padrão está em cypress.config.js.
//   Mais lento:   npx cypress run --env lentidao=1000
//   Mais rápido:  npx cypress run --env lentidao=0        (sem pausas, como em integração contínua)

const lentidao = Number(Cypress.env("lentidao")) || 0;

if (lentidao > 0) {
  const pausar = (resultado) =>
    new Cypress.Promise((resolver) => setTimeout(() => resolver(resultado), lentidao));

  // cliques, seleções e marcações: pausa depois de executar a ação
  ["click", "select", "check", "uncheck", "clear"].forEach((comando) => {
    Cypress.Commands.overwrite(comando, (original, assunto, ...args) => original(assunto, ...args).then(pausar));
  });

  // digitação: mais devagar (ms entre as teclas) e com pausa ao terminar
  const atrasoPorTecla = Math.max(20, Math.round(lentidao / 10));
  Cypress.Commands.overwrite("type", (original, assunto, texto, opcoes = {}) =>
    original(assunto, texto, { delay: atrasoPorTecla, ...opcoes }).then(pausar));
}
