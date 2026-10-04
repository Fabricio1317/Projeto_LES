// Testes funcionais automatizados (Cypress) da criação de pedido — caminho feliz, da
// inclusão de itens no carrinho até a finalização da compra (status EM PROCESSAMENTO).
// Escopo da atividade: RF0031–RF0038, RN0023–RN0025, RN0031, RN0033–RN0036, RNF0011 e RNF0012.
// Cada teste traz no nome o RF/RN/RNF que valida e os blocos seguem o roteiro mínimo
// da apresentação. A aplicação deve estar rodando em http://localhost:8080 (ver README).

const {
  API, PRAZO_PADRAO, CARTAO_VISA, CARTAO_MASTER, unico,
  criarCliente, criarCartao, criarJogo, criarCupomTroca, definirPrazoCarrinho,
  abrirLoja, linhaJogo, adicionarAoCarrinho, linhaCarrinho, abrirCheckout, pagarComPrimeiroCartaoCadastrado,
  preencherNovoEndereco, preencherNovoCartao, aplicarCupomPorCodigo, usarCupomDeTroca, finalizarCompra,
  comprarViaInterface, abrirGestaoVendas, linhaPedido, abrirConsultaDoCliente,
} = require("../support/vendas-helpers");

describe("Criação de pedido — caminho feliz", () => {

  beforeEach(() => {
    definirPrazoCarrinho(PRAZO_PADRAO);
  });

  // =====================================================================
  // Roteiro 1 — Carrinho de compra (RF0031, RF0032, RN0031)
  // =====================================================================
  describe("Roteiro 1 — RF0031 / RF0032 / RN0031 — Carrinho de compra", () => {

    it("RF0031 / RF0032 — deve incluir mais de um jogo no carrinho, definindo a quantidade na adição e alterando-a na visualização", () => {
      criarCliente().then((cliente) => {
        criarJogo({ preco: 50 }).then((jogoA) => {
          criarJogo({ preco: 80 }).then((jogoB) => {
            abrirLoja(cliente.id);
            adicionarAoCarrinho(jogoA.titulo, 2);
            linhaCarrinho(jogoA.titulo).should("exist");
            adicionarAoCarrinho(jogoB.titulo, 1);

            cy.get("[data-cy=linha-carrinho]").should("have.length", 2);
            linhaCarrinho(jogoA.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "2");
            linhaCarrinho(jogoA.titulo).find("[data-cy=carrinho-item-subtotal]").should("have.text", "R$ 100,00");
            linhaCarrinho(jogoB.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "1");
            cy.get("[data-cy=carrinho-subtotal]").should("have.text", "R$ 180,00");

            linhaCarrinho(jogoB.titulo).find("[data-cy=carrinho-qtd]").clear().type("3");
            linhaCarrinho(jogoB.titulo).find("[data-cy=btn-atualizar-qtd]").click();

            cy.get("[data-cy=mensagem]").should("contain.text", "Quantidade atualizada");
            linhaCarrinho(jogoB.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "3");
            linhaCarrinho(jogoB.titulo).find("[data-cy=carrinho-item-subtotal]").should("have.text", "R$ 240,00");
            cy.get("[data-cy=carrinho-subtotal]").should("have.text", "R$ 340,00");
          });
        });
      });
    });

    it("RF0031 — deve excluir um item do carrinho mantendo os demais", () => {
      criarCliente().then((cliente) => {
        criarJogo({ preco: 50 }).then((jogoA) => {
          criarJogo({ preco: 80 }).then((jogoB) => {
            abrirLoja(cliente.id);
            adicionarAoCarrinho(jogoA.titulo, 1);
            linhaCarrinho(jogoA.titulo).should("exist");
            adicionarAoCarrinho(jogoB.titulo, 1);
            cy.get("[data-cy=linha-carrinho]").should("have.length", 2);

            linhaCarrinho(jogoA.titulo).find("[data-cy=btn-remover-item]").click();

            cy.get("[data-cy=mensagem]").should("contain.text", "removido do carrinho");
            cy.get("[data-cy=linha-carrinho]").should("have.length", 1);
            linhaCarrinho(jogoB.titulo).should("exist");
            cy.get("[data-cy=carrinho-subtotal]").should("have.text", "R$ 80,00");
          });
        });
      });
    });

    it("RN0031 — não deve adicionar quantidade superior à disponível em estoque", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 2 }).then((jogo) => {
          abrirLoja(cliente.id);
          linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "2");
          adicionarAoCarrinho(jogo.titulo, 3);

          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0031");
          cy.get("[data-cy=linha-carrinho]").should("not.exist");
        });
      });
    });

    it("RN0031 — não deve alterar a quantidade no carrinho para acima da disponível em estoque", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 2 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").clear().type("5");
          linhaCarrinho(jogo.titulo).find("[data-cy=btn-atualizar-qtd]").click();

          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0031");
          cy.get("[data-cy=nav-loja]").click();
          linhaCarrinho(jogo.titulo).find("[data-cy=carrinho-qtd]").should("have.value", "1");
        });
      });
    });

    it("RN0031 — não deve adicionar jogo indisponível (sem estoque)", () => {
      criarCliente().then((cliente) => {
        criarJogo({ estoque: 0 }).then((jogo) => {
          abrirLoja(cliente.id);
          linhaJogo(jogo.titulo).find("[data-cy=disponivel-jogo]").should("have.text", "0");
          adicionarAoCarrinho(jogo.titulo, 1);

          cy.get("[data-cy=mensagem]").should("contain.text", "RN0031").and("contain.text", "não possui estoque disponível");
          cy.get("[data-cy=linha-carrinho]").should("not.exist");
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 2 — Compra com endereço e cartão previamente cadastrados
  // =====================================================================
  describe("Roteiro 2 — RF0033 / RF0035 / RF0036 / RF0038 — Endereço e cartão já cadastrados", () => {

    it("RF0033 / RF0035 / RF0036 / RF0038 — deve finalizar uma compra com endereço e cartão previamente cadastrados", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        criarJogo({ preco: 50 }).then((jogoA) => {
          criarJogo({ preco: 80 }).then((jogoB) => {
            abrirLoja(cliente.id);
            adicionarAoCarrinho(jogoA.titulo, 2);
            linhaCarrinho(jogoA.titulo).should("exist");
            adicionarAoCarrinho(jogoB.titulo, 1);
            cy.get("[data-cy=linha-carrinho]").should("have.length", 2);

            abrirCheckout();
            cy.get("[data-cy=checkout-endereco]").find("option:selected").should("contain.text", "Residencial");
            // 1,5 kg para SP: R$ 10,00 + 1,5 × R$ 5,00
            cy.get("[data-cy=checkout-frete]").should("have.text", "R$ 17,50");
            cy.get("[data-cy=checkout-total]").should("have.text", "R$ 197,50");
            pagarComPrimeiroCartaoCadastrado();
            cy.get("[data-cy=pagto-cartao-valor]").first().should("have.value", "197,50");

            finalizarCompra().then((codigo) => {
              cy.get("[data-cy=mensagem]").should("contain.text", "Status: EM PROCESSAMENTO");
              cy.contains("[data-cy=linha-minha-compra]", codigo)
                .should("contain.text", "R$ 197,50")
                .and("contain.text", "EM PROCESSAMENTO");
            });
          });
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 3 — Novo endereço e novo cartão durante a compra
  // =====================================================================
  describe("Roteiro 3 — RF0035 / RF0036 / RN0023 / RN0024 / RN0025 — Novo endereço e novo cartão", () => {

    it("RF0035 / RF0036 — deve finalizar com novo endereço e novo cartão cadastrados durante a compra e incorporá-los ao perfil", () => {
      const nome = `Comprador Novo Endereco Cartao ${unico()}`;
      criarCliente(nome).then((cliente) => {
        criarJogo({ preco: 50 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          cy.get("[data-cy=linha-cartao-pagamento]").should("not.exist");

          preencherNovoEndereco({ apelido: "Casa da Praia", estado: "BA" });
          cy.get("[data-cy=novo-end-salvar]").check();
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 130,00");

          preencherNovoCartao(CARTAO_MASTER);
          cy.get("[data-cy=pagto-novo-valor]").should("have.value", "130,00");
          cy.get("[data-cy=pagto-novo-salvar]").check();
          finalizarCompra().then((codigo) => {
            cy.contains("[data-cy=linha-minha-compra]", codigo).should("contain.text", "EM PROCESSAMENTO");
          });

          abrirConsultaDoCliente(nome);
          cy.get("[data-cy=btn-enderecos]").first().click();
          cy.contains("[data-cy=linha-endereco]", "Casa da Praia").should("contain.text", "ENTREGA").and("contain.text", "Salvador/BA");
          cy.get("[data-cy=btn-fechar-enderecos]").click();
          cy.get("[data-cy=btn-cartoes]").first().click();
          cy.contains("[data-cy=linha-cartao]", "4444").should("contain.text", "MASTERCARD");
        });
      });
    });

    it("RN0023 — não deve finalizar com um novo endereço sem os campos obrigatórios", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 1);
          abrirCheckout();

          cy.get("[data-cy=checkout-endereco]").select("novo");
          cy.get("[data-cy=novo-end-apelido]").type("Incompleto");
          cy.get("[data-cy=novo-end-estado]").type("SP");
          cy.get("[data-cy=checkout-frete]").should("not.have.text", "—");
          pagarComPrimeiroCartaoCadastrado();
          cy.get("[data-cy=btn-finalizar-compra]").click();

          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0023");
          cy.get("[data-cy=linha-minha-compra]").should("not.exist");
        });
      });
    });

    it("RN0024 — não deve finalizar com um novo cartão sem nome impresso e código de segurança", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();

          preencherNovoCartao({ numero: CARTAO_MASTER.numero, bandeira: CARTAO_MASTER.bandeira });
          cy.get("[data-cy=btn-finalizar-compra]").click();

          cy.get("[data-cy=mensagem]").should("have.class", "erro")
            .and("contain.text", "RN0024")
            .and("contain.text", "Nome impresso no cartão é obrigatório")
            .and("contain.text", "Código de segurança é obrigatório");
          cy.get("[data-cy=linha-minha-compra]").should("not.exist");
        });
      });
    });

    it("RN0025 — o novo cartão só aceita bandeiras registradas no sistema", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          cy.get("[data-cy=pagto-novo-check]").check();
          cy.get("[data-cy=pagto-novo-bandeira] option").then((opcoes) => {
            const valores = [...opcoes].map((o) => o.value).filter(Boolean);
            expect(valores).to.deep.equal(["VISA", "MASTERCARD", "ELO", "AMERICAN_EXPRESS", "HIPERCARD"]);
          });

          // mesmo enviando direto à API, uma bandeira não registrada é recusada
          cy.request({
            method: "POST", url: `${API}/clientes/${cliente.id}/pedidos`, failOnStatusCode: false,
            body: { enderecoId: null, novoEndereco: null, cupons: [], cartoes: [{
              novoCartao: { numero: "36000000000008", nomeImpresso: "CLIENTE TESTE", bandeira: "DINERS", codigoSeguranca: "123" },
              valor: 115 }] },
          }).its("body.codigoRegra").should("eq", "RN0025");
        });
      });
    });
  });

  // =====================================================================
  // RF0034 — Cálculo do frete
  // =====================================================================
  describe("RF0034 — Cálculo do frete", () => {

    it("RF0034 — deve calcular o frete com base nos itens selecionados e no endereço de entrega", () => {
      criarCliente().then((cliente) => {
        criarJogo({ preco: 50, pesoKg: 0.5 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          cy.get("[data-cy=criterio-frete]").should("be.visible").and("contain.text", "R$ 5,00 por kg");

          // endereço cadastrado em SP: R$ 10,00 + 1 kg × R$ 5,00
          cy.get("[data-cy=checkout-frete]").should("have.text", "R$ 15,00");
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 115,00");

          // novo endereço na BA: R$ 25,00 + 1 kg × R$ 5,00
          cy.get("[data-cy=checkout-endereco]").select("novo");
          cy.get("[data-cy=novo-end-estado]").type("BA");
          cy.get("[data-cy=checkout-frete]").should("have.text", "R$ 30,00");
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 130,00");
        });
      });
    });

    it("RF0034 — o frete acompanha a quantidade de itens do carrinho", () => {
      criarCliente().then((cliente) => {
        criarJogo({ preco: 50, pesoKg: 0.5 }).then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 4);
          abrirCheckout();
          // 2 kg para SP: R$ 10,00 + 2 × R$ 5,00
          cy.get("[data-cy=checkout-frete]").should("have.text", "R$ 20,00");
          cy.get("[data-cy=checkout-total]").should("have.text", "R$ 220,00");
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 4 — Pagamento com mais de um cartão (RN0034)
  // =====================================================================
  describe("Roteiro 4 — RN0034 — Mais de um cartão de crédito", () => {

    it("RN0034 — deve pagar com mais de um cartão de crédito, com no mínimo R$ 10,00 em cada", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        criarCartao(cliente.id, CARTAO_MASTER);
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();

          cy.get("[data-cy=pagto-cartao-check]").eq(0).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(0).clear().type("100,00");
          cy.get("[data-cy=pagto-cartao-check]").eq(1).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(1).clear().type("15,00");
          finalizarCompra().then((codigo) => {
            abrirGestaoVendas();
            linhaPedido(codigo).find(".col-pagamento")
              .should("contain.text", "VISA 1111: R$ 100,00")
              .and("contain.text", "MASTERCARD 4444: R$ 15,00");
            linhaPedido(codigo).find("[data-cy=status-pedido]").should("have.text", "EM PROCESSAMENTO");
          });
        });
      });
    });

    it("RN0034 — não deve aceitar um cartão com valor abaixo de R$ 10,00 quando não há cupons", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id, CARTAO_VISA);
        criarCartao(cliente.id, CARTAO_MASTER);
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();

          cy.get("[data-cy=pagto-cartao-check]").eq(0).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(0).clear().type("110,00");
          cy.get("[data-cy=pagto-cartao-check]").eq(1).check();
          cy.get("[data-cy=pagto-cartao-valor]").eq(1).clear().type("5,00");
          cy.get("[data-cy=btn-finalizar-compra]").click();

          cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RN0034");
          cy.get("[data-cy=checkout]").should("be.visible");
        });
      });
    });

    it("RF0036 — não deve finalizar quando a soma dos cartões difere do valor a pagar", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();

          pagarComPrimeiroCartaoCadastrado();
          cy.get("[data-cy=pagto-cartao-valor]").first().clear().type("50,00");
          cy.get("[data-cy=btn-finalizar-compra]").click();

          cy.get("[data-cy=mensagem]").should("contain.text", "RF0036").and("contain.text", "soma dos valores");
        });
      });
    });
  });

  // =====================================================================
  // Roteiro 5 — Cartão de crédito e cupons (RF0037, RN0033, RN0035)
  // =====================================================================
  describe("Roteiro 5 — RF0037 / RN0033 / RN0035 — Cartão de crédito e cupons", () => {

    it("RF0037 — deve pagar com cartão de crédito e cupom promocional", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();

          aplicarCupomPorCodigo("PROMO10");
          cy.contains("[data-cy=linha-cupom]", "PROMO10").should("contain.text", "promocional");
          cy.get("[data-cy=checkout-valor-cupons]").should("have.text", "R$ 10,00");
          cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 105,00");
          pagarComPrimeiroCartaoCadastrado();
          cy.get("[data-cy=pagto-cartao-valor]").first().should("have.value", "105,00");

          finalizarCompra().then((codigo) => {
            abrirGestaoVendas();
            linhaPedido(codigo).find(".col-pagamento")
              .should("contain.text", "Cupom PROMO10: R$ 10,00")
              .and("contain.text", "VISA 1111: R$ 105,00");
          });
        });
      });
    });

    it("RF0037 / RN0035 — deve combinar cupons de troca e promocional com cartão, aceitando menos de R$ 10,00 no cartão", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarCupomTroca(cliente.id, 100).then((cupomTroca) => {
          criarJogo().then((jogo) => {
            abrirLoja(cliente.id);
            adicionarAoCarrinho(jogo.titulo, 2);
            abrirCheckout();

            usarCupomDeTroca(cupomTroca.codigo);
            aplicarCupomPorCodigo("PROMO10");
            cy.contains("[data-cy=linha-cupom]", "PROMO10").should("exist");
            // o valor máximo dos cupons é usado primeiro: R$ 115,00 − R$ 110,00
            cy.get("[data-cy=checkout-valor-cupons]").should("have.text", "R$ 110,00");
            cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 5,00");
            pagarComPrimeiroCartaoCadastrado();
            cy.get("[data-cy=pagto-cartao-valor]").first().should("have.value", "5,00");

            finalizarCompra().then((codigo) => {
              abrirGestaoVendas();
              linhaPedido(codigo).find(".col-pagamento")
                .should("contain.text", `Cupom ${cupomTroca.codigo}: R$ 100,00`)
                .and("contain.text", "Cupom PROMO10: R$ 10,00")
                .and("contain.text", "VISA 1111: R$ 5,00");
            });
          });
        });
      });
    });

    it("RN0033 — não deve permitir mais de um cupom promocional na mesma compra", () => {
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
  // Roteiro 6 — Cupons acima do valor da compra (RN0036)
  // =====================================================================
  describe("Roteiro 6 — RN0036 — Cupom de troca com a diferença", () => {

    it("RN0036 — deve emitir um cupom de troca com a diferença quando os cupons superam o valor da compra", () => {
      criarCliente().then((cliente) => {
        criarCupomTroca(cliente.id, 70).then((cupomA) => {
          criarCupomTroca(cliente.id, 60).then((cupomB) => {
            criarJogo().then((jogo) => {
              abrirLoja(cliente.id);
              adicionarAoCarrinho(jogo.titulo, 2);
              abrirCheckout();

              usarCupomDeTroca(cupomA.codigo);
              usarCupomDeTroca(cupomB.codigo);
              cy.get("[data-cy=checkout-total]").should("have.text", "R$ 115,00");
              cy.get("[data-cy=checkout-restante]").should("have.text", "R$ 0,00");

              finalizarCompra().then((codigo) => {
                // R$ 130,00 em cupons − R$ 115,00 da compra = R$ 15,00 de troco
                cy.get("[data-cy=mensagem]").should("contain.text", "Cupom de troca gerado com a diferença")
                  .and("contain.text", "R$ 15,00");
                cy.contains("[data-cy=linha-minha-compra]", codigo)
                  .find("[data-cy=cupom-troco-gerado]").should("contain.text", "TROCA-").and("contain.text", "R$ 15,00");

                // o cupom emitido fica disponível para o cliente na próxima compra
                criarJogo().then((outroJogo) => {
                  abrirLoja(cliente.id);
                  adicionarAoCarrinho(outroJogo.titulo, 1);
                  abrirCheckout();
                  cy.get("[data-cy=cupons-troca-disponiveis]").should("contain.text", "R$ 15,00");
                  cy.get("[data-cy=cupons-troca-disponiveis]").should("not.contain.text", cupomA.codigo);
                });
              });
            });
          });
        });
      });
    });

    it("RN0036 — não deve permitir o uso de cupons desnecessários", () => {
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
  // Roteiro 7 — Pedido registrado EM PROCESSAMENTO (RF0038)
  // =====================================================================
  describe("Roteiro 7 — RF0038 — Pedido finalizado EM PROCESSAMENTO", () => {

    it("RF0038 — o pedido finalizado é registrado com status EM PROCESSAMENTO e o carrinho é esvaziado", () => {
      criarCliente().then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          comprarViaInterface(cliente.id, jogo.titulo, 2).then((codigo) => {
            cy.get("[data-cy=mensagem]").should("contain.text", "Status: EM PROCESSAMENTO");
            cy.get("[data-cy=linha-carrinho]").should("not.exist");
            cy.get("[data-cy=btn-comprar]").should("be.disabled");
            cy.contains("[data-cy=linha-minha-compra]", codigo).should("contain.text", "EM PROCESSAMENTO");

            abrirGestaoVendas();
            cy.get("[data-cy=filtro-status-pedido]").select("EM_PROCESSAMENTO");
            cy.get("[data-cy=btn-filtrar-pedidos]").click();
            linhaPedido(codigo).find("[data-cy=status-pedido]").should("have.text", "EM PROCESSAMENTO");
            linhaPedido(codigo).should("contain.text", "R$ 115,00");
          });
        });
      });
    });
  });

  // =====================================================================
  // RNF0011 / RNF0012 — Requisitos não funcionais gerais
  // =====================================================================
  describe("RNF0011 / RNF0012 — Tempo de resposta e log de transação", () => {

    it("RNF0011 — as consultas usadas na criação do pedido respondem em no máximo 1 segundo", () => {
      const nome = `Comprador Desempenho ${unico()}`;
      criarCliente(nome).then((cliente) => {
        criarCartao(cliente.id);
        criarJogo().then((jogo) => {
          cy.request("POST", `${API}/clientes/${cliente.id}/carrinho/itens`, { jogoId: jogo.id, quantidade: 2 });

          const consultas = [
            ["GET", `${API}/jogos?clienteId=${cliente.id}`],
            ["GET", `${API}/clientes/${cliente.id}/carrinho`],
            ["GET", `${API}/clientes/${cliente.id}/enderecos`],
            ["GET", `${API}/clientes/${cliente.id}/cartoes`],
            ["GET", `${API}/clientes/${cliente.id}/cupons`],
            ["POST", `${API}/clientes/${cliente.id}/carrinho/frete`, { estado: "SP" }],
            ["GET", `${API}/clientes?nome=${encodeURIComponent(nome)}`],
            ["GET", `${API}/clientes/${cliente.id}/pedidos`],
          ];
          consultas.forEach(([method, url, body]) => {
            cy.request({ method, url, body }).then((resposta) => {
              expect(resposta.status).to.eq(200);
              expect(resposta.duration, `${method} ${url}`).to.be.lessThan(1000);
            });
          });
        });
      });
    });

    it("RNF0012 — as operações de escrita da compra são registradas no log de auditoria", () => {
      criarCliente().then((cliente) => {
        criarJogo().then((jogo) => {
          abrirLoja(cliente.id);
          adicionarAoCarrinho(jogo.titulo, 2);
          abrirCheckout();
          preencherNovoEndereco({ apelido: `Endereco Log ${cliente.id}`, estado: "SP" });
          cy.get("[data-cy=novo-end-salvar]").check();
          preencherNovoCartao(CARTAO_MASTER);
          cy.get("[data-cy=pagto-novo-salvar]").check();

          finalizarCompra().then((codigo) => {
            cy.get("[data-cy=nav-auditoria]").click();
            cy.contains("[data-cy=linha-auditoria]", `Adicionado ao carrinho: ${jogo.titulo}`).should("contain.text", "INSERT");
            cy.contains("[data-cy=linha-auditoria]", `apelido=Endereco Log ${cliente.id}`).should("contain.text", "INSERT");
            cy.contains("[data-cy=linha-auditoria]", `Compra ${codigo} finalizada`)
              .should("contain.text", "INSERT")
              .and("contain.text", `cliente-${cliente.id}`);
          });
        });
      });
    });
  });
});
