// Gestão de vendas (administrador): consulta dos pedidos, despacho e confirmação de entrega,
// pedidos de troca (visualizar, autorizar e confirmar o recebimento), estoque, cupons e parâmetros.
// A validação do pagamento (APROVADA / REPROVADA) é feita automaticamente pelo sistema.

let timerGestao = null;

document.querySelector('[data-tab="tab-vendas"]').addEventListener("click", carregarGestaoVendas);

async function carregarGestaoVendas() {
  limparMensagem();
  await Promise.all([carregarPedidos(), carregarTrocas(), carregarEstoque(), carregarCupons(), carregarParametros()]);
}

// --------------------- compras ---------------------
el("form-filtro-pedidos").addEventListener("submit", (ev) => {
  ev.preventDefault();
  carregarPedidos();
});

function resumoPagamento(p) {
  const partes = p.pagamentos.map((pg) => {
    const retorno = pg.aprovado == null ? "" : pg.aprovado ? " ✓" : " ✗ recusado";
    return `${pg.bandeira} ${pg.numeroMascarado.slice(-4)}: ${formatarMoeda(pg.valor)}${retorno}`;
  });
  p.cupons.forEach((c) => partes.push(`Cupom ${c.codigo}: ${formatarMoeda(c.valor)}`));
  if (p.cupomTrocoGerado) partes.push(`Troco em cupom ${p.cupomTrocoGerado}: ${formatarMoeda(p.valorTroco)}`);
  if (p.motivoReprovacao) partes.push(`<span class="texto-erro" data-cy="motivo-reprovacao">${p.motivoReprovacao}</span>`);
  return partes.join("<br>");
}

function acoesDoPedido(p) {
  if (p.status === "EM_PROCESSAMENTO") {
    return `<span class="hint" data-cy="aguardando-validacao">Aguardando validação automática</span>`;
  }
  if (p.status === "APROVADA") {
    return `<button type="button" class="btn-secondary btn-sm" data-cy="btn-despachar" data-id="${p.id}">Despachar</button>`;
  }
  if (p.status === "EM_TRANSPORTE") {
    return `<button type="button" class="btn-secondary btn-sm" data-cy="btn-confirmar-entrega" data-id="${p.id}">Confirmar entrega</button>`;
  }
  return "";
}

async function carregarPedidos() {
  clearTimeout(timerGestao);
  try {
    const status = el("form-filtro-pedidos").elements["status"].value;
    const pedidos = await PedidoApi.listar(status);
    el("tbody-pedidos").innerHTML = pedidos.map((p) => `
      <tr data-cy="linha-pedido" data-codigo="${p.codigo}">
        <td class="codigo">${p.codigo}</td>
        <td>#${p.clienteId}</td>
        <td class="data">${formatarData(p.dataCompra)}</td>
        <td class="preco">${formatarMoeda(p.total)}</td>
        <td class="col-pagamento">${resumoPagamento(p)}</td>
        <td>${statusPedidoBadge(p)}</td>
        <td class="acoes">${acoesDoPedido(p)}</td>
      </tr>`).join("");
    // enquanto houver compra aguardando a validação automática, a tela se atualiza sozinha
    // (o estoque é recarregado depois das compras, para refletir a baixa das que foram aprovadas)
    if (pedidos.some((p) => p.status === "EM_PROCESSAMENTO") && !el("tab-vendas").hidden) {
      timerGestao = setTimeout(async () => { await carregarPedidos(); await carregarEstoque(); }, 1500);
    }
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("tbody-pedidos").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-id]");
  if (!btn) return;
  const acoes = {
    "btn-despachar": PedidoApi.despachar,
    "btn-confirmar-entrega": PedidoApi.confirmarEntrega,
  };
  limparMensagem();
  try {
    const pedido = await acoes[btn.dataset.cy](btn.dataset.id);
    mostrarMensagem(`Compra ${pedido.codigo}: status ${pedido.statusDescricao}.`, "sucesso");
    await Promise.all([carregarPedidos(), carregarEstoque()]);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- pedidos de troca (RF0042 / RF0041 / RF0043) ---------------------
el("form-filtro-trocas").addEventListener("submit", (ev) => {
  ev.preventDefault();
  carregarTrocas();
});

function acoesDaTroca(t) {
  if (t.status === "EM_TROCA") {
    return `<button type="button" class="btn-secondary btn-sm" data-cy="btn-autorizar-troca" data-id="${t.id}">Autorizar troca</button>`;
  }
  if (t.status === "TROCA_AUTORIZADA") {
    return `
      <label class="checkbox-label inline" title="RF0054 — dar entrada dos itens devolvidos no estoque">
        <input type="checkbox" data-cy="troca-retornar-estoque" checked> Retornar ao estoque
      </label>
      <button type="button" class="btn-secondary btn-sm" data-cy="btn-confirmar-recebimento" data-id="${t.id}">Confirmar recebimento</button>`;
  }
  return t.retornouEstoque == null ? "" : `<span class="hint">${t.retornouEstoque ? "Itens retornaram ao estoque" : "Itens não retornaram ao estoque"}</span>`;
}

async function carregarTrocas() {
  try {
    const status = el("form-filtro-trocas").elements["status"].value;
    const trocas = await TrocaApi.listar(status);
    el("tbody-trocas").innerHTML = trocas.map((t) => `
      <tr data-cy="linha-troca" data-codigo="${t.codigo}">
        <td class="codigo">${t.codigo}</td>
        <td class="codigo">${t.pedidoCodigo}</td>
        <td>#${t.clienteId}</td>
        <td>${t.tipoDescricao}</td>
        <td>${t.itens.map((i) => `${i.quantidade}× ${i.titulo}`).join("<br>")}</td>
        <td class="preco">${formatarMoeda(t.valor)}</td>
        <td data-cy="troca-cupom">${t.cupomGerado || "—"}</td>
        <td>${statusTrocaBadge(t)}</td>
        <td class="acoes">${acoesDaTroca(t)}</td>
      </tr>`).join("");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("tbody-trocas").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-id]");
  if (!btn) return;
  limparMensagem();
  try {
    let troca;
    if (btn.dataset.cy === "btn-autorizar-troca") {
      troca = await TrocaApi.autorizar(btn.dataset.id);
      mostrarMensagem(`Troca ${troca.codigo} autorizada; o cliente foi notificado.`, "sucesso");
    } else {
      const retornar = btn.closest("tr").querySelector("[data-cy=troca-retornar-estoque]").checked;
      troca = await TrocaApi.confirmarRecebimento(btn.dataset.id, retornar);
      mostrarMensagem(`Troca ${troca.codigo}: recebimento confirmado, status ${troca.statusDescricao}. `
        + `Cupom de troca ${troca.cupomGerado} de ${formatarMoeda(troca.valor)} gerado para o cliente.`, "sucesso");
    }
    await Promise.all([carregarTrocas(), carregarPedidos(), carregarEstoque(), carregarCupons()]);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- estoque ---------------------
async function carregarEstoque() {
  try {
    const jogos = await JogoApi.estoque();
    el("tbody-estoque").innerHTML = jogos.map((j) => `
      <tr data-cy="linha-estoque" data-jogo-id="${j.id}">
        <td>${j.titulo}</td>
        <td>${chipPlataforma(j.plataforma)}</td>
        <td class="preco">${formatarMoeda(j.preco)}</td>
        <td data-cy="estoque-fisico">${j.estoque}</td>
        <td data-cy="estoque-disponivel">${j.disponivel}</td>
        <td class="acoes">
          <input type="number" min="0" class="input-qtd" data-cy="input-ajuste-estoque" value="${j.estoque}">
          ${iconBtn({ icon: "salvar", titulo: "Ajustar estoque", dataCy: "btn-ajustar-estoque", id: j.id, variant: "icon-btn--success" })}
        </td>
      </tr>`).join("");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("tbody-estoque").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-cy=btn-ajustar-estoque]");
  if (!btn) return;
  limparMensagem();
  const estoque = parseInt(btn.closest("tr").querySelector("[data-cy=input-ajuste-estoque]").value, 10);
  try {
    const jogo = await JogoApi.ajustarEstoque(btn.dataset.id, estoque);
    mostrarMensagem(`Estoque de ${jogo.titulo} ajustado para ${jogo.estoque}.`, "sucesso");
    await carregarEstoque();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- cupons ---------------------
async function carregarCupons() {
  try {
    const cupons = await CupomApi.listar();
    el("tbody-cupons").innerHTML = cupons.map((c) => `
      <tr data-cy="linha-cupom-admin">
        <td>${c.codigo}</td>
        <td>${c.tipo}</td>
        <td>${formatarMoeda(c.valor)}</td>
        <td>${c.clienteId ? "#" + c.clienteId : "—"}</td>
        <td>${c.validade ? new Date(c.validade + "T00:00:00").toLocaleDateString("pt-BR") : "—"}</td>
        <td>${c.utilizado ? "Sim" : "Não"}</td>
      </tr>`).join("");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("form-cupom").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  limparMensagem();
  const form = ev.target;
  const dados = Object.fromEntries(new FormData(form).entries());
  const payload = {
    codigo: dados.codigo.trim(),
    tipo: dados.tipo,
    valor: lerValor(dados.valor),
    clienteId: dados.clienteId ? Number(dados.clienteId) : null,
    validade: dados.validade || null,
  };
  try {
    const cupom = await CupomApi.cadastrar(payload);
    mostrarMensagem(`Cupom ${cupom.codigo} cadastrado.`, "sucesso");
    form.reset();
    await carregarCupons();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- parâmetros (RN0044) ---------------------
async function carregarParametros() {
  try {
    const parametros = await ParametroApi.listar();
    const prazo = parametros.find((p) => p.chave === "PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS");
    if (prazo) el("form-parametro").elements["prazo"].value = prazo.valor;
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("form-parametro").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  limparMensagem();
  try {
    const p = await ParametroApi.alterar("PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS", ev.target.elements["prazo"].value);
    mostrarMensagem(`Prazo de bloqueio do carrinho alterado para ${p.valor} segundos.`, "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});
