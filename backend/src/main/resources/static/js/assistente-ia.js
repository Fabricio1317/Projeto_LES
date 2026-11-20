// Assistente de recomendação com IA generativa (RNF0044).
// Perfil cliente: chat com recomendações, busca e dúvidas, avaliação útil / não útil de cada resposta
// e preferências do perfil. Perfil administrador: treinamento (ajuste fino) do modelo.

const assistente = {
  clienteId: null,
  conversaId: null, // começa uma conversa nova a cada cliente ou "Nova conversa"
  opcoes: null,
  enviando: false,
};
let timerIa = null;

// --------------------- perfil: qual aba aparece ---------------------
function assistAplicarPerfil() {
  const admin = perfilAtual() === "ADMINISTRADOR";
  document.querySelector("[data-cy=nav-assistente]").hidden = admin;
  document.querySelector("[data-cy=nav-ia]").hidden = !admin;
  const abertaProibida = (admin && !el("tab-assistente").hidden) || (!admin && !el("tab-ia").hidden);
  if (abertaProibida) document.querySelector('[data-tab="tab-cadastro"]').click();
}

el("perfil-usuario").addEventListener("change", assistAplicarPerfil);
assistAplicarPerfil();

document.querySelector('[data-tab="tab-assistente"]').addEventListener("click", assistAbrir);
document.querySelector('[data-tab="tab-ia"]').addEventListener("click", iaAbrir);

// --------------------- formatação das respostas ---------------------
function assistEscapar(texto) {
  return String(texto ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Markdown simples da IA: **negrito**, listas com "- " ou "* " e quebras de linha. */
function assistFormatar(texto) {
  return assistEscapar(texto).split("\n").map((linha) => {
    const comNegrito = linha.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    return /^\s*[-*]\s+/.test(linha) ? `<span class="assist-item">${comNegrito.replace(/^\s*[-*]\s+/, "")}</span>` : comNegrito;
  }).join("<br>");
}

// --------------------- abrir a aba do cliente ---------------------
async function assistAbrir() {
  limparMensagem();
  const cliente = perfilAtual() === "CLIENTE";
  el("assist-acesso-negado").hidden = cliente;
  el("assist-filtros").hidden = !cliente;
  if (!cliente) {
    el("assist-conteudo").hidden = true;
    return;
  }
  try {
    const [clientes, opcoes] = await Promise.all([ClienteApi.consultar({ status: "ATIVO" }), AssistenteApi.opcoes()]);
    assistente.opcoes = opcoes;
    const select = el("assist-cliente");
    const atual = select.value;
    select.innerHTML = '<option value="">Selecione o cliente</option>' +
      clientes.map((c) => `<option value="${c.id}">${assistEscapar(c.nome)} — ${c.codigoCliente}</option>`).join("");
    select.value = atual;
    await assistAtualizarStatus();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("assist-cliente").addEventListener("change", async (ev) => {
  limparMensagem();
  assistente.clienteId = ev.target.value || null;
  el("assist-conteudo").hidden = !assistente.clienteId;
  if (!assistente.clienteId) return;
  assistNovaConversa();
  try {
    await Promise.all([assistCarregarPreferencias(), assistCarregarAvaliacoes(), assistAtualizarStatus()]);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

async function assistAtualizarStatus() {
  const status = await AssistenteApi.status();
  el("assist-modelo").textContent = status.modeloEmUso;
  el("assist-aviso-ia").hidden = status.configurada;
  el("assist-aviso-ia").textContent = status.configurada ? "" : "IA ainda não configurada: " + status.motivoNaoConfigurada;
}

// --------------------- conversa ---------------------
function assistNovaConversa() {
  assistente.conversaId = null;
  const nome = el("assist-cliente").selectedOptions[0]?.textContent.split(" — ")[0] || "";
  el("assist-mensagens").innerHTML = `
    <div class="assist-balao ia boas-vindas" data-cy="assist-boas-vindas">
      Olá${nome ? ", " + assistEscapar(nome.split(" ")[0]) : ""}! Sou o assistente da Nexus. Posso recomendar jogos com base nas suas
      compras e preferências, procurar jogos no catálogo e responder dúvidas. Como posso ajudar?
    </div>`;
}

el("btn-nova-conversa").addEventListener("click", assistNovaConversa);

document.querySelectorAll("[data-cy=assist-sugestao]").forEach((botao) => {
  botao.addEventListener("click", () => {
    el("form-assistente").elements["mensagem"].value = botao.textContent;
    el("form-assistente").requestSubmit();
  });
});

el("form-assistente").elements["mensagem"].addEventListener("keydown", (ev) => {
  if (ev.key === "Enter" && !ev.shiftKey) {
    ev.preventDefault();
    el("form-assistente").requestSubmit();
  }
});

el("form-assistente").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const campo = ev.target.elements["mensagem"];
  const texto = campo.value.trim();
  if (!texto || assistente.enviando || !assistente.clienteId) return;
  limparMensagem();
  assistente.enviando = true;
  ev.target.querySelector("button[type=submit]").disabled = true;

  const lista = el("assist-mensagens");
  lista.insertAdjacentHTML("beforeend", `<div class="assist-balao cliente" data-cy="msg-cliente">${assistFormatar(texto)}</div>`);
  lista.insertAdjacentHTML("beforeend", '<div class="assist-balao ia digitando" data-cy="assist-digitando">Pensando<span>.</span><span>.</span><span>.</span></div>');
  lista.scrollTop = lista.scrollHeight;
  campo.value = "";

  try {
    const resposta = await AssistenteApi.enviar(assistente.clienteId, assistente.conversaId, texto);
    assistente.conversaId = resposta.conversaId;
    lista.querySelector("[data-cy=assist-digitando]")?.remove();
    lista.insertAdjacentHTML("beforeend", assistBalaoResposta(resposta));
  } catch (erro) {
    lista.querySelector("[data-cy=assist-digitando]")?.remove();
    lista.insertAdjacentHTML("beforeend", `<div class="assist-balao ia erro" data-cy="assist-erro">${assistEscapar(mensagemDeErro(erro))}</div>`);
  } finally {
    assistente.enviando = false;
    ev.target.querySelector("button[type=submit]").disabled = false;
    lista.scrollTop = lista.scrollHeight;
  }
});

function assistBalaoResposta(m) {
  return `
    <div class="assist-balao ia" data-cy="msg-assistente" data-id="${m.id}">
      <div class="assist-texto" data-cy="msg-assistente-texto">${assistFormatar(m.resposta)}</div>
      <div class="assist-rodape">
        <span class="assist-modelo-msg" title="Modelo que respondeu">${assistEscapar(m.modelo)}</span>
        <span class="assist-avaliar">
          Esta resposta foi:
          <button type="button" class="btn-avaliar ${m.avaliacao === "UTIL" ? "marcado" : ""}" data-avaliacao="UTIL" data-cy="btn-util">Útil</button>
          <button type="button" class="btn-avaliar ${m.avaliacao === "NAO_UTIL" ? "marcado" : ""}" data-avaliacao="NAO_UTIL" data-cy="btn-nao-util">Não útil</button>
        </span>
        <span class="assist-avaliado" data-cy="avaliacao-registrada" ${m.avaliacao ? "" : "hidden"}>${m.avaliacao ? "Feedback registrado: " + m.avaliacaoDescricao : ""}</span>
      </div>
    </div>`;
}

// RNF0044 (item 3) — avaliação de cada resposta no próprio chat
el("assist-mensagens").addEventListener("click", async (ev) => {
  const botao = ev.target.closest(".btn-avaliar");
  if (!botao) return;
  const balao = botao.closest("[data-cy=msg-assistente]");
  try {
    const avaliada = await AssistenteApi.avaliar(balao.dataset.id, botao.dataset.avaliacao);
    balao.querySelectorAll(".btn-avaliar").forEach((b) => b.classList.toggle("marcado", b.dataset.avaliacao === avaliada.avaliacao));
    const registro = balao.querySelector("[data-cy=avaliacao-registrada]");
    registro.textContent = "Feedback registrado: " + avaliada.avaliacaoDescricao;
    registro.hidden = false;
    await assistCarregarAvaliacoes();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

async function assistCarregarAvaliacoes() {
  const avaliacoes = await AssistenteApi.avaliacoes(assistente.clienteId);
  el("assist-lista-avaliacoes").innerHTML = avaliacoes.length
    ? avaliacoes.slice(0, 8).map((a) => `
        <li data-cy="item-avaliacao" data-id="${a.id}" data-avaliacao="${a.avaliacao}">
          <span class="selo-avaliacao ${a.avaliacao}">${a.avaliacaoDescricao}</span>
          <span class="pergunta">${assistEscapar(a.pergunta)}</span>
          <span class="hint">${formatarData(a.dataAvaliacao)}</span>
        </li>`).join("")
    : '<li class="hint">Nenhuma resposta avaliada ainda.</li>';
}

// --------------------- preferências (critério "e") ---------------------
async function assistCarregarPreferencias() {
  const pref = await AssistenteApi.preferencias(assistente.clienteId);
  const chips = (lista, marcados, nome, dataCy) => lista.map((valor) => `
    <label class="chip-categoria">
      <input type="checkbox" name="${nome}" value="${assistEscapar(valor)}" data-cy="${dataCy}" ${marcados.includes(valor) ? "checked" : ""}>${assistEscapar(valor)}
    </label>`).join("");
  el("pref-generos").innerHTML = chips(assistente.opcoes.generos, pref.generos, "generos", "pref-genero");
  el("pref-plataformas").innerHTML = chips(assistente.opcoes.plataformas, pref.plataformas, "plataformas", "pref-plataforma");
  const form = el("form-preferencias");
  form.elements["orcamentoMaximo"].value = pref.orcamentoMaximo ?? "";
  form.elements["observacoes"].value = pref.observacoes ?? "";
}

el("form-preferencias").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  limparMensagem();
  const form = ev.target;
  const marcados = (nome) => [...form.querySelectorAll(`input[name=${nome}]:checked`)].map((c) => c.value);
  try {
    await AssistenteApi.salvarPreferencias(assistente.clienteId, {
      generos: marcados("generos"),
      plataformas: marcados("plataformas"),
      orcamentoMaximo: form.elements["orcamentoMaximo"].value ? Number(form.elements["orcamentoMaximo"].value) : null,
      observacoes: form.elements["observacoes"].value,
    });
    mostrarMensagem("Preferências salvas. As próximas recomendações já levam em conta o que você marcou.", "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- treinamento (administrador) ---------------------
async function iaAbrir() {
  limparMensagem();
  const admin = perfilAtual() === "ADMINISTRADOR";
  el("ia-acesso-negado").hidden = admin;
  el("ia-conteudo").hidden = !admin;
  if (admin) await iaCarregar();
}

async function iaCarregar() {
  clearTimeout(timerIa);
  try {
    const [status, dataset, treinamentos, feedbacks] = await Promise.all([
      IaApi.status(), IaApi.dataset(), IaApi.treinamentos(), IaApi.feedbacks()]);
    el("ia-provedor").textContent = status.provedor;
    el("ia-modelo-em-uso").textContent = status.modeloEmUso;
    el("ia-vendas").textContent = status.vendasEfetivadas;
    el("ia-feedback").textContent = `${status.feedbackUtil} / ${status.feedbackNaoUtil}`;
    el("ia-novos-dados").textContent = status.retreinoAutomatico
      ? `${status.novosDados} de ${status.limiarRetreino} para o próximo treino automático`
      : `${status.novosDados} (treino automático desligado)`;
    el("ia-aviso").hidden = status.configurada && !status.treinamentoEmAndamento;
    el("ia-aviso").textContent = !status.configurada ? "IA não configurada: " + status.motivoNaoConfigurada
      : "Ajuste fino em andamento. O chat continua usando o modelo atual até o novo ficar pronto.";
    el("btn-treinar").disabled = !status.configurada || status.treinamentoEmAndamento;

    el("ia-resumo-dataset").innerHTML = `<strong data-cy="ia-total-exemplos">${dataset.total}</strong> exemplos:
      <strong data-cy="ia-exemplos-vendas">${dataset.exemplosVendas}</strong> gerados das vendas de ${dataset.clientes} clientes e
      <strong data-cy="ia-exemplos-feedback">${dataset.exemplosFeedback}</strong> de respostas avaliadas como úteis.
      <strong data-cy="ia-feedback-descartado">${dataset.feedbackDescartado}</strong> respostas não úteis ficaram de fora.`;
    el("ia-amostra-dataset").textContent = dataset.amostra.map((linha) => JSON.stringify(JSON.parse(linha), null, 2)).join("\n\n");

    el("tbody-treinamentos").innerHTML = treinamentos.length ? treinamentos.map((t) => `
      <tr data-cy="linha-treinamento" data-status="${t.status}">
        <td>${assistEscapar(t.nomeModelo)}</td>
        <td><span class="status-badge ${t.status}" data-cy="status-treinamento">${t.statusDescricao}</span></td>
        <td>${{ AUTOMATICO: "Automático", RECUPERADO: "Recuperado na subida" }[t.motivo] || "Manual"}</td>
        <td>${t.exemplosVendas} + ${t.exemplosFeedback}</td>
        <td>${formatarData(t.iniciadoEm)}</td>
        <td>${t.concluidoEm ? formatarData(t.concluidoEm) : "—"}</td>
        <td class="ia-detalhe" data-cy="detalhe-treinamento">${assistEscapar(t.mensagemErro || [t.modeloAjustado, t.progresso].filter(Boolean).join(" — ") || "")}</td>
      </tr>`).join("") : '<tr><td colspan="7" class="hint">Nenhum ajuste fino feito ainda.</td></tr>';

    el("tbody-feedbacks").innerHTML = feedbacks.map((f) => `
      <tr data-cy="linha-feedback" data-id="${f.id}" data-avaliacao="${f.avaliacao}">
        <td>${formatarData(f.dataAvaliacao)}</td>
        <td>${assistEscapar(f.clienteNome)}</td>
        <td>${assistEscapar(f.pergunta)}</td>
        <td><span class="selo-avaliacao ${f.avaliacao}">${f.avaliacaoDescricao}</span></td>
        <td class="hint">${assistEscapar(f.modelo)}</td>
      </tr>`).join("");

    // enquanto treina, atualiza sozinho a cada 20 s (só com a aba aberta)
    if (status.treinamentoEmAndamento) {
      timerIa = setTimeout(() => { if (!el("tab-ia").hidden) iaCarregar(); }, 20000);
    }
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("btn-atualizar-ia").addEventListener("click", iaCarregar);

el("btn-treinar").addEventListener("click", async () => {
  limparMensagem();
  el("btn-treinar").disabled = true;
  try {
    const treinamento = await IaApi.treinar();
    mostrarMensagem(`Ajuste fino ${treinamento.nomeModelo} iniciado com ${treinamento.exemplosVendas + treinamento.exemplosFeedback} exemplos.`, "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
  await iaCarregar();
});

el("btn-baixar-dataset").addEventListener("click", async () => {
  try {
    const arquivo = await IaApi.arquivoDataset();
    const link = document.createElement("a");
    link.href = URL.createObjectURL(arquivo);
    link.download = "dataset-ajuste-fino.jsonl";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});
