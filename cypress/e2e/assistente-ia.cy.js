// Testes funcionais automatizados (Cypress) do chatbot de recomendação com IA generativa (RNF0044).
// Critérios da atividade: chatbot do perfil cliente; IA escolhida pelo grupo (Qwen2.5 local no Ollama);
// ajuste fino com dados de vendas e feedback; feedback por avaliação útil / não útil no chat;
// preferências declaradas pelo cliente no perfil.
//
// Os testes que dependem da IA de verdade (conversa real) rodam só quando o Ollama está rodando com
// o modelo base (GET /api/assistente/status → configurada = true); sem ele, são pulados. Os demais
// cobrem a massa de dados, as preferências, o registro do feedback, o dataset do ajuste fino e o
// acesso por perfil. Nenhum teste inicia um ajuste fino de verdade (ele leva cerca de 8 horas).
// A aplicação deve estar rodando em http://localhost:8080 (ver README).

const { API, criarCliente } = require("../support/vendas-helpers");

const CLIENTE = { "X-Perfil": "CLIENTE" };
const ADMIN = { "X-Perfil": "ADMINISTRADOR" };
// os 14 gêneros do catálogo da loja (outras suítes criam categorias de teste com um jogo só)
const GENEROS_DA_LOJA = ["Ação", "Aventura", "Corrida", "Esporte", "Estratégia", "Festa", "Luta", "Plataforma", "Puzzle",
  "RPG", "Sandbox", "Simulação", "Terror", "Tiro"];
const ANA = "ana.souza@nexus.com.br";
const BRUNO = "bruno.lima@nexus.com.br";

function clientePorEmail(email) {
  return cy.request(`${API}/clientes?email=${encodeURIComponent(email)}`).its("body.0");
}

function preferenciasDe(clienteId) {
  return cy.request({ url: `${API}/clientes/${clienteId}/preferencias`, headers: CLIENTE }).its("body");
}

function avaliacoesDe(clienteId) {
  return cy.request({ url: `${API}/clientes/${clienteId}/assistente/avaliacoes`, headers: CLIENTE }).its("body");
}

function avaliarViaApi(mensagemId, avaliacao) {
  return cy.request({ method: "PUT", url: `${API}/assistente/mensagens/${mensagemId}/avaliacao`, headers: CLIENTE, body: { avaliacao } });
}

function dataset() {
  return cy.request({ url: `${API}/ia/dataset`, headers: ADMIN }).its("body");
}

/** Linhas do JSONL do ajuste fino ({"messages": [system, user, assistant...]}): { sistema, conversa: [{ papel, texto }] }. */
function linhasDoDataset() {
  return cy.request({ url: `${API}/ia/dataset/arquivo`, headers: ADMIN }).its("body").then((texto) =>
    String(texto).trim().split("\n").map((linha) => {
      const mensagens = JSON.parse(linha).messages;
      return {
        sistema: mensagens[0].content,
        conversa: mensagens.slice(1).map((m) => ({ papel: m.role, texto: m.content })),
      };
    }));
}

/** Jogos recomendados numa resposta-modelo: linhas "- **Título** (Plataforma, Gênero) — R$ ...". */
function recomendadosNa(texto) {
  return [...texto.matchAll(/- \*\*(.+?)\*\* \((.+?), (.+?)\) —/g)].map((m) => ({ titulo: m[1], plataforma: m[2], genero: m[3] }));
}

function abrirAssistenteComo(email) {
  cy.visit("/index.html");
  cy.get("[data-cy=perfil-usuario]").select("CLIENTE");
  cy.get("[data-cy=nav-assistente]").should("be.visible").click();
  cy.get("[data-cy=assist-cliente] option").should("have.length.greaterThan", 1);
  clientePorEmail(email).then((cliente) => {
    cy.get("[data-cy=assist-cliente]").select(String(cliente.id));
    cy.get("[data-cy=assist-conteudo]").should("be.visible");
    cy.get("[data-cy=pref-genero]").should("have.length.greaterThan", 0);
    cy.wrap(cliente).as("cliente");
  });
}

function iaConfigurada() {
  return cy.request({ url: `${API}/assistente/status`, headers: CLIENTE }).its("body.configurada");
}

describe("Assistente de recomendação com IA — RNF0044", () => {

  // =====================================================================
  // Massa de dados: clientes com históricos distintos, vendas e feedback
  // =====================================================================
  describe("Massa de dados", () => {

    it("RNF0044 — há clientes com históricos e preferências distintos, e feedback suficiente para o ajuste fino", () => {
      clientePorEmail(ANA).then((ana) => clientePorEmail(BRUNO).then((bruno) => {
        preferenciasDe(ana.id).then((p) => {
          expect(p.generos).to.have.members(["RPG", "Aventura"]);
          expect(p.plataformas).to.include("PC");
        });
        preferenciasDe(bruno.id).then((p) => expect(p.generos).to.have.members(["Esporte", "Corrida"]));

        avaliacoesDe(ana.id).then((lista) => {
          expect(lista.filter((a) => a.avaliacao === "UTIL").length, "úteis da Ana").to.be.at.least(3);
          expect(lista.filter((a) => a.avaliacao === "NAO_UTIL").length, "não úteis da Ana").to.be.at.least(1);
        });
      }));

      dataset().then((d) => {
        expect(d.clientes, "clientes com compras").to.be.at.least(2);
        expect(d.exemplosVendas, "exemplos das vendas").to.be.greaterThan(20);
        expect(d.exemplosFeedback, "exemplos do feedback útil").to.be.at.least(12);
        expect(d.feedbackDescartado, "feedback não útil fora do treino").to.be.at.least(6);
        expect(d.total).to.eq(d.exemplosVendas + d.exemplosFeedback);
      });
    });
  });

  // =====================================================================
  // Roteiros 2 e 3 — personalização nos dados de treino
  // =====================================================================
  describe("Roteiros 2 e 3 — Recomendação personalizada no ajuste fino", () => {

    it("RNF0044 — os exemplos de treino recomendam jogos do gosto de cada cliente, diferentes para Ana e Bruno", () => {
      linhasDoDataset().then((linhas) => {
        const recomendacoesDe = (nome) => linhas
          .filter((l) => l.sistema.includes(`Nome: ${nome}`) && l.conversa[0].texto === "Me recomenda um jogo?")
          .flatMap((l) => recomendadosNa(l.conversa[1].texto));

        const daAna = recomendacoesDe("Ana Souza");
        const doBruno = recomendacoesDe("Bruno Lima");
        expect(daAna, "recomendações da Ana").to.not.be.empty;
        expect(doBruno, "recomendações do Bruno").to.not.be.empty;

        // a primeira sugestão é de um gênero preferido de cada um
        expect(["RPG", "Aventura"]).to.include(daAna[0].genero);
        expect(["Esporte", "Corrida"]).to.include(doBruno[0].genero);
        // e nenhum jogo já comprado é recomendado
        ["Elden Ring", "Cyberpunk 2077", "Hogwarts Legacy"].forEach((t) => expect(daAna.map((r) => r.titulo)).to.not.include(t));
        ["EA Sports FC 25", "Forza Horizon 5", "NBA 2K25"].forEach((t) => expect(doBruno.map((r) => r.titulo)).to.not.include(t));
        // as listas são diferentes
        expect(daAna.map((r) => r.titulo).join()).to.not.eq(doBruno.map((r) => r.titulo).join());

        // o perfil vai na instrução de sistema, igual ao chat
        const linhaAna = linhas.find((l) => l.sistema.includes("Nome: Ana Souza"));
        expect(linhaAna.sistema).to.contain("Compras: ").and.contain("Elden Ring").and.match(/Preferências declaradas: gêneros (RPG, Aventura|Aventura, RPG)/);
        expect(linhaAna.sistema).to.contain("SUGESTÕES PARA ESTE CLIENTE");
        expect(linhaAna.sistema).to.contain("CATÁLOGO");
      });
    });
  });

  // =====================================================================
  // Critério e — preferências declaradas no perfil
  // =====================================================================
  describe("Critério e — Preferências do cliente", () => {

    it("RNF0044 — o cliente marca gêneros, plataformas e orçamento no perfil, e as preferências ficam salvas", () => {
      criarCliente().then((novo) => {
        cy.visit("/index.html");
        cy.get("[data-cy=nav-assistente]").click();
        cy.get("[data-cy=assist-cliente] option").should("have.length.greaterThan", 1);
        cy.get("[data-cy=assist-cliente]").select(String(novo.id));
        cy.get("[data-cy=pref-genero]").should("not.be.checked");

        cy.get('[data-cy=pref-genero][value="Corrida"]').check();
        cy.get('[data-cy=pref-genero][value="Plataforma"]').check();
        cy.get('[data-cy=pref-plataforma][value="Nintendo Switch"]').check();
        cy.get("[data-cy=pref-orcamento]").type("200");
        cy.get("[data-cy=pref-observacoes]").type("Jogo com a família");
        cy.get("[data-cy=btn-salvar-preferencias]").click();
        cy.get("[data-cy=mensagem]").should("have.class", "sucesso").and("contain.text", "Preferências salvas");

        preferenciasDe(novo.id).then((p) => {
          expect(p.generos).to.have.members(["Corrida", "Plataforma"]);
          expect(p.plataformas).to.deep.eq(["Nintendo Switch"]);
          expect(p.orcamentoMaximo).to.eq(200);
          expect(p.observacoes).to.eq("Jogo com a família");
        });

        // ao voltar ao cliente, a tela mostra o que foi salvo
        cy.reload();
        cy.get("[data-cy=nav-assistente]").click();
        cy.get("[data-cy=assist-cliente] option").should("have.length.greaterThan", 1);
        cy.get("[data-cy=assist-cliente]").select(String(novo.id));
        cy.get('[data-cy=pref-genero][value="Corrida"]').should("be.checked");
        cy.get('[data-cy=pref-plataforma][value="Nintendo Switch"]').should("be.checked");
        cy.get("[data-cy=pref-orcamento]").should("have.value", "200");

        // orçamento inválido é recusado
        cy.get("[data-cy=pref-orcamento]").clear().type("0");
        cy.get("[data-cy=btn-salvar-preferencias]").click();
        cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "RNF0044");
      });
    });

    it("RNF0044 — o catálogo tem pelo menos 3 jogos de cada gênero em cada plataforma", () => {
      cy.request({ url: `${API}/assistente/opcoes`, headers: CLIENTE }).its("body").then(({ generos: todos, plataformas }) => {
          const generos = GENEROS_DA_LOJA;
          expect(todos).to.include.members(GENEROS_DA_LOJA);
        expect(plataformas).to.have.members(["Nintendo Switch", "PC", "PlayStation 5", "Xbox Series X|S"]);
        cy.request(`${API}/jogos`).its("body").then((jogos) => {
          const faltando = [];
          generos.forEach((genero) => plataformas.forEach((plataforma) => {
            const titulos = new Set(jogos.filter((j) => j.genero === genero && j.plataforma === plataforma).map((j) => j.titulo));
            if (titulos.size < 3) faltando.push(`${genero} / ${plataforma}: ${titulos.size}`);
          }));
          expect(faltando, "combinações gênero / plataforma com menos de 3 jogos").to.be.empty;
        });
      });
    });

    it("RNF0044 — para qualquer gênero e plataforma marcados no perfil, as 3 primeiras recomendações são daquele gênero e plataforma", () => {
      criarCliente().then((novo) => {
        cy.request({ url: `${API}/assistente/opcoes`, headers: CLIENTE }).its("body").then(({ generos: todos, plataformas }) => {
          const generos = GENEROS_DA_LOJA;
          expect(todos).to.include.members(GENEROS_DA_LOJA);
          const erradas = [];
          generos.forEach((genero) => plataformas.forEach((plataforma) => {
            cy.request({ method: "PUT", url: `${API}/clientes/${novo.id}/preferencias`, headers: CLIENTE,
              body: { generos: [genero], plataformas: [plataforma], orcamentoMaximo: null, observacoes: null } });
            cy.request({ url: `${API}/clientes/${novo.id}/assistente/recomendacoes?quantidade=3`, headers: CLIENTE }).its("body")
              .then((recomendacoes) => {
                const certas = recomendacoes.filter((r) => r.genero === genero && r.plataforma === plataforma);
                if (recomendacoes.length < 3 || certas.length < 3) {
                  erradas.push(`${genero} / ${plataforma}: ${recomendacoes.map((r) => `${r.titulo} (${r.genero}, ${r.plataforma})`).join("; ")}`);
                }
              });
          }));
          cy.then(() => expect(erradas, "combinações sem 3 recomendações do gênero e da plataforma").to.be.empty);
        });
      });
    });

    it("RNF0044 — o orçamento e as plataformas do perfil são limites: nenhuma recomendação passa deles", () => {
      criarCliente().then((novo) => {
        cy.request({ method: "PUT", url: `${API}/clientes/${novo.id}/preferencias`, headers: CLIENTE,
          body: { generos: ["Corrida"], plataformas: ["Xbox Series X|S"], orcamentoMaximo: 100, observacoes: null } });
        cy.request({ url: `${API}/clientes/${novo.id}/assistente/recomendacoes?quantidade=10`, headers: CLIENTE }).its("body")
          .then((recomendacoes) => {
            expect(recomendacoes).to.not.be.empty;
            recomendacoes.forEach((r) => {
              expect(r.preco, `${r.titulo} cabe no orçamento`).to.be.at.most(100);
              expect(r.plataforma, `${r.titulo} é da plataforma do cliente`).to.eq("Xbox Series X|S");
            });
          });
      });
    });
  });

  // =====================================================================
  // Roteiro 7 — Avaliação útil / não útil e registro do feedback
  // =====================================================================
  describe("Roteiro 7 — Avaliação das respostas", () => {

    it("RNF0044 — avaliar uma resposta no chat registra o feedback e muda o dataset do próximo ajuste fino", () => {
      clientePorEmail(ANA).then((ana) => avaliacoesDe(ana.id).then((avaliacoes) => {
        const util = avaliacoes.find((a) => a.avaliacao === "UTIL");

        // a resposta do chat é simulada aqui (a IA real é testada no bloco final); a avaliação é real
        cy.intercept("POST", "**/assistente/mensagens", {
          statusCode: 200,
          body: { ...util, avaliacao: null, avaliacaoDescricao: null, dataAvaliacao: null },
        }).as("conversa");

        dataset().then((antes) => {
          abrirAssistenteComo(ANA);
          cy.get("[data-cy=assist-boas-vindas]").should("contain.text", "Ana");
          cy.get("[data-cy=assist-entrada]").type(util.pergunta);
          cy.get("[data-cy=btn-enviar-assistente]").click();
          cy.wait("@conversa");
          cy.get("[data-cy=msg-cliente]").last().should("contain.text", util.pergunta);
          cy.get("[data-cy=msg-assistente]").last().as("resposta");
          cy.get("@resposta").find("[data-cy=avaliacao-registrada]").should("not.be.visible");

          cy.get("@resposta").find("[data-cy=btn-nao-util]").click();
          cy.get("@resposta").find("[data-cy=avaliacao-registrada]").should("be.visible").and("contain.text", "Não útil");
          cy.get(`[data-cy=item-avaliacao][data-id="${util.id}"]`).should("have.attr", "data-avaliacao", "NAO_UTIL");

          // registrado na base e visível para o administrador
          cy.request({ url: `${API}/ia/feedbacks`, headers: ADMIN }).its("body").then((feedbacks) => {
            expect(feedbacks.find((f) => f.id === util.id).avaliacao).to.eq("NAO_UTIL");
          });
          // a resposta sai dos exemplos do ajuste fino
          dataset().then((depois) => {
            expect(depois.exemplosFeedback).to.eq(antes.exemplosFeedback - 1);
            expect(depois.feedbackDescartado).to.eq(antes.feedbackDescartado + 1);
          });

          // marcar como útil de novo volta a resposta para o dataset
          cy.get("@resposta").find("[data-cy=btn-util]").click();
          cy.get("@resposta").find("[data-cy=avaliacao-registrada]").should("contain.text", "Útil");
          dataset().its("exemplosFeedback").should("eq", antes.exemplosFeedback);
        });
      }));
    });

    it("RNF0044 — a avaliação aceita só útil ou não útil", () => {
      clientePorEmail(ANA).then((ana) => avaliacoesDe(ana.id).then((avaliacoes) => {
        cy.request({ method: "PUT", url: `${API}/assistente/mensagens/${avaliacoes[0].id}/avaliacao`, headers: CLIENTE,
          body: {}, failOnStatusCode: false }).then((r) => {
          expect(r.status).to.eq(422);
          expect(r.body.codigoRegra).to.eq("RNF0044");
        });
        cy.request({ method: "PUT", url: `${API}/assistente/mensagens/999999999/avaliacao`, headers: CLIENTE,
          body: { avaliacao: "UTIL" }, failOnStatusCode: false }).its("status").should("eq", 404);
      }));
    });
  });

  // =====================================================================
  // Roteiro 8 — Treinamento: dados, procedimento e incorporação de dados novos
  // =====================================================================
  describe("Roteiro 8 — Ajuste fino", () => {

    it("RNF0044 — o administrador vê o procedimento, o dataset, o histórico e o feedback registrado", () => {
      cy.visit("/index.html");
      cy.get("[data-cy=perfil-usuario]").select("ADMINISTRADOR");
      cy.get("[data-cy=nav-ia]").should("be.visible").click();
      cy.get("[data-cy=ia-conteudo]").should("be.visible");
      cy.get("[data-cy=ia-provedor]").should("contain.text", "Ollama");
      cy.get("[data-cy=ia-procedimento] li").should("have.length", 4);
      cy.get("[data-cy=ia-exemplos-vendas]").invoke("text").then(Number).should("be.greaterThan", 20);
      cy.get("[data-cy=ia-exemplos-feedback]").invoke("text").then(Number).should("be.greaterThan", 0);
      cy.get("[data-cy=ia-feedback-descartado]").invoke("text").then(Number).should("be.greaterThan", 0);
      cy.get("[data-cy=tabela-feedbacks] [data-cy=linha-feedback]").should("have.length.greaterThan", 10);
      cy.get('[data-cy=linha-feedback][data-avaliacao="NAO_UTIL"]').should("exist");
      cy.get("[data-cy=ia-amostra-dataset]").should("contain.text", "\"role\": \"system\"").and("contain.text", "\"role\": \"assistant\"");
      cy.get("[data-cy=ia-novos-dados]").should("contain.text", "treino automático");
    });

    it("RNF0044 — sem a IA configurada, o ajuste fino é recusado com a orientação de configuração", function () {
      iaConfigurada().then((configurada) => {
        if (configurada) this.skip(); // com a IA pronta, o pedido iniciaria um treino de verdade (cerca de uma hora)
        cy.request({ method: "POST", url: `${API}/ia/treinamentos`, headers: ADMIN, failOnStatusCode: false }).then((r) => {
          expect(r.status).to.eq(503);
          expect(r.body.codigoRegra).to.eq("IA_INDISPONIVEL");
          expect(r.body.mensagem).to.contain("Ollama");
        });
      });
    });
  });

  // =====================================================================
  // Critério a — perfil cliente usa o chat; administrador treina
  // =====================================================================
  describe("Critério a — Acesso por perfil", () => {

    it("RNF0044 — o chat é do perfil cliente e o treinamento, do administrador", () => {
      clientePorEmail(ANA).then((ana) => {
        cy.request({ method: "POST", url: `${API}/clientes/${ana.id}/assistente/mensagens`, headers: ADMIN,
          body: { mensagem: "Oi" }, failOnStatusCode: false }).then((r) => {
          expect(r.status).to.eq(403);
          expect(r.body.codigoRegra).to.eq("ACESSO_RESTRITO");
        });
        cy.request({ url: `${API}/ia/status`, headers: CLIENTE, failOnStatusCode: false }).its("status").should("eq", 403);
        cy.request({ url: `${API}/ia/dataset`, failOnStatusCode: false }).its("status").should("eq", 403);
      });

      cy.visit("/index.html");
      cy.get("[data-cy=nav-assistente]").should("be.visible");
      cy.get("[data-cy=nav-ia]").should("not.be.visible");
      cy.get("[data-cy=perfil-usuario]").select("ADMINISTRADOR");
      cy.get("[data-cy=nav-assistente]").should("not.be.visible");
      cy.get("[data-cy=nav-ia]").should("be.visible");
    });
  });

  // =====================================================================
  // Roteiros 1 a 6 com a IA de verdade (só com o Ollama rodando)
  // =====================================================================
  describe("Roteiros 1 a 6 — Conversa com a IA generativa", () => {

    function perguntar(texto) {
      cy.intercept("POST", "**/assistente/mensagens").as("resposta");
      cy.get("[data-cy=assist-entrada]").clear().type(texto);
      cy.get("[data-cy=btn-enviar-assistente]").click();
      cy.wait("@resposta", { timeout: 90000 }).its("response.statusCode").should("eq", 200);
      return cy.get("[data-cy=msg-assistente]").last().find("[data-cy=msg-assistente-texto]").invoke("text");
    }

    function titulosDoCatalogo(generos) {
      return cy.request(`${API}/jogos/estoque`).its("body")
        .then((jogos) => jogos.filter((j) => generos.includes(j.genero)).map((j) => j.titulo));
    }

    it("RNF0044 — recomendações coerentes e diferentes para Ana (RPG/aventura) e Bruno (esporte/corrida)", function () {
      iaConfigurada().then((configurada) => {
        if (!configurada) this.skip();
        titulosDoCatalogo(["RPG", "Aventura"]).then((rpg) => titulosDoCatalogo(["Esporte", "Corrida"]).then((esporte) => {
          abrirAssistenteComo(ANA);
          cy.get("[data-cy=assist-modelo]").should("not.contain.text", "—");
          perguntar("Me recomenda um jogo?").then((resposta) => {
            expect(resposta.length).to.be.greaterThan(20);
            expect(rpg.some((t) => resposta.includes(t)), "cita um RPG/aventura do catálogo").to.eq(true);
          });

          abrirAssistenteComo(BRUNO);
          perguntar("Me recomenda um jogo?").then((resposta) => {
            expect(esporte.some((t) => resposta.includes(t)), "cita um jogo de esporte/corrida do catálogo").to.eq(true);
          });
        }));
      });
    });

    it("RNF0044 — ajuda na busca, responde dúvidas, sugere conteúdo e registra a avaliação da resposta real", function () {
      iaConfigurada().then((configurada) => {
        if (!configurada) this.skip();
        abrirAssistenteComo(BRUNO);
        cy.request(`${API}/jogos`).its("body").then((jogos) => {
          const corrida = jogos.filter((j) => j.genero === "Corrida").map((j) => j.titulo);
          perguntar("Quais jogos de corrida vocês têm?").then((resposta) => {
            expect(corrida.some((t) => resposta.includes(t)), "cita um jogo de corrida do catálogo").to.eq(true);
          });
        });
        perguntar("O Gran Turismo 7 é bom para quem está começando?").then((resposta) => expect(resposta.length).to.be.greaterThan(20));
        perguntar("Que outros conteúdos você me sugere para aproveitar melhor os jogos de corrida?")
          .then((resposta) => expect(resposta.length).to.be.greaterThan(20));

        cy.get("[data-cy=msg-assistente]").last().find("[data-cy=btn-util]").click();
        cy.get("[data-cy=msg-assistente]").last().find("[data-cy=avaliacao-registrada]").should("contain.text", "Útil");
        cy.get("[data-cy=msg-assistente]").last().invoke("attr", "data-id").then((id) => {
          cy.get(`[data-cy=item-avaliacao][data-id="${id}"]`).should("have.attr", "data-avaliacao", "UTIL");
        });
      });
    });
  });
});
