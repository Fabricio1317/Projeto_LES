// Testes funcionais automatizados (Cypress) das regras de vendas implementadas, mas
// FORA DO ESCOPO da apresentação da fase "criação de pedido":
// validação do pagamento e mudanças de status após a finalização (RN0037, RN0038,
// RF0039, RF0040, RN0039, RN0040), baixa em estoque (RF0053, RN0028), bloqueio e
// expiração de itens no carrinho (RN0032, RN0044, RN0045, RNF0042), além de RF0025
// (transações do cliente) e RN0027 (ranking), que dependem dessas etapas.
// A suíte apresentada nesta fase é a pedido-caminho-feliz.cy.js.

const {
  API, PRAZO_PADRAO, CARTAO_RECUSADO, unico,
  criarCliente, criarCartao, criarJogo, definirPrazoCarrinho, ajustarEstoqueViaApi,
  abrirLoja, linhaJogo, adicionarAoCarrinho, linhaCarrinho, abrirCheckout, pagarComPrimeiroCartaoCadastrado,
  aplicarCupomPorCodigo, comprarViaInterface, abrirGestaoVendas, linhaPedido, executarAcaoNoPedido,
  linhaEstoque, abrirConsultaDoCliente,
} = require("../support/vendas-helpers");

describe("Vendas — etapas posteriores à finalização e bloqueio do carrinho", () => {

  beforeEach(() => {
    definirPrazoCarrinho(PRAZO_PADRAO);
  });

  after(() => {
    definirPrazoCarrinho(PRAZO_PADRAO);
  });

  // =====================================================================
  // RN0037 / RN0038 / RF0039 / RF0040 — ciclo de status após a finalização
  // =====================================================================
  describe("RN0037 / RN0038 / RF0039 / RF0040 — Ciclo de status da compra", () => {

    it("RN0038 → RF0039 → RF0040 — compra EM PROCESSAMENTO passa a APROVADA, EM TRANSPORTE e ENTREGUE", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            abrirGestaoVendas();
            linhaPedido(codigo).find("[data-cy=status-pedido]").should("have.text", "EM PROCESSAMENTO");
            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "APROVADA");
            executarAcaoNoPedido(codigo, "btn-despachar", "EM TRANSPORTE");
            executarAcaoNoPedido(codigo, "btn-confirmar-entrega", "ENTREGUE");
            linhaPedido(codigo).find("button").should("not.exist");
          });
        });
      });
    });

    it("RN0037 / RN0038 — compra com cartão recusado pela operadora fica REPROVADA e os itens são liberados", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_RECUSADO);
        criarJogo({ estoque: 10 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            abrirGestaoVendas();
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "8");

            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "REPROVADA");
            linhaPedido(codigo).find("[data-cy=motivo-reprovacao]").should("contain.text", "recusado pela operadora");
            linhaPedido(codigo).find("[data-cy=btn-despachar]").should("not.exist");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "10");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "10");
          });
        });
      });
    });

    it("RN0037 — não deve aceitar cupom vencido nem cupom inexistente", () => {
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

    it("RF0039 / RN0039 — somente compras aprovadas podem ser despachadas", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 1).then((codigo) => {
            abrirGestaoVendas();
            linhaPedido(codigo).find("[data-cy=btn-despachar]").should("not.exist");
            cy.request(`${API}/pedidos`).its("body").then((pedidos) => {
              const pedido = pedidos.find((p) => p.codigo === codigo);
              cy.request({ method: "PATCH", url: `${API}/pedidos/${pedido.id}/despachar`, failOnStatusCode: false })
                .its("body.codigoRegra").should("eq", "RN0039");
            });
            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "APROVADA");
            executarAcaoNoPedido(codigo, "btn-despachar", "EM TRANSPORTE");
          });
        });
      });
    });

    it("RF0040 / RN0040 — somente compras em transporte têm a entrega confirmada", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 1).then((codigo) => {
            abrirGestaoVendas();
            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "APROVADA");
            linhaPedido(codigo).find("[data-cy=btn-confirmar-entrega]").should("not.exist");
            executarAcaoNoPedido(codigo, "btn-despachar", "EM TRANSPORTE");
            executarAcaoNoPedido(codigo, "btn-confirmar-entrega", "ENTREGUE");
          });
        });
      });
    });
  });

  // =====================================================================
  // RN0028 / RF0053 — Baixa em estoque
  // =====================================================================
  describe("RN0028 / RF0053 — Baixa em estoque", () => {

    it("RN0028 / RF0053 — a baixa no estoque só acontece quando a compra é aprovada", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo({ estoque: 10 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            abrirGestaoVendas();
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "10");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "8");

            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "APROVADA");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-fisico]").should("have.text", "8");
            linhaEstoque(jogo.titulo).find("[data-cy=estoque-disponivel]").should("have.text", "8");
          });
        });
      });
    });
  });

  // =====================================================================
  // RN0044 / RN0045 / RNF0042 / RN0032 — Bloqueio e revalidação do carrinho
  // =====================================================================
  describe("RN0044 / RN0045 / RNF0042 / RN0032 — Bloqueio e revalidação do carrinho", () => {

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

    it("RN0044 — deve notificar o cliente quando faltarem 5 minutos ou menos para o bloqueio expirar", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          linhaCarrinho(jogo.titulo).should("exist");
          cy.get("[data-cy=carrinho-alerta-expiracao]").should("not.be.visible");

          definirPrazoCarrinho(240);
          adicionarAoCarrinho(jogo.titulo, 1);
          cy.get("[data-cy=carrinho-alerta-expiracao]").should("be.visible").and("contain.text", "faltam");
        });
      });
    });

    it("RN0044 / RN0045 / RNF0042 — ao expirar o prazo, os itens são liberados, exibidos como removidos e a compra fica desabilitada", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 5 }).then((jogo) => {
          definirPrazoCarrinho(3);
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          linhaCarrinho(jogo.titulo).should("exist");

          cy.get("[data-cy=carrinho-removidos]", { timeout: 10000 }).should("be.visible");
          cy.contains("[data-cy=linha-removido]", jogo.titulo).should("exist");
          cy.get("[data-cy=msg-itens-removidos]").should("be.visible").and("contain.text", "prazo de bloqueio de");
          cy.get("[data-cy=aviso-carrinho]").should("contain.text", "RN0044");
          cy.get("[data-cy=linha-carrinho]").should("not.exist");
          cy.get("[data-cy=btn-comprar]").should("be.disabled");
          linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "5");

          definirPrazoCarrinho(PRAZO_PADRAO);
          cy.contains("[data-cy=linha-removido]", jogo.titulo).find("[data-cy=btn-readicionar]").click();
          cy.get("[data-cy=carrinho-removidos]").should("not.be.visible");
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "2");
          cy.get("[data-cy=btn-comprar]").should("not.be.disabled");
        });
      });
    });

    it("RN0032 — deve ajustar a quantidade e notificar o cliente quando o estoque diminuir antes da finalização", () => {
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

    it("RN0032 — deve remover do carrinho, com alerta, o item que ficou indisponível", () => {
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
  // RF0025 / RN0027 — Reflexos da compra no cadastro do cliente
  // =====================================================================
  describe("RF0025 / RN0027 — Transações e ranking do cliente", () => {

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
              .and("contain.text", "R$ 115,00")
              .and("contain.text", "EM PROCESSAMENTO");
          });
        });
      });
    });

    it("RN0027 — o ranking do cliente é atualizado conforme o perfil de compra (1 ponto a cada R$ 100,00 aprovados)", () => {
      const nome = `Comprador Ranking ${unico()}`;
      criarCliente(nome).then((cliente) => {
        criarCartao(cliente.id);
        // 3 × R$ 100,00 + frete SP (R$ 10,00 + 1,5 kg × R$ 5,00) = R$ 317,50 → ranking 3
        criarJogo({ preco: 100, estoque: 10, pesoKg: 0.5 }).then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 3).then((codigo) => {
            abrirConsultaDoCliente(nome);
            cy.contains("[data-cy=linha-cliente]", nome).find("td").eq(4).should("have.text", "0");

            abrirGestaoVendas();
            executarAcaoNoPedido(codigo, "btn-validar-pagamento", "APROVADA");

            abrirConsultaDoCliente(nome);
            cy.contains("[data-cy=linha-cliente]", nome).find("td").eq(4).should("have.text", "3");
          });
        });
      });
    });
  });
});
