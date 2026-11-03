// Testes funcionais automatizados (Cypress) da análise do histórico de vendas por categoria:
// RF0055 (busca por período comparando categorias), RNF0043 (gráfico de linhas) e RNF0011
// (tempo de resposta), com os critérios da atividade: comparação entre categorias, acesso só do
// administrador, eixo X por mês/ano, somente compras ENTREGUE e valor total em reais.
// Os blocos "Roteiro N" seguem o roteiro mínimo de demonstração. Os valores esperados são
// calculados a partir das compras registradas na base (API de pedidos), e não fixos no teste.
// A aplicação deve estar rodando em http://localhost:8080 (ver README).

const {
  API, unico, criarCliente, criarCartao, criarJogo, comprarViaApi, aguardarStatusPedido,
  abrirGestaoVendas, executarAcaoNoPedido,
} = require("../support/vendas-helpers");

const ADMIN = { "X-Perfil": "ADMINISTRADOR" };

// --------------------- datas ---------------------
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const hoje = new Date();
const inicioDoMes = (mesesAtras) => iso(new Date(hoje.getFullYear(), hoje.getMonth() - mesesAtras, 1));
const fimDoMes = (mesesAtras) => iso(new Date(hoje.getFullYear(), hoje.getMonth() - mesesAtras + 1, 0));
const HOJE = iso(hoje);
const MES_ATUAL = HOJE.slice(0, 7);

function mesesEntre(inicio, fim) {
  const meses = [];
  let [ano, mes] = inicio.slice(0, 7).split("-").map(Number);
  const [anoFim, mesFim] = fim.slice(0, 7).split("-").map(Number);
  while (ano < anoFim || (ano === anoFim && mes <= mesFim)) {
    meses.push(`${ano}-${String(mes).padStart(2, "0")}`);
    mes += 1;
    if (mes > 12) { mes = 1; ano += 1; }
  }
  return meses;
}

const centavos = (valor) => Math.round(Number(valor) * 100);

/**
 * Valor esperado, em centavos, por categoria e mês: soma de preço × quantidade dos itens das compras
 * ENTREGUE com data de compra no período, calculada a partir dos pedidos registrados na base.
 */
function vendasEsperadas(inicio, fim, categorias) {
  return cy.request(`${API}/jogos/estoque`).its("body").then((jogos) => {
    const categoriaDoJogo = new Map(jogos.map((j) => [j.id, j.genero]));
    return cy.request(`${API}/pedidos?status=ENTREGUE`).its("body").then((pedidos) => {
      const esperado = Object.fromEntries(categorias.map((c) => [c, Object.fromEntries(mesesEntre(inicio, fim).map((m) => [m, 0]))]));
      pedidos.filter((p) => p.dataCompra.slice(0, 10) >= inicio && p.dataCompra.slice(0, 10) <= fim).forEach((p) => {
        p.itens.forEach((i) => {
          const categoria = categoriaDoJogo.get(i.jogoId);
          if (esperado[categoria]) esperado[categoria][p.dataCompra.slice(0, 7)] += centavos(i.precoUnitario) * i.quantidade;
        });
      });
      return esperado;
    });
  });
}

// --------------------- ações na interface ---------------------
function abrirAnaliseComoAdministrador() {
  cy.visit("/index.html");
  cy.get("[data-cy=perfil-usuario]").select("ADMINISTRADOR");
  cy.get("[data-cy=nav-analise]").should("be.visible").click();
  cy.get("[data-cy=form-analise]").should("be.visible");
  cy.get("[data-cy=categoria-check]").should("have.length.greaterThan", 0);
}

/** Preenche o período e as categorias e clica em Consultar. */
function enviarConsulta(inicio, fim, categorias) {
  cy.get("[data-cy=analise-inicio]").clear().type(inicio);
  cy.get("[data-cy=analise-fim]").clear().type(fim);
  cy.get("[data-cy=categoria-check]").uncheck({ multiple: true });
  categorias.forEach((c) => cy.get(`[data-cy=categoria-check][value="${c}"]`).check());
  cy.intercept("GET", "/api/analises/vendas-por-categoria*").as("consulta");
  cy.get("[data-cy=btn-consultar-analise]").click();
  cy.wait("@consulta");
}

function consultar(inicio, fim, categorias) {
  enviarConsulta(inicio, fim, categorias);
  cy.get("[data-cy=analise-resultado]").should("be.visible");
}

/** Confere cada mês de cada categoria no gráfico (pontos) e na tabela contra o valor esperado. */
function conferirValores(inicio, fim, categorias) {
  const meses = mesesEntre(inicio, fim);
  cy.get("[data-cy=linha-analise]").should("have.length", meses.length);
  vendasEsperadas(inicio, fim, categorias).then((esperado) => {
    categorias.forEach((categoria) => {
      meses.forEach((mes) => {
        const valor = esperado[categoria][mes];
        cy.get(`[data-cy=ponto][data-categoria="${categoria}"][data-mes="${mes}"]`)
          .should(($p) => expect(centavos($p.attr("data-valor")), `${categoria} ${mes} (gráfico)`).to.eq(valor));
        cy.get(`[data-cy=valor-celula][data-categoria="${categoria}"][data-mes="${mes}"]`)
          .should(($c) => expect(centavos($c.attr("data-valor")), `${categoria} ${mes} (tabela)`).to.eq(valor));
      });
    });
  });
}

/** Compra via API de um jogo de uma categoria criada só para o teste, levada até EM TRANSPORTE. */
function compraEmTransporte(categoria, quantidade) {
  return criarCliente().then((cliente) => criarCartao(cliente.id).then((cartao) =>
    criarJogo({ preco: 100, genero: categoria }).then((jogo) =>
      comprarViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade }]).then((pedido) => {
        aguardarStatusPedido(pedido.id, "APROVADA");
        cy.request("PATCH", `${API}/pedidos/${pedido.id}/despachar`);
        return cy.wrap(pedido);
      }))));
}

describe("Análise de vendas por categoria — RF0055 / RNF0043 / RNF0011", () => {

  const INICIO = inicioDoMes(6);

  // =====================================================================
  // Roteiro 1 — Uma categoria em um período válido
  // =====================================================================
  describe("Roteiro 1 — RF0055 — Consulta de uma categoria", () => {

    it("RF0055 — os valores mensais de uma categoria são iguais à soma das vendas ENTREGUE registradas na base", () => {
      abrirAnaliseComoAdministrador();
      consultar(INICIO, HOJE, ["RPG"]);
      cy.get("[data-cy=legenda-item]").should("have.length", 1).and("contain.text", "RPG");
      cy.get("[data-cy=analise-detalhes-tabela] summary").click();
      conferirValores(INICIO, HOJE, ["RPG"]);
    });

    it("RF0055 — exige um período válido e ao menos uma categoria", () => {
      abrirAnaliseComoAdministrador();
      enviarConsulta(HOJE, INICIO, ["RPG"]);
      cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RF0055").and("contain.text", "data de início");

      enviarConsulta(INICIO, HOJE, []);
      cy.get("[data-cy=mensagem]").should("contain.text", "RF0055").and("contain.text", "ao menos uma categoria");
    });
  });

  // =====================================================================
  // Roteiro 2 — Comparação entre categorias
  // =====================================================================
  describe("Roteiro 2 — RF0055 — Comparação entre categorias", () => {

    it("RF0055 — compara duas ou mais categorias no mesmo período, uma linha por categoria", () => {
      const categorias = ["Aventura", "Esporte", "RPG"];
      abrirAnaliseComoAdministrador();
      consultar(INICIO, HOJE, categorias);
      cy.get("[data-cy=linha-categoria]").should("have.length", 3);
      cy.get("[data-cy=legenda-item]").should("have.length", 3).each(($item, i) => {
        expect($item.attr("data-categoria")).to.eq(categorias[i]);
      });
      conferirValores(INICIO, HOJE, categorias);

      // o total de cada categoria na legenda é a soma dos meses
      vendasEsperadas(INICIO, HOJE, categorias).then((esperado) => {
        categorias.forEach((c) => {
          const total = Object.values(esperado[c]).reduce((a, b) => a + b, 0);
          const texto = `R$ ${(total / 100).toFixed(2).replace(".", ",")}`; // mesmo formato do formatarMoeda da tela
          cy.get(`[data-cy=legenda-item][data-categoria="${c}"] [data-cy=legenda-total]`).should("have.text", texto);
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 3 — Gráfico de linhas em reais por mês
  // =====================================================================
  describe("Roteiro 3 — RNF0043 — Gráfico de linhas", () => {

    it("RNF0043 — apresenta um gráfico de linhas com o eixo X por mês/ano e os valores em reais", () => {
      abrirAnaliseComoAdministrador();
      consultar(INICIO, HOJE, ["Ação", "RPG"]);
      const meses = mesesEntre(INICIO, HOJE);

      cy.get("[data-cy=grafico-linhas]").should("be.visible");
      cy.get("[data-cy=linha-categoria] polyline").should("have.length", 2);
      cy.get("[data-cy=linha-categoria][data-categoria=RPG] [data-cy=ponto]").should("have.length", meses.length);
      cy.get("[data-cy=eixo-x-rotulo]").should("have.length", meses.length).each(($r, i) => {
        const [ano, mes] = meses[i].split("-");
        expect($r.text()).to.eq(`${mes}/${ano}`);
      });
      cy.get("[data-cy=eixo-y-rotulo]").each(($r) => expect($r.text()).to.match(/^R\$ [\d.]+$/));

      // ao passar o mouse, o tooltip mostra o mês e o valor de cada categoria em reais
      cy.get(".captura").trigger("mousemove", "center");
      cy.get("[data-cy=tooltip-grafico]").should("be.visible")
        .and("contain.text", "Ação").and("contain.text", "RPG")
        .invoke("text").should("match", /\d{2}\/\d{4}[\s\S]*R\$/);
    });
  });

  // =====================================================================
  // Roteiro 4 — Novo período, novo resultado
  // =====================================================================
  describe("Roteiro 4 — RF0055 — Alteração do período", () => {

    it("RF0055 — alterar a data de início ou de fim gera um novo resultado no gráfico", () => {
      const categorias = ["Aventura", "RPG"];
      abrirAnaliseComoAdministrador();
      consultar(INICIO, HOJE, categorias);
      cy.get("[data-cy=eixo-x-rotulo]").should("have.length", mesesEntre(INICIO, HOJE).length);

      // nova data de início: só os dois últimos meses e o atual
      const novoInicio = inicioDoMes(2);
      consultar(novoInicio, HOJE, categorias);
      cy.get("[data-cy=eixo-x-rotulo]").should("have.length", 3).first().should("have.text", novoInicio.slice(5, 7) + "/" + novoInicio.slice(0, 4));
      conferirValores(novoInicio, HOJE, categorias);

      // nova data de fim: termina no mês anterior, o mês atual sai do gráfico
      const novoFim = fimDoMes(1);
      consultar(novoInicio, novoFim, categorias);
      cy.get("[data-cy=eixo-x-rotulo]").should("have.length", 2);
      cy.get(`[data-cy=ponto][data-mes="${MES_ATUAL}"]`).should("not.exist");
      conferirValores(novoInicio, novoFim, categorias);
    });
  });

  // =====================================================================
  // Roteiro 5 — Compra que não está ENTREGUE
  // =====================================================================
  describe("Roteiro 5 — Critério de status — Compra não ENTREGUE", () => {

    it("RF0055 — compras com status diferente de ENTREGUE não são contabilizadas no gráfico", () => {
      const categoria = `Categoria Teste ${unico()}`;
      compraEmTransporte(categoria, 2).then(() => {
        // uma compra reprovada da mesma categoria também não entra
        criarCliente().then((cliente) => criarCartao(cliente.id, { numero: "4000000000000000", nomeImpresso: "CLIENTE TESTE",
          bandeira: "VISA", codigoSeguranca: "999" }).then((recusado) => {
          cy.request(`${API}/jogos/estoque`).its("body").then((jogos) => {
            const jogo = jogos.find((j) => j.genero === categoria);
            comprarViaApi(cliente.id, recusado.id, [{ jogoId: jogo.id, quantidade: 1 }])
              .then((pedido) => aguardarStatusPedido(pedido.id, "REPROVADA"));
          });
        }));

        abrirAnaliseComoAdministrador();
        consultar(inicioDoMes(0), HOJE, [categoria]);
        cy.get(`[data-cy=ponto][data-categoria="${categoria}"][data-mes="${MES_ATUAL}"]`).should("have.attr", "data-valor", "0");
        cy.get("[data-cy=analise-vazio]").should("be.visible");
        cy.get(`[data-cy=legenda-item][data-categoria="${categoria}"]`).should("contain.text", "R$ 0,00");
      });
    });
  });

  // =====================================================================
  // Roteiro 6 — Compra que passa a ENTREGUE
  // =====================================================================
  describe("Roteiro 6 — Critério de status — Compra que passa a ENTREGUE", () => {

    it("RF0055 — a compra que passa a ENTREGUE dentro do período aparece na consulta seguinte", () => {
      const categoria = `Categoria Teste ${unico()}`;
      compraEmTransporte(categoria, 2).then((pedido) => {
        abrirAnaliseComoAdministrador();
        consultar(inicioDoMes(0), HOJE, [categoria]);
        cy.get(`[data-cy=ponto][data-categoria="${categoria}"][data-mes="${MES_ATUAL}"]`).should("have.attr", "data-valor", "0");

        // o administrador confirma a entrega na Gestão de vendas
        abrirGestaoVendas();
        executarAcaoNoPedido(pedido.codigo, "btn-confirmar-entrega", "ENTREGUE");

        // 2 × R$ 100,00 = R$ 200,00 no mês atual
        cy.get("[data-cy=nav-analise]").click();
        consultar(inicioDoMes(0), HOJE, [categoria]);
        cy.get(`[data-cy=ponto][data-categoria="${categoria}"][data-mes="${MES_ATUAL}"]`)
          .should(($p) => expect(centavos($p.attr("data-valor"))).to.eq(20000));
        cy.get(`[data-cy=legenda-item][data-categoria="${categoria}"]`).should("contain.text", "R$ 200,00");
      });
    });
  });

  // =====================================================================
  // Roteiro 7 — Acesso somente do administrador
  // =====================================================================
  describe("Roteiro 7 — Critério de acesso — Somente administrador", () => {

    it("o gráfico é acessível ao administrador e negado a outros perfis", () => {
      const url = `${API}/analises/vendas-por-categoria?inicio=${INICIO}&fim=${HOJE}&categorias=RPG`;

      // perfil Cliente: a aba não aparece e a API recusa
      cy.visit("/index.html");
      cy.get("[data-cy=perfil-usuario]").should("have.value", "CLIENTE");
      cy.get("[data-cy=nav-analise]").should("not.be.visible");
      cy.request({ url, headers: { "X-Perfil": "CLIENTE" }, failOnStatusCode: false }).then((resposta) => {
        expect(resposta.status).to.eq(403);
        expect(resposta.body.codigoRegra).to.eq("ACESSO_RESTRITO");
      });
      cy.request({ url, failOnStatusCode: false }).its("status").should("eq", 403);

      // perfil Administrador: a aba aparece e a consulta funciona
      cy.get("[data-cy=perfil-usuario]").select("ADMINISTRADOR");
      cy.get("[data-cy=nav-analise]").should("be.visible").click();
      consultar(INICIO, HOJE, ["RPG"]);
      cy.get("[data-cy=grafico-linhas]").should("be.visible");
      cy.request({ url, headers: ADMIN }).its("status").should("eq", 200);

      // voltar para Cliente com a aba aberta fecha a análise
      cy.get("[data-cy=perfil-usuario]").select("CLIENTE");
      cy.get("[data-cy=nav-analise]").should("not.be.visible");
      cy.get("[data-cy=form-analise]").should("not.be.visible");
    });
  });

  // =====================================================================
  // Roteiro 8 — Tempo de resposta
  // =====================================================================
  describe("Roteiro 8 — RNF0011 — Tempo de resposta", () => {

    it("RNF0011 — a consulta do histórico responde em no máximo 1 segundo", () => {
      cy.request({ url: `${API}/analises/categorias`, headers: ADMIN }).its("body").then((todas) => {
        const categorias = todas.slice(0, 8);
        const inicioAno = inicioDoMes(12);
        const params = categorias.map((c) => `categorias=${encodeURIComponent(c)}`).join("&");
        for (let i = 0; i < 3; i += 1) {
          cy.request({ url: `${API}/analises/vendas-por-categoria?inicio=${inicioAno}&fim=${HOJE}&${params}`, headers: ADMIN })
            .then((resposta) => {
              expect(resposta.status).to.eq(200);
              expect(resposta.duration, "tempo da consulta").to.be.lessThan(1000);
            });
        }

        // na tela, o tempo medido da consulta também fica abaixo de 1 segundo
        abrirAnaliseComoAdministrador();
        consultar(inicioAno, HOJE, categorias.slice(0, 4));
        cy.get("[data-cy=analise-tempo-resposta]").should("contain.text", "Consulta respondida em")
          .invoke("attr", "data-ms").then((ms) => expect(Number(ms)).to.be.lessThan(1000));
      });
    });
  });
});
