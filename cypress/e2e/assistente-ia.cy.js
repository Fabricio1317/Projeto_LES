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
// preferências da massa de dados (CargaAssistente): restauradas antes dos testes, porque um teste manual na tela pode
// ter mudado as da Ana ou as do Bruno e os testes abaixo contam com o perfil original deles
const PREFERENCIAS_DA_MASSA = {
  [ANA]: { generos: ["RPG", "Aventura"], plataformas: ["PC", "PlayStation 5"], orcamentoMaximo: 250,
    observacoes: "Gosto de histórias longas e mundo aberto." },
  [BRUNO]: { generos: ["Esporte", "Corrida"], plataformas: ["PlayStation 5", "Xbox Series X|S"], orcamentoMaximo: null,
    observacoes: "Jogo online com os amigos." },
};
const PLATAFORMAS = ["Nintendo Switch", "PC", "PlayStation 5", "Xbox Series X|S"];

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

function salvarPreferencias(clienteId, preferencias) {
  return cy.request({ method: "PUT", url: `${API}/clientes/${clienteId}/preferencias`, headers: CLIENTE, body: preferencias });
}

describe("Assistente de recomendação com IA — RNF0044", () => {

  before(() => {
    Object.entries(PREFERENCIAS_DA_MASSA).forEach(([email, preferencias]) =>
      clientePorEmail(email).then((cliente) => salvarPreferencias(cliente.id, preferencias)));
  });

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

    it("RNF0044 — o catálogo tem pelo menos 3 jogos de até R$ 100 de cada gênero em cada plataforma", () => {
      cy.request(`${API}/jogos`).its("body").then((jogos) => {
        const faltando = [];
        GENEROS_DA_LOJA.forEach((genero) => PLATAFORMAS.forEach((plataforma) => {
          const baratos = new Set(jogos.filter((j) => j.genero === genero && j.plataforma === plataforma && j.preco <= 100)
            .map((j) => j.titulo));
          if (baratos.size < 3) faltando.push(`${genero} / ${plataforma}: ${baratos.size}`);
        }));
        expect(faltando, "combinações gênero / plataforma com menos de 3 jogos até R$ 100").to.be.empty;
      });
    });

    it("RNF0044 — o mesmo jogo tem o preço de cada plataforma: PS5 e Xbox iguais, PC e Switch mais baratos", () => {
      cy.request(`${API}/jogos`).its("body").then((jogos) => {
        const precoDe = (titulo, plataforma) => jogos.find((j) => j.titulo === titulo && j.plataforma === plataforma).preco;
        // Hogwarts Legacy saiu primeiro no PS5, por R$ 199,90
        expect(precoDe("Hogwarts Legacy", "PlayStation 5")).to.eq(199.9);
        expect(precoDe("Hogwarts Legacy", "Xbox Series X|S")).to.eq(199.9);
        expect(precoDe("Hogwarts Legacy", "PC")).to.eq(159.9);
        expect(precoDe("Hogwarts Legacy", "Nintendo Switch")).to.eq(179.9);
        // The Witcher 3 saiu primeiro no PC, por R$ 149,90: nos consoles custa mais
        expect(precoDe("The Witcher 3: Wild Hunt Complete Edition", "PC")).to.eq(149.9);
        expect(precoDe("The Witcher 3: Wild Hunt Complete Edition", "PlayStation 5")).to.eq(184.9);
        // a maioria dos jogos vendidos em várias plataformas tem preços diferentes entre elas
        const precosPorTitulo = {};
        jogos.filter((j) => GENEROS_DA_LOJA.includes(j.genero))
          .forEach((j) => (precosPorTitulo[j.titulo] = (precosPorTitulo[j.titulo] || new Set()).add(j.preco)));
        const variados = Object.values(precosPorTitulo).filter((precos) => precos.size > 1).length;
        expect(variados, "jogos com preço diferente entre plataformas").to.be.greaterThan(50);
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

    it("RNF0044 — com vários gêneros marcados, as recomendações trazem um jogo de cada um", () => {
      const generos = ["Ação", "Puzzle", "Terror", "Corrida"];
      criarCliente().then((novo) => {
        salvarPreferencias(novo.id, { generos, plataformas: ["PlayStation 5"], orcamentoMaximo: null, observacoes: null });
        // pedindo só 3, vêm pelo menos 4: um de cada gênero marcado
        cy.request({ url: `${API}/clientes/${novo.id}/assistente/recomendacoes?quantidade=3`, headers: CLIENTE }).its("body")
          .then((recomendacoes) => {
            generos.forEach((g) => expect(recomendacoes.map((r) => r.genero), `um jogo de ${g}`).to.include(g));
            recomendacoes.forEach((r) => expect(r.plataforma, `${r.titulo} é da plataforma do cliente`).to.eq("PlayStation 5"));
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

    it("RNF0044 — sem a IA configurada, o ajuste fino é recusado com a orientação de configuração", () => {
      const motivo = "o Ollama não está rodando em http://localhost:11434 (abra o Ollama e baixe o modelo: ollama pull qwen2.5:1.5b)";

      // com o Ollama desligado, a API recusa de verdade; ligado, o pedido iniciaria um treino real (cerca de 8 horas),
      // então essa parte só roda sem a IA
      iaConfigurada().then((configurada) => {
        if (configurada) return;
        cy.request({ method: "POST", url: `${API}/ia/treinamentos`, headers: ADMIN, failOnStatusCode: false }).then((r) => {
          expect(r.status).to.eq(503);
          expect(r.body.codigoRegra).to.eq("IA_INDISPONIVEL");
          expect(r.body.mensagem).to.contain("Ollama");
        });
      });

      // na tela, a IA sem configuração é simulada (nenhum treino é iniciado): o aviso explica o que fazer, o botão
      // fica desativado e, se o pedido chegar mesmo assim, a recusa do servidor aparece para o administrador
      cy.request({ url: `${API}/ia/status`, headers: ADMIN }).its("body").then((status) => {
        cy.intercept("GET", "**/api/ia/status", { statusCode: 200,
          body: { ...status, configurada: false, motivoNaoConfigurada: motivo, treinamentoEmAndamento: false } }).as("status");
        cy.intercept("POST", "**/api/ia/treinamentos", { statusCode: 503,
          body: { codigoRegra: "IA_INDISPONIVEL", mensagem: `A IA não está configurada: ${motivo}` } }).as("treinar");

        cy.visit("/index.html");
        cy.get("[data-cy=perfil-usuario]").select("ADMINISTRADOR");
        cy.get("[data-cy=nav-ia]").click();
        cy.wait("@status");
        cy.get("[data-cy=ia-aviso]").should("be.visible").and("contain.text", "IA não configurada").and("contain.text", "Ollama");
        cy.get("[data-cy=btn-treinar]").should("be.disabled");

        cy.get("[data-cy=btn-treinar]").invoke("prop", "disabled", false).click();
        cy.wait("@treinar");
        cy.get("[data-cy=mensagem]").should("have.class", "erro").and("contain.text", "IA_INDISPONIVEL").and("contain.text", "Ollama");
        cy.get("[data-cy=btn-treinar]").should("be.disabled");
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

    /**
     * Pede uma recomendação numa conversa nova. O modelo local é pequeno e não determinístico: de vez em
     * quando devolve só uma pergunta ("Você gosta de corrida ou de esporte?") em vez de um jogo; nesse caso
     * abre outra conversa e pergunta de novo antes de o teste avaliar a resposta.
     */
    function recomendacaoPara(email, titulos, tentativas = 3) {
      abrirAssistenteComo(email);
      return perguntar("Me recomenda um jogo?").then((resposta) => {
        if (tentativas <= 1 || titulos.some((t) => resposta.includes(t))) return resposta;
        cy.log(`A IA não citou um jogo do catálogo; nova tentativa: "${resposta}"`);
        return recomendacaoPara(email, titulos, tentativas - 1);
      });
    }

    it("RNF0044 — recomendações coerentes e diferentes para Ana (RPG/aventura) e Bruno (esporte/corrida)", function () {
      iaConfigurada().then((configurada) => {
        if (!configurada) this.skip();
        titulosDoCatalogo(["RPG", "Aventura"]).then((rpg) => titulosDoCatalogo(["Esporte", "Corrida"]).then((esporte) => {
          recomendacaoPara(ANA, rpg).then((resposta) => {
            cy.get("[data-cy=assist-modelo]").should("not.contain.text", "—");
            expect(resposta.length).to.be.greaterThan(20);
            expect(rpg.some((t) => resposta.includes(t)), "cita um RPG/aventura do catálogo").to.eq(true);
          });

          recomendacaoPara(BRUNO, esporte).then((resposta) => {
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

  // =====================================================================
  // Conversa básica com a IA de verdade: o que o cliente responde, refina ou procura
  // (o backend confere cada resposta contra o catálogo e o perfil; só com o Ollama rodando)
  // =====================================================================
  describe("Conversa básica com a IA generativa", () => {

    const RECUSA = "só consigo ajudar com jogos";
    const PREFERIDA_ERRADA = /(PC|PlayStation 5|Xbox Series X\|S), a plataforma que você prefere/;

    /** Manda as mensagens numa mesma conversa, uma depois da outra, e devolve as respostas na ordem. */
    function conversar(clienteId, mensagens) {
      const respostas = [];
      let conversaId = null;
      mensagens.forEach((mensagem) => {
        cy.then(() => cy.request({ method: "POST", url: `${API}/clientes/${clienteId}/assistente/mensagens`, headers: CLIENTE,
          body: { conversaId, mensagem }, timeout: 120000 })).then((r) => {
          conversaId = r.body.conversaId;
          respostas.push(r.body.resposta);
        });
      });
      return cy.then(() => respostas);
    }

    /** Um cliente novo, sem compras, com as preferências informadas. */
    function clienteCom(preferencias) {
      return criarCliente().then((novo) => salvarPreferencias(novo.id, { orcamentoMaximo: null, observacoes: null, ...preferencias })
        .then(() => novo));
    }

    function catalogo() {
      return cy.request(`${API}/jogos`).its("body").then((jogos) => jogos.filter((j) => GENEROS_DA_LOJA.includes(j.genero)));
    }

    const citados = (resposta, jogos) => [...new Set(jogos.map((j) => j.titulo))].filter((t) => resposta.includes(t));

    beforeEach(function () {
      iaConfigurada().then((configurada) => {
        if (!configurada) this.skip();
      });
    });

    it("RNF0044 — responder \"sim\" a uma pergunta do assistente continua a conversa, e \"não, obrigado\" encerra", () => {
      catalogo().then((jogos) => clienteCom({ generos: ["Corrida"], plataformas: ["Xbox Series X|S"] }).then((cliente) => {
        conversar(cliente.id, ["Me recomenda um jogo?", "sim", "não, obrigado"]).then(([recomendacao, sim, nao]) => {
          expect(citados(recomendacao, jogos), "a recomendação cita um jogo do catálogo").to.not.be.empty;
          expect(sim, "o \"sim\" não é tratado como assunto fora de jogos").to.not.contain(RECUSA);
          expect(citados(sim, jogos), "o \"sim\" traz um jogo do catálogo").to.not.be.empty;
          expect(citados(nao, jogos), "o \"não\" não empurra outro jogo").to.be.empty;
        });
      }));
    });

    it("RNF0044 — depois de \"Quais jogos de corrida vocês têm?\", \"quero ver somente os de PC\" lista todos os de corrida do PC", () => {
      catalogo().then((jogos) => clienteCom({ generos: ["Corrida"], plataformas: ["Xbox Series X|S"] }).then((cliente) => {
        const corridaNoPc = [...new Set(jogos.filter((j) => j.genero === "Corrida" && j.plataforma === "PC").map((j) => j.titulo))];
        const corridaSemPc = [...new Set(jogos.filter((j) => j.genero === "Corrida").map((j) => j.titulo))]
          .filter((t) => !corridaNoPc.includes(t));
        conversar(cliente.id, ["Quais jogos de corrida vocês têm?", "quero ver somente os de Pc"]).then(([, soPc]) => {
          corridaNoPc.forEach((t) => expect(soPc, `${t} sai para PC`).to.contain(t));
          corridaSemPc.forEach((t) => expect(soPc, `${t} não sai para PC`).to.not.contain(t));
        });
      }));
    });

    it("RNF0044 — procurar um jogo pelo nome, mesmo escrito errado, traz o jogo pedido com o preço", () => {
      clienteCom({ generos: ["Aventura"], plataformas: ["Xbox Series X|S"] }).then((cliente) => {
        conversar(cliente.id, ["procura o minecraft pra mim por favor", "conta mais sobre the wicther 3"]).then(([minecraft, witcher]) => {
          expect(minecraft).to.contain("Minecraft").and.match(/R\$ \d/);
          expect(minecraft, "Sandbox não é gênero preferido do cliente").to.not.match(/Sandbox, um dos seus gêneros preferidos/);
          expect(minecraft).to.not.match(PREFERIDA_ERRADA);
          expect(witcher).to.contain("The Witcher 3").and.match(/R\$ \d/).and.not.contain(RECUSA);
        });
      });
    });

    const reais = (valor) => `R$ ${Number(valor).toFixed(2).replace(".", ",")}`;

    it("RNF0044 — responde perguntas sobre a loja com os dados do catálogo: mais barato, mais caro, por empresa e quantidade", () => {
      catalogo().then((jogos) => clienteCom({ generos: ["Corrida"], plataformas: ["Xbox Series X|S"] }).then((cliente) => {
        const corridaXbox = jogos.filter((j) => j.genero === "Corrida" && j.plataforma === "Xbox Series X|S" && j.estoque > 0);
        const menor = Math.min(...corridaXbox.map((j) => j.preco));
        const maior = Math.max(...corridaXbox.map((j) => j.preco));
        const maisBaratos = corridaXbox.filter((j) => j.preco === menor).map((j) => j.titulo);
        const maisCaros = corridaXbox.filter((j) => j.preco === maior).map((j) => j.titulo);
        const capcomNoXbox = [...new Set(jogos.filter((j) => j.desenvolvedora === "Capcom" && j.plataforma === "Xbox Series X|S")
          .map((j) => j.titulo))];

        conversar(cliente.id, ["me mostra o seu jogo de corrida mais barato", "e o mais caro de corrida?",
          "quais jogos da Capcom vocês têm?", "quantos jogos vocês têm?"]).then(([barato, caro, capcom, quantos]) => {
          expect(maisBaratos.some((t) => barato.includes(t)), `cita o mais barato (${maisBaratos.join(", ")})`).to.eq(true);
          expect(barato).to.contain(reais(menor));
          expect(maisCaros.some((t) => caro.includes(t)), `cita o mais caro (${maisCaros.join(", ")})`).to.eq(true);
          expect(caro).to.contain(reais(maior));
          expect(capcomNoXbox, "a Capcom tem jogos no Xbox").to.not.be.empty;
          capcomNoXbox.forEach((t) => expect(capcom, `${t} é da Capcom`).to.contain(t));
          // o total de títulos do catálogo (a divisão por plataforma é opcional na resposta)
          const total = Number((quantos.match(/(\d+) jogos/) || [])[1]);
          expect(total, "total de jogos do catálogo").to.be.at.least(120);
        });
      }));
    });

    it("RNF0044 — continua a pergunta da loja (\"o mais barato\" → \"e de PC?\") e responde os mais vendidos", () => {
      cy.request(`${API}/jogos`).its("body").then((todos) => clienteCom({ generos: ["Puzzle"], plataformas: ["Nintendo Switch"] })
        .then((cliente) => {
          const doPc = todos.filter((j) => j.plataforma === "PC" && j.estoque > 0);
          const menor = Math.min(...doPc.map((j) => j.preco));
          const maisBaratosDoPc = doPc.filter((j) => j.preco === menor).map((j) => j.titulo);
          conversar(cliente.id, ["me mostra o seu produto mais barato", "e de PC?", "o que está fazendo sucesso na loja?"])
            .then(([, dePc, sucesso]) => {
              expect(maisBaratosDoPc.some((t) => dePc.includes(t)), `o mais barato do PC (${maisBaratosDoPc.join(", ")})`).to.eq(true);
              expect(dePc).to.contain(reais(menor)).and.contain("PC");
              expect(sucesso).to.match(/vendid|sucesso|popular/i).and.not.contain(RECUSA);
              expect(todos.some((j) => sucesso.includes(j.titulo)), "os mais vendidos citam jogos do catálogo").to.eq(true);
              // o gênero dito de cada jogo é o do catálogo (a IA já chamou o Hogwarts Legacy, de Aventura, de RPG)
              todos.filter((j) => sucesso.includes(`**${j.titulo}**`)).forEach((j) => {
                const linha = sucesso.split("\n").find((l) => l.includes(`**${j.titulo}**`));
                ["RPG", "Corrida", "Esporte", "Luta", "Terror", "Tiro"].filter((g) => g !== j.genero)
                  .forEach((g) => expect(linha, `${j.titulo} é de ${j.genero}`).to.not.match(new RegExp(`\\b${g}\\b`)));
              });
            });
        }));
    });

    it("RNF0044 — responde pedidos fora do comum: jogar no banho, o melhor jogo de um gênero e carro de verdade", () => {
      catalogo().then((jogos) => clienteCom({ generos: ["Corrida"], plataformas: ["Xbox Series X|S"] }).then((cliente) => {
        const titulosDe = (g) => [...new Set(jogos.filter((j) => j.genero === g && j.plataforma === "Xbox Series X|S").map((j) => j.titulo))];
        conversar(cliente.id, ["qual melhor jogo para mim quando estou tomando banho?", "me liste o melhor jogo de luta",
          "quando eu jogo nidhogg 2 qual seria o melhor carro para eu comprar?", "qual o melhor carro no Forza Horizon 5?"])
          .then(([banho, luta, carro, forza]) => {
          // no banho não dá, mas sugere jogos para depois
          expect(banho).to.contain("banho").and.not.contain(RECUSA);
          expect(citados(banho, jogos), "sugere jogos para depois do banho").to.not.be.empty;
          // "o melhor jogo de luta" é um pedido de recomendação do gênero
          expect(titulosDe("Luta").some((t) => luta.includes(t)), "cita um jogo de Luta").to.eq(true);
          // o Nidhogg 2 é de Luta (duelos de espada): não tem carros; carro de verdade não é com a loja, mas quem
          // curte carros pode gostar dos jogos de corrida
          expect(carro).to.contain("Nidhogg 2").and.contain("Luta").and.contain("não tem carros").and.contain("só ajudo com jogos");
          expect(titulosDe("Corrida").some((t) => carro.includes(t)), "liga carros aos jogos de corrida").to.eq(true);
          // o Forza Horizon 5 tem carros (o catálogo cita "centenas de carros"): a resposta fala do jogo
          expect(forza).to.contain("Forza Horizon 5").and.contain("carros fazem parte do jogo").and.not.contain("não tem carros");
        });
      }));
    });

    it("RNF0044 — com 4 gêneros marcados, a recomendação da IA traz um jogo de cada gênero", () => {
      const generos = ["Ação", "Puzzle", "Terror", "Corrida"];
      catalogo().then((jogos) => clienteCom({ generos, plataformas: ["PlayStation 5"] }).then((cliente) => {
        conversar(cliente.id, ["Me recomenda um jogo?"]).then(([resposta]) => {
          generos.forEach((g) => {
            const doGenero = [...new Set(jogos.filter((j) => j.genero === g && j.plataforma === "PlayStation 5").map((j) => j.titulo))];
            expect(doGenero.some((t) => resposta.includes(t)), `cita um jogo de ${g}`).to.eq(true);
          });
        });
      }));
    });

    it("RNF0044 — a recomendação segue as preferências mudadas na tela, sem gênero ou plataforma preferida inventados", () => {
      catalogo().then((jogos) => criarCliente().then((cliente) => {
        cy.visit("/index.html");
        cy.get("[data-cy=nav-assistente]").click();
        cy.get("[data-cy=assist-cliente] option").should("have.length.greaterThan", 1);
        cy.get("[data-cy=assist-cliente]").select(String(cliente.id));
        cy.get('[data-cy=pref-genero][value="Simulação"]').check();
        cy.get('[data-cy=pref-plataforma][value="Nintendo Switch"]').check();
        cy.get("[data-cy=pref-orcamento]").type("100");
        cy.get("[data-cy=btn-salvar-preferencias]").click();
        cy.get("[data-cy=mensagem]").should("have.class", "sucesso");

        const simulacaoNoSwitch = [...new Set(jogos.filter((j) => j.genero === "Simulação" && j.plataforma === "Nintendo Switch"
          && j.preco <= 100).map((j) => j.titulo))];
        const luta = [...new Set(jogos.filter((j) => j.genero === "Luta").map((j) => j.titulo))];
        conversar(cliente.id, ["Me recomenda um jogo?", "e o de luta?"]).then(([recomendacao, deLuta]) => {
          expect(simulacaoNoSwitch.some((t) => recomendacao.includes(t)), "cita um jogo de Simulação do Switch até R$ 100").to.eq(true);
          expect(recomendacao).to.not.match(PREFERIDA_ERRADA);
          expect(luta.some((t) => deLuta.includes(t)), "\"e o de luta?\" traz um jogo de Luta").to.eq(true);
          expect(deLuta, "Luta não é gênero preferido do cliente").to.not.match(/Luta, um dos seus gêneros preferidos/);
        });
      }));
    });
  });
});
