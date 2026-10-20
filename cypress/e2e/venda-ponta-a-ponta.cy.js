// Testes funcionais automatizados (Cypress) do caso de uso de venda de ponta a ponta:
// do carrinho ao fechamento do ciclo de troca, com as mudanças de status feitas pelo
// sistema (validação automática do pagamento) e pelo administrador, a emissão de cupons
// e a movimentação de estoque.
// Os blocos "Roteiro N" seguem o roteiro mínimo de demonstração da atividade e cada teste
// traz no nome os requisitos que valida, na numeração da atividade (ver tabela no README).
// A aplicação deve estar rodando em http://localhost:8080 (ver README).

const {
  API, PRAZO_PADRAO, CARTAO_VISA, CARTAO_MASTER, CARTAO_RECUSADO, unico,
  criarCliente, criarCartao, criarJogo, criarCupomTroca, definirPrazoCarrinho, ajustarEstoqueViaApi,
  comprarViaApi, aguardarStatusPedido, comprarEntregueViaApi, solicitarTrocaViaApi, concluirTrocaViaApi,
  linhaMinhaCompra, statusDaMinhaCompra, statusDoPedido, linhaMinhaTroca, linhaTroca, filtrarTrocas,
  abrirLoja, linhaJogo, adicionarAoCarrinho, linhaCarrinho, abrirCheckout, pagarComPrimeiroCartaoCadastrado,
  aplicarCupomPorCodigo, usarCupomDeTroca, finalizarCompra, comprarViaInterface,
  abrirGestaoVendas, linhaPedido, executarAcaoNoPedido, linhaEstoque, abrirConsultaDoCliente,
} = require("../support/vendas-helpers");

const CODIGO_TROCA = /TRC-\d{4}-[0-9A-F]{8}/;
const CODIGO_CUPOM_TROCA = /TROCA-[0-9A-F]{8}/;

/** RF0040 — abre o pedido em "Minhas compras" e solicita a troca pela interface; devolve o código da troca. */
function solicitarTrocaPelaTela(codigoPedido, { compraInteira = false, quantidades = {} } = {}) {
  linhaMinhaCompra(codigoPedido).find("[data-cy=btn-solicitar-troca]").should("not.be.disabled").click();
  cy.get("[data-cy=modal-troca]").should("be.visible");
  cy.get("#troca-pedido-codigo").should("have.text", codigoPedido);
  if (compraInteira) {
    cy.get("[data-cy=troca-compra-inteira]").check();
    cy.get("[data-cy=troca-qtd]").each((campo) => expect(campo).to.be.disabled);
  }
  Object.entries(quantidades).forEach(([titulo, quantidade]) => {
    cy.contains("[data-cy=linha-item-troca]", titulo).find("[data-cy=troca-qtd]").clear().type(String(quantidade));
  });
  cy.get("[data-cy=btn-confirmar-troca]").click();
  return cy.get("[data-cy=mensagem]")
    .should("contain.text", "Troca solicitada")
    .and("contain.text", "EM TROCA")
    .invoke("text")
    .then((texto) => texto.match(CODIGO_TROCA)[0]);
}

function cupomDaLinha(seletor) {
  return cy.get(seletor).invoke("text").then((texto) => texto.match(CODIGO_CUPOM_TROCA)[0]);
}

describe("Venda ponta a ponta — do carrinho ao fechamento da troca", () => {

  beforeEach(() => {
    definirPrazoCarrinho(PRAZO_PADRAO);
  });

  after(() => {
    definirPrazoCarrinho(PRAZO_PADRAO);
  });

  // =====================================================================
  // Roteiro 1 — Compra aprovada com um cartão
  // =====================================================================
  describe("Roteiro 1 — RF0037 / RN0037 / RN0038 / RF0053 / RN0028 — Compra aprovada com um cartão", () => {

    it("RF0037 / RN0037 / RN0038 / RF0053 / RN0028 — a compra nasce EM PROCESSAMENTO, o sistema valida o pagamento, aprova e dá baixa no estoque", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        criarJogo({ preco: 50, estoque: 10 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            // RF0037 — o sistema registra a compra EM PROCESSAMENTO
            cy.get("[data-cy=mensagem]").should("contain.text", "Status: EM PROCESSAMENTO");
            // RN0037 / RN0038 — a validação automática aprova a compra, sem ação do usuário
            statusDaMinhaCompra(codigo).should("have.text", "APROVADA");

            abrirGestaoVendas();
            statusDoPedido(codigo).should("have.text", "APROVADA");
            linhaPedido(codigo).find(".col-pagamento").should("contain.text", "VISA 1111: R$ 115,00 ✓");
            // RF0053 / RN0028 — baixa no estoque somente com a compra aprovada
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "8");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "8");

            cy.get("[data-cy=nav-auditoria]").click();
            cy.contains("[data-cy=linha-auditoria]", `Compra ${codigo} finalizada`).should("contain.text", "status EM PROCESSAMENTO");
            cy.contains("[data-cy=linha-auditoria]", `Compra ${codigo} alterada para APROVADA`).should("contain.text", "sistema");
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 2 — Dois ou mais cartões, com tentativa abaixo de R$ 10,00
  // =====================================================================
  describe("Roteiro 2 — RF0036 / RN0034 — Compra com dois cartões", () => {

    it("RN0034 — bloqueia valor abaixo de R$ 10,00 em um dos cartões e aprova a compra com no mínimo R$ 10,00 em cada", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        criarCartao(cliente.id, CARTAO_MASTER);
        criarJogo({ preco: 50 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 115,00");

          // tentativa: R$ 110,00 + R$ 5,00 → bloqueada
          cy.get("[data-cy=pagto-cartao-check]").eq(0).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(0).clear().type("110,00");
          cy.get("[data-cy=pagto-cartao-check]").eq(1).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(1).clear().type("5,00");
          cy.get("[data-cy=btn-finalizar-compra]").click();
          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0034");
          cy.get("[data-cy=checkout]").should("be.visible");

          // correção: R$ 100,00 + R$ 15,00 → finalizada e aprovada
          cy.get("[data-cy=pagto-cartao-valor]").eq(0).clear().type("100,00");
          cy.get("[data-cy=pagto-cartao-valor]").eq(1).clear().type("15,00");
          finalizarCompra().then((codigo) => {
            statusDaMinhaCompra(codigo).should("have.text", "APROVADA");
            abrirGestaoVendas();
            statusDoPedido(codigo).should("have.text", "APROVADA");
            linhaPedido(codigo).find(".col-pagamento")
              .should("contain.text", "VISA 1111: R$ 100,00")
              .and("contain.text", "MASTERCARD 4444: R$ 15,00");
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 3 — Cupom promocional e cartão com menos de R$ 10,00
  // =====================================================================
  describe("Roteiro 3 — RF0036 / RN0035 — Cupom promocional e cartão abaixo de R$ 10,00", () => {

    it("RN0035 — com cupom promocional, o único cartão pode pagar menos de R$ 10,00", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        // R$ 15,00 + frete SP (R$ 10,00 + 0,1 kg × R$ 5,00) = R$ 25,50
        criarJogo({ preco: 15, pesoKg: 0.1 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          abrirCheckout();
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 25,50");

          aplicarCupomPorCodigo("PROMO20");
          cy.contains("[data-cy=linha-cupom]", "PROMO20").should("contain.text", "promocional");
          cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 5,50");
          pagarComPrimeiroCartaoCadastrado();
          cy.get("[data-cy=pagto-cartao-valor]").first().should("have.value", "5,50");

          finalizarCompra().then((codigo) => {
            statusDaMinhaCompra(codigo).should("have.text", "APROVADA");
            abrirGestaoVendas();
            linhaPedido(codigo).find(".col-pagamento")
              .should("contain.text", "Cupom PROMO20: R$ 20,00")
              .and("contain.text", "VISA 1111: R$ 5,50");
          });
        });
      });
    });

    it("RN0033 — não permite mais de um cupom promocional na mesma compra", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          aplicarCupomPorCodigo("PROMO10");
          cy.contains("[data-cy=linha-cupom]", "PROMO10").should("exist");
          aplicarCupomPorCodigo("PROMO20");
          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0033");
          cy.get("[data-cy=linha-cupom]").should("have.length", 1);
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 4 — Cupons de troca acima do valor da compra
  // =====================================================================
  describe("Roteiro 4 — RN0036 — Cupons de troca acima do valor da compra", () => {

    it("RN0036 — a compra paga com cupons de troca de valor superior gera um cupom de troca com a diferença", () => {
      criarCliente().then((cliente) => {
        criarCupomTroca(cliente.id, 70).then((cupomA) => {
          criarCupomTroca(cliente.id, 60).then((cupomB) => {
            criarJogo({ preco: 50 }).then((jogo) => {
              abrirLoja(cliente.id);
              adicionarAoCarrinho(jogo.titulo, 2);
              abrirCheckout();
              usarCupomDeTroca(cupomA.codigo);
              usarCupomDeTroca(cupomB.codigo);
              cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 0,00");

              finalizarCompra().then((codigo) => {
                // R$ 130,00 em cupons − R$ 115,00 da compra = R$ 15,00
                cy.get("[data-cy=mensagem]").should("contain.text", "Cupom de troca gerado com a diferença")
                  .and("contain.text", "R$ 15,00");
                statusDaMinhaCompra(codigo).should("have.text", "APROVADA");
                linhaMinhaCompra(codigo).find("[data-cy=cupom-troco-gerado]").should("contain.text", "R$ 15,00");

                // o cupom da diferença fica disponível ao cliente; os usados, não
                cupomDaLinha(`[data-cy=linha-minha-compra][data-codigo="${codigo}"] [data-cy=cupom-troco-gerado]`).then((troco) => {
                  cy.get(`[data-cy=linha-meu-cupom][data-codigo="${troco}"]`).should("contain.text", "R$ 15,00");
                });
                cy.get(`[data-cy=linha-meu-cupom][data-codigo="${cupomA.codigo}"]`).should("not.exist");
                cy.get(`[data-cy=linha-meu-cupom][data-codigo="${cupomB.codigo}"]`).should("not.exist");
              });
            });
          });
        });
      });
    });

    it("RN0036 — não permite o uso desnecessário de cupons além do valor da compra", () => {
      criarCliente().then((cliente) => {
        criarCupomTroca(cliente.id, 120).then((cupomA) => {
          criarCupomTroca(cliente.id, 20).then((cupomB) => {
            criarJogo().then((jogo) => {
              abrirLoja(cliente.id);
              adicionarAoCarrinho(jogo.titulo, 2);
              abrirCheckout();
              usarCupomDeTroca(cupomA.codigo);
              usarCupomDeTroca(cupomB.codigo);
              cy.get("[data-cy=btn-finalizar-compra]").click();
              cy.get("[data-cy=mensagem]").should("have.class", "erro")
                .and("contain.text", "RN0036").and("contain.text", "desnecessário");
              cy.get("[data-cy=linha-minha-compra]").should("not.exist");
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 5 — Compra REPROVADA
  // =====================================================================
  describe("Roteiro 5 — RN0037 / RN0038 / RF0053 / RN0028 — Compra REPROVADA", () => {

    it("RN0038 / RN0028 — a compra recusada pela operadora fica REPROVADA, os itens são desbloqueados e o estoque não sofre baixa", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_RECUSADO);
        criarJogo({ estoque: 10 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            cy.get("[data-cy=mensagem]").should("contain.text", "Status: EM PROCESSAMENTO");
            statusDaMinhaCompra(codigo).should("have.text", "REPROVADA");
            linhaMinhaCompra(codigo).find("[data-cy=btn-solicitar-troca]").should("be.disabled");

            abrirGestaoVendas();
            statusDoPedido(codigo).should("have.text", "REPROVADA");
            linhaPedido(codigo).find("[data-cy=motivo-reprovacao]").should("contain.text", "recusado pela operadora");
            linhaPedido(codigo).find("[data-cy=btn-despachar]").should("not.exist");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "10");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "10");

            // os itens voltam a ficar disponíveis na loja
            abrirLoja(cliente.id);
            linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "10");
          });
        });
      });
    });

    it("RN0037 — não aceita cupom vencido nem cupom inexistente", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          abrirCheckout();
          aplicarCupomPorCodigo("VENCIDO10");
          cy.get("[data-cy=mensagem]").should("contain.text", "RN0037").and("contain.text", "vencido");
          aplicarCupomPorCodigo("NAOEXISTE123");
          cy.get("[data-cy=mensagem]").should("contain.text", "RN0037").and("contain.text", "não existe");
          cy.get("[data-cy=linha-cupom]").should("not.exist");
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 6 — Expiração do bloqueio no carrinho
  // =====================================================================
  describe("Roteiro 6 — RN0044 / RNF0045 / RNF0042 — Expiração do bloqueio no carrinho", () => {

    it("RN0044 / RNF0045 / RNF0042 — ao expirar o bloqueio, os itens saem do carrinho, aparecem como removidos e a compra fica desabilitada", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 5 }).then((jogo) => {
          definirPrazoCarrinho(8);
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          linhaCarrinho(jogo.titulo).should("exist");
          // RNF0045 — inclusões do mesmo produto ficam juntas e são retiradas juntas
          adicionarAoCarrinho(jogo.titulo, 1);
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "2");
          linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "3");

          cy.get("[data-cy=carrinho-removidos]", { timeout: 20000 }).should("be.visible");
          cy.contains("[data-cy=linha-removido]", jogo.titulo).should("contain.text", "2");
          cy.get("[data-cy=msg-itens-removidos]").should("be.visible").and("contain.text", "prazo de bloqueio");
          cy.get("[data-cy=aviso-carrinho]").should("contain.text", "RN0044");
          cy.get("[data-cy=linha-carrinho]").should("not.exist");
          cy.get("[data-cy=btn-comprar]").should("be.disabled");
          linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "5");

          // RNF0042 — os itens voltam ao carrinho e a compra é liberada
          definirPrazoCarrinho(PRAZO_PADRAO);
          cy.contains("[data-cy=linha-removido]", jogo.titulo).find("[data-cy=btn-readicionar]").click();
          cy.get("[data-cy=carrinho-removidos]").should("not.be.visible");
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "2");
          cy.get("[data-cy=btn-comprar]").should("not.be.disabled");
        });
      });
    });

    it("RN0044 — itens no carrinho de um cliente ficam bloqueados para os demais clientes", () => {
      criarCliente().then((clienteA) => {
        criarCliente().then((clienteB) => {
          criarJogo({ estoque: 2 }).then((jogo) => {
            cy.request("POST", `${API}/clientes/${clienteA.id}/carrinho/itens`, { jogoId: jogo.id, quantidade: 2 });
            abrirLoja(clienteB.id);
            linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "0");
            adicionarAoCarrinho(jogo.titulo, 1);
            cy.get("[data-cy=mensagem]").should("contain.text", "RN0031");
            cy.get("[data-cy=linha-carrinho]").should("not.exist");
          });
        });
      });
    });

    it("RN0044 — avisa o cliente quando faltam 5 minutos ou menos para o bloqueio expirar", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          linhaCarrinho(jogo.titulo).should("exist");
          cy.get("[data-cy=carrinho-alerta-expiracao]").should("not.be.visible");

          // o prazo é contado a partir do último item incluído
          definirPrazoCarrinho(240);
          adicionarAoCarrinho(jogo.titulo, 1);
          cy.get("[data-cy=carrinho-alerta-expiracao]").should("be.visible").and("contain.text", "faltam");
        });
      });
    });

    it("RN0032 — revalida o estoque na finalização, ajusta a quantidade e avisa o cliente", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo({ estoque: 5 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 4);
          abrirCheckout();
          pagarComPrimeiroCartaoCadastrado();

          ajustarEstoqueViaApi(jogo.id, 2);
          cy.get("[data-cy=btn-finalizar-compra]").click();

          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0032");
          cy.get("[data-cy=aviso-carrinho]").should("contain.text", "quantidade ajustada para 2");
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "2");
          cy.get("[data-cy=linha-minha-compra]").should("not.exist");
        });
      });
    });

    it("RN0032 — remove do carrinho, com alerta, o item que ficou indisponível", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 5 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          linhaCarrinho(jogo.titulo).should("exist");

          ajustarEstoqueViaApi(jogo.id, 0);
          cy.get("[data-cy=nav-loja]").click();

          cy.get("[data-cy=aviso-carrinho]").should("contain.text", "RN0032").and("contain.text", "indisponível");
          cy.get("[data-cy=linha-carrinho]").should("not.exist");
          cy.get("[data-cy=carrinho-vazio]").should("be.visible");
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 7 — Fluxo do administrador: APROVADA → EM TRANSPORTE → ENTREGUE
  // =====================================================================
  describe("Roteiro 7 — RF0038 / RN0039 / RF0039 / RN0040 — Despacho e entrega", () => {

    it("RF0038 / RF0039 — o administrador despacha a compra APROVADA (EM TRANSPORTE) e confirma a entrega (ENTREGUE)", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            abrirGestaoVendas();
            statusDoPedido(codigo).should("have.text", "APROVADA");
            linhaPedido(codigo).find("[data-cy=btn-confirmar-entrega]").should("not.exist");

            executarAcaoNoPedido(codigo, "btn-despachar", "EM TRANSPORTE");
            linhaPedido(codigo).find("[data-cy=btn-despachar]").should("not.exist");
            executarAcaoNoPedido(codigo, "btn-confirmar-entrega", "ENTREGUE");
            linhaPedido(codigo).find("button").should("not.exist");

            // o cliente acompanha o status e passa a poder solicitar a troca
            abrirLoja(cliente.id);
            statusDaMinhaCompra(codigo).should("have.text", "ENTREGUE");
            linhaMinhaCompra(codigo).find("[data-cy=btn-solicitar-troca]").should("not.be.disabled");
          });
        });
      });
    });

    it("RN0039 / RN0040 — bloqueia o despacho de compra não aprovada e a entrega de compra que não está em transporte", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_RECUSADO).then((recusado) => {
          criarCartao(cliente.id, CARTAO_VISA).then((visa) => {
            criarJogo().then((jogo) => {
              comprarViaApi(cliente.id, recusado.id, [{ jogoId: jogo.id, quantidade: 1 }]).then((pedido) => {
                aguardarStatusPedido(pedido.id, "REPROVADA");
                cy.request({ method: "PATCH", url: `${API}/pedidos/${pedido.id}/despachar`, failOnStatusCode: false })
                  .its("body.codigoRegra").should("eq", "RN0039");
              });
              comprarViaApi(cliente.id, visa.id, [{ jogoId: jogo.id, quantidade: 1 }]).then((pedido) => {
                aguardarStatusPedido(pedido.id, "APROVADA");
                cy.request({ method: "PATCH", url: `${API}/pedidos/${pedido.id}/confirmar-entrega`, failOnStatusCode: false })
                  .its("body.codigoRegra").should("eq", "RN0040");
              });
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 8 — Troca de pedido que não está ENTREGUE
  // =====================================================================
  describe("Roteiro 8 — RN0043 — Troca de pedido não ENTREGUE", () => {

    it("RN0043 — bloqueia a solicitação de troca de um pedido que não está ENTREGUE", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo().then((jogo) => {
            comprarViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 1 }]).then((pedido) => {
              aguardarStatusPedido(pedido.id, "APROVADA");
              cy.request("PATCH", `${API}/pedidos/${pedido.id}/despachar`);

              abrirLoja(cliente.id);
              statusDaMinhaCompra(pedido.codigo).should("have.text", "EM TRANSPORTE");
              linhaMinhaCompra(pedido.codigo).find("[data-cy=btn-solicitar-troca]")
                .should("be.disabled")
                .and("have.attr", "title").and("contain", "RN0043");

              // mesmo enviando direto à API, a troca é recusada
              cy.request({
                method: "POST", url: `${API}/clientes/${cliente.id}/pedidos/${pedido.id}/trocas`, failOnStatusCode: false,
                body: { compraInteira: true, itens: [] },
              }).then((resposta) => {
                expect(resposta.status).to.eq(422);
                expect(resposta.body.codigoRegra).to.eq("RN0043");
                expect(resposta.body.mensagem).to.contain("EM TRANSPORTE");
              });
              cy.request(`${API}/clientes/${cliente.id}/trocas`).its("body").should("have.length", 0);
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 9 — Troca de um item e troca da compra inteira
  // =====================================================================
  describe("Roteiro 9 — RF0040 / RN0041 — Troca de um item e da compra inteira", () => {

    it("RF0040 / RN0041 — o cliente solicita a troca de um item de um pedido ENTREGUE", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50 }).then((jogoA) => {
            criarJogo({ preco: 80 }).then((jogoB) => {
              comprarEntregueViaApi(cliente.id, cartao.id,
                [{ jogoId: jogoA.id, quantidade: 2 }, { jogoId: jogoB.id, quantidade: 1 }]).then((pedido) => {
                abrirLoja(cliente.id);
                statusDaMinhaCompra(pedido.codigo).should("have.text", "ENTREGUE");

                solicitarTrocaPelaTela(pedido.codigo, { quantidades: { [jogoA.titulo]: 1 } }).then((codigoTroca) => {
                  linhaMinhaTroca(codigoTroca)
                    .should("contain.text", pedido.codigo)
                    .and("contain.text", "Item")
                    .and("contain.text", `1× ${jogoA.titulo}`)
                    .and("contain.text", "R$ 50,00");
                  linhaMinhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "EM TROCA");
                  linhaMinhaTroca(codigoTroca).should("not.contain.text", jogoB.titulo);
                  // a troca de um item não altera os demais itens do pedido
                  statusDaMinhaCompra(pedido.codigo).should("have.text", "ENTREGUE");

                  // o item fica com 1 unidade disponível para nova troca
                  linhaMinhaCompra(pedido.codigo).find("[data-cy=btn-solicitar-troca]").click();
                  cy.contains("[data-cy=linha-item-troca]", jogoA.titulo).find("[data-cy=troca-disponivel]").should("have.text", "1");
                  cy.contains("[data-cy=linha-item-troca]", jogoB.titulo).find("[data-cy=troca-disponivel]").should("have.text", "1");
                  cy.get("#btn-cancelar-troca").click();
                });
              });
            });
          });
        });
      });
    });

    it("RF0040 / RN0041 — o cliente solicita a troca da compra inteira e o pedido passa a EM TROCA", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50 }).then((jogoA) => {
            criarJogo({ preco: 80 }).then((jogoB) => {
              comprarEntregueViaApi(cliente.id, cartao.id,
                [{ jogoId: jogoA.id, quantidade: 2 }, { jogoId: jogoB.id, quantidade: 1 }]).then((pedido) => {
                abrirLoja(cliente.id);
                solicitarTrocaPelaTela(pedido.codigo, { compraInteira: true }).then((codigoTroca) => {
                  linhaMinhaTroca(codigoTroca)
                    .should("contain.text", "Compra inteira")
                    .and("contain.text", `2× ${jogoA.titulo}`)
                    .and("contain.text", `1× ${jogoB.titulo}`)
                    .and("contain.text", "R$ 180,00");
                  linhaMinhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "EM TROCA");
                  statusDaMinhaCompra(pedido.codigo).should("have.text", "EM TROCA");
                  linhaMinhaCompra(pedido.codigo).find("[data-cy=btn-solicitar-troca]").should("be.disabled");
                });
              });
            });
          });
        });
      });
    });

    it("RF0040 / RN0041 — não permite trocar mais unidades do que as compradas nem a compra inteira depois da troca de um item", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo().then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 2 }]).then((pedido) => {
              const item = pedido.itens[0];
              cy.request({
                method: "POST", url: `${API}/clientes/${cliente.id}/pedidos/${pedido.id}/trocas`, failOnStatusCode: false,
                body: { compraInteira: false, itens: [{ pedidoItemId: item.id, quantidade: 3 }] },
              }).its("body.codigoRegra").should("eq", "RF0040");

              solicitarTrocaViaApi(cliente.id, pedido.id, { itens: [{ pedidoItemId: item.id, quantidade: 1 }] });
              cy.request({
                method: "POST", url: `${API}/clientes/${cliente.id}/pedidos/${pedido.id}/trocas`, failOnStatusCode: false,
                body: { compraInteira: true, itens: [] },
              }).its("body.codigoRegra").should("eq", "RN0041");
              cy.request({
                method: "POST", url: `${API}/clientes/${cliente.id}/pedidos/${pedido.id}/trocas`, failOnStatusCode: false,
                body: { compraInteira: false, itens: [{ pedidoItemId: item.id, quantidade: 2 }] },
              }).its("body.mensagem").should("contain", "maior que a disponível para troca (1)");
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 10 — Fluxo completo da troca
  // =====================================================================
  describe("Roteiro 10 — RF0042 / RF0041 / RNF0046 / RF0043 / RN0042 / RF0044 / RF0054 — Fluxo da troca", () => {

    it("RF0042 / RF0041 / RNF0046 / RF0043 / RF0044 / RF0054 — solicitação, visualização, autorização com notificação, recebimento, cupom e reentrada no estoque", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50, estoque: 10 }).then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 2 }]).then((pedido) => {
              // 1) cliente solicita a troca de 1 unidade
              abrirLoja(cliente.id);
              solicitarTrocaPelaTela(pedido.codigo, { quantidades: { [jogo.titulo]: 1 } }).then((codigoTroca) => {

                // 2) RF0042 — o administrador visualiza os pedidos de troca EM TROCA
                abrirGestaoVendas();
                linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "8");
                filtrarTrocas("EM_TROCA");
                linhaTroca(codigoTroca)
                  .should("contain.text", pedido.codigo)
                  .and("contain.text", `#${cliente.id}`)
                  .and("contain.text", `1× ${jogo.titulo}`)
                  .and("contain.text", "R$ 50,00");
                linhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "EM TROCA");

                // 3) RF0041 — autoriza a troca
                linhaTroca(codigoTroca).find("[data-cy=btn-autorizar-troca]").click();
                cy.get("[data-cy=mensagem]").should("contain.text", "autorizada").and("contain.text", "notificado");
                linhaTroca(codigoTroca).should("not.exist");
                filtrarTrocas("TROCA_AUTORIZADA");
                linhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "TROCA AUTORIZADA");

                // 4) RNF0046 — o cliente recebe a notificação da autorização
                abrirLoja(cliente.id);
                cy.get("[data-cy=painel-notificacoes]").should("be.visible");
                cy.get("[data-cy=qtd-notificacoes-nao-lidas]").should("have.text", "1");
                cy.contains("[data-cy=linha-notificacao]", codigoTroca)
                  .should("contain.text", "foi autorizada").and("have.class", "nao-lida");
                linhaMinhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "TROCA AUTORIZADA");
                cy.get("[data-cy=btn-marcar-notificacoes-lidas]").click();
                cy.get("[data-cy=qtd-notificacoes-nao-lidas]").should("have.text", "0");
                cy.contains("[data-cy=linha-notificacao]", codigoTroca).should("have.class", "lida");

                // 5) RF0043 / RN0042 / RF0054 — confirma o recebimento, devolvendo o item ao estoque
                abrirGestaoVendas();
                filtrarTrocas("TROCA_AUTORIZADA");
                linhaTroca(codigoTroca).find("[data-cy=troca-retornar-estoque]").should("be.checked");
                linhaTroca(codigoTroca).find("[data-cy=btn-confirmar-recebimento]").click();
                cy.get("[data-cy=mensagem]").should("contain.text", "recebimento confirmado")
                  .and("contain.text", "TROCADO").and("contain.text", "R$ 50,00");
                filtrarTrocas("TROCADO");
                linhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "TROCADO");
                linhaTroca(codigoTroca).find("[data-cy=troca-cupom]").invoke("text").should("match", CODIGO_CUPOM_TROCA);
                linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "9");

                // 6) RF0044 — o cupom de troca fica disponível para o cliente
                cupomDaLinha(`[data-cy=linha-troca][data-codigo="${codigoTroca}"] [data-cy=troca-cupom]`).then((cupom) => {
                  cy.contains("[data-cy=linha-cupom-admin]", cupom).should("contain.text", "R$ 50,00");
                  abrirLoja(cliente.id);
                  linhaMinhaTroca(codigoTroca).find("[data-cy=status-troca]").should("have.text", "TROCADO");
                  linhaMinhaTroca(codigoTroca).find("[data-cy=troca-cupom-gerado]").should("have.text", cupom);
                  cy.get(`[data-cy=linha-meu-cupom][data-codigo="${cupom}"]`).should("contain.text", "R$ 50,00");
                  cy.contains("[data-cy=linha-notificacao]", cupom).should("contain.text", "está disponível");
                });
              });
            });
          });
        });
      });
    });

    it("RF0041 / RF0043 — na troca da compra inteira, o pedido acompanha os status EM TROCA, TROCA AUTORIZADA e TROCADO", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50, estoque: 10 }).then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 2 }]).then((pedido) => {
              solicitarTrocaViaApi(cliente.id, pedido.id, { compraInteira: true }).then(([troca]) => {
                cy.visit("/index.html");
                abrirGestaoVendas();
                statusDoPedido(pedido.codigo).should("have.text", "EM TROCA");
                linhaTroca(troca.codigo).should("contain.text", "Compra inteira").and("contain.text", "R$ 100,00");

                linhaTroca(troca.codigo).find("[data-cy=btn-autorizar-troca]").click();
                linhaTroca(troca.codigo).find("[data-cy=status-troca]").should("have.text", "TROCA AUTORIZADA");
                statusDoPedido(pedido.codigo).should("have.text", "TROCA AUTORIZADA");

                linhaTroca(troca.codigo).find("[data-cy=btn-confirmar-recebimento]").click();
                linhaTroca(troca.codigo).find("[data-cy=status-troca]").should("have.text", "TROCADO");
                statusDoPedido(pedido.codigo).should("have.text", "TROCADO");
                // as 2 unidades devolvidas voltam ao estoque (8 → 10)
                linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "10");
              });
            });
          });
        });
      });
    });

    it("RF0043 / RN0042 / RF0054 — o recebimento pode ser confirmado sem devolver os itens ao estoque, e só depois da autorização", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50, estoque: 10 }).then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 2 }]).then((pedido) => {
              solicitarTrocaViaApi(cliente.id, pedido.id, { itens: [{ pedidoItemId: pedido.itens[0].id, quantidade: 1 }] })
                .then(([troca]) => {
                  // sem autorização, o recebimento não pode ser confirmado
                  cy.request({ method: "PATCH", url: `${API}/trocas/${troca.id}/confirmar-recebimento`,
                    body: { retornarEstoque: true }, failOnStatusCode: false })
                    .its("body.codigoRegra").should("eq", "RF0043");
                  cy.request("PATCH", `${API}/trocas/${troca.id}/autorizar`);

                  cy.visit("/index.html");
                  abrirGestaoVendas();
                  linhaTroca(troca.codigo).find("[data-cy=troca-retornar-estoque]").uncheck();
                  linhaTroca(troca.codigo).find("[data-cy=btn-confirmar-recebimento]").click();
                  linhaTroca(troca.codigo).find("[data-cy=status-troca]").should("have.text", "TROCADO");
                  linhaTroca(troca.codigo).should("contain.text", "Itens não retornaram ao estoque");
                  linhaTroca(troca.codigo).find("[data-cy=troca-cupom]").invoke("text").should("match", CODIGO_CUPOM_TROCA);
                  linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "8");
                });
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 11 — Uso do cupom de troca gerado em uma nova compra
  // =====================================================================
  describe("Roteiro 11 — RF0044 / RF0036 — Uso do cupom de troca em nova compra", () => {

    it("RF0044 — o cupom gerado pela troca é usado como pagamento em uma nova compra", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ preco: 50 }).then((jogoTrocado) => {
            criarJogo({ preco: 50 }).then((jogoNovo) => {
              comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogoTrocado.id, quantidade: 2 }]).then((pedido) => {
                solicitarTrocaViaApi(cliente.id, pedido.id, { itens: [{ pedidoItemId: pedido.itens[0].id, quantidade: 1 }] })
                  .then(([troca]) => concluirTrocaViaApi(troca.id))
                  .then((trocada) => {
                    const cupom = trocada.cupomGerado;
                    abrirLoja(cliente.id);
                    cy.get(`[data-cy=linha-meu-cupom][data-codigo="${cupom}"]`).should("contain.text", "R$ 50,00");

                    // R$ 50,00 + frete SP (R$ 10,00 + 0,5 kg × R$ 5,00) = R$ 62,50; cupom de R$ 50,00
                    adicionarAoCarrinho(jogoNovo.titulo, 1);
                    abrirCheckout();
                    cy.get("[data-cy=checkout-total]").should("have.text", "R$ 62,50");
                    usarCupomDeTroca(cupom);
                    cy.get("[data-cy=checkout-valor-cupons]").should("have.text", "R$ 50,00");
                    cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 12,50");
                    pagarComPrimeiroCartaoCadastrado();
                    cy.get("[data-cy=pagto-cartao-valor]").first().should("have.value", "12,50");

                    finalizarCompra().then((codigo) => {
                      statusDaMinhaCompra(codigo).should("have.text", "APROVADA");
                      // o cupom foi consumido e não aparece mais para o cliente
                      cy.get(`[data-cy=linha-meu-cupom][data-codigo="${cupom}"]`).should("not.exist");

                      abrirGestaoVendas();
                      linhaPedido(codigo).find(".col-pagamento")
                        .should("contain.text", `Cupom ${cupom}: R$ 50,00`)
                        .and("contain.text", "VISA 1111: R$ 12,50");
                    });
                  });
              });
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Reflexos no cliente e requisitos transversais
  // =====================================================================
  describe("RF0025 / RN0027 / RNF0011 / RNF0012 — Reflexos no cliente e requisitos transversais", () => {

    it("RF0025 — a consulta de clientes exibe as transações realizadas pelo cliente", () => {
      const nome = `Comprador Transacoes ${unico()}`;
      criarCliente(nome).then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            abrirConsultaDoCliente(nome);
            cy.contains("[data-cy=linha-cliente]", nome).find("[data-cy=btn-transacoes]").click();
            cy.get("[data-cy=modal-transacoes]").should("be.visible");
            cy.contains("[data-cy=linha-transacao]", codigo)
              .should("contain.text", jogo.titulo)
              .and("contain.text", "R$ 115,00");
          });
        });
      });
    });

    it("RN0027 — o ranking do cliente é atualizado pela compra aprovada (1 ponto a cada R$ 100,00)", () => {
      const nome = `Comprador Ranking ${unico()}`;
      criarCliente(nome).then((cliente) => {
        criarCartao(cliente.id);
        // 3 × R$ 100,00 + frete SP (R$ 10,00 + 1,5 kg × R$ 5,00) = R$ 317,50 → ranking 3
        criarJogo({ preco: 100, estoque: 10, pesoKg: 0.5 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 3).then((codigo) => {
            statusDaMinhaCompra(codigo).should("have.text", "APROVADA");
            abrirConsultaDoCliente(nome);
            cy.contains("[data-cy=linha-cliente]", nome).find("td").eq(4).should("have.text", "3");
          });
        });
      });
    });

    it("RNF0011 — as consultas do pós-venda (pedidos, trocas, cupons, notificações e estoque) respondem em no máximo 1 segundo", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo().then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 1 }]).then((pedido) => {
              solicitarTrocaViaApi(cliente.id, pedido.id, { compraInteira: true });
              [
                `${API}/clientes/${cliente.id}/pedidos`,
                `${API}/pedidos`,
                `${API}/pedidos/${pedido.id}`,
                `${API}/pedidos?status=ENTREGUE`,
                `${API}/clientes/${cliente.id}/trocas`,
                `${API}/trocas`,
                `${API}/trocas?status=EM_TROCA`,
                `${API}/clientes/${cliente.id}/cupons`,
                `${API}/clientes/${cliente.id}/notificacoes`,
                `${API}/jogos/estoque`,
              ].forEach((url) => {
                cy.request(url).then((resposta) => {
                  expect(resposta.status).to.eq(200);
                  expect(resposta.duration, `GET ${url}`).to.be.lessThan(1000);
                });
              });
            });
          });
        });
      });
    });

    it("RNF0012 — as mudanças de status, a troca, o cupom e a reentrada no estoque são registrados no log de auditoria", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id).then((cartao) => {
          criarJogo({ estoque: 10 }).then((jogo) => {
            comprarEntregueViaApi(cliente.id, cartao.id, [{ jogoId: jogo.id, quantidade: 2 }]).then((pedido) => {
              solicitarTrocaViaApi(cliente.id, pedido.id, { itens: [{ pedidoItemId: pedido.itens[0].id, quantidade: 1 }] })
                .then(([troca]) => concluirTrocaViaApi(troca.id))
                .then((trocada) => {
                  cy.visit("/index.html");
                  cy.get("[data-cy=nav-auditoria]").click();
                  cy.contains("[data-cy=linha-auditoria]", `Compra ${pedido.codigo} alterada para APROVADA`).should("contain.text", "sistema");
                  cy.contains("[data-cy=linha-auditoria]", `Compra ${pedido.codigo} alterada para EM TRANSPORTE`).should("contain.text", "admin");
                  cy.contains("[data-cy=linha-auditoria]", `Compra ${pedido.codigo} alterada para ENTREGUE`).should("contain.text", "admin");
                  cy.contains("[data-cy=linha-auditoria]", `Troca ${trocada.codigo} (Item) solicitada`)
                    .should("contain.text", "INSERT").and("contain.text", `cliente-${cliente.id}`);
                  cy.contains("[data-cy=linha-auditoria]", `Troca ${trocada.codigo} alterada para TROCA AUTORIZADA`).should("contain.text", "admin");
                  cy.contains("[data-cy=linha-auditoria]", `Troca ${trocada.codigo} alterada para TROCADO`).should("contain.text", trocada.cupomGerado);
                  cy.contains("[data-cy=linha-auditoria]", `Reentrada no estoque de ${jogo.titulo}`).should("contain.text", "de 8 para 9");
                  cy.contains("[data-cy=linha-auditoria]", `gerado pela troca ${trocada.codigo}`).should("contain.text", "INSERT");
                });
            });
          });
        });
      });
    });
  });
});
