// Loja: catálogo, carrinho (RF0031/RF0032) e finalização da compra (RF0033–RF0038).

const loja = {
  clienteId: null,
  carrinho: null,
  enderecos: [],
  cartoes: [],
  cupons: [],
  frete: null,
  timerCarrinho: null,
};

function el(id) {
  return document.getElementById(id);
}

function descreverPrazo(segundos) {
  if (segundos < 60) return `${segundos} segundo(s)`;
  const min = Math.floor(segundos / 60);
  const seg = segundos % 60;
  return seg ? `${min} min ${seg} s` : `${min} minuto(s)`;
}

function lerValor(texto) {
  const n = Number(String(texto || "").replace(",", "."));
  return Number.isFinite(n) ? n : 0;
}

// --------------------- clientes da loja ---------------------
document.querySelector('[data-tab="tab-loja"]').addEventListener("click", carregarClientesLoja);

async function carregarClientesLoja() {
  try {
    const clientes = await ClienteApi.consultar({ status: "ATIVO" });
    const select = el("loja-cliente");
    const atual = select.value;
    select.innerHTML = '<option value="">Selecione o cliente</option>' +
      clientes.map((c) => `<option value="${c.id}">${c.nome} (${c.codigoCliente})</option>`).join("");
    select.value = clientes.some((c) => String(c.id) === atual) ? atual : "";
    if (select.value) await carregarLoja();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("loja-cliente").addEventListener("change", (ev) => {
  limparMensagem();
  loja.clienteId = ev.target.value || null;
  carregarLoja();
});

async function carregarLoja() {
  clearTimeout(loja.timerCarrinho);
  el("checkout").hidden = true;
  loja.clienteId = el("loja-cliente").value || null;
  el("loja-conteudo").hidden = !loja.clienteId;
  if (!loja.clienteId) return;
  await carregarCarrinho();
  await Promise.all([carregarCatalogo(), carregarMinhasCompras()]);
}

// --------------------- catálogo ---------------------
async function carregarCatalogo() {
  const tbody = el("tbody-catalogo");
  // data-pronto indica que a tabela terminou de ser redesenhada (usado pelos testes automatizados)
  tbody.dataset.pronto = "false";
  try {
    const jogos = await JogoApi.catalogo(loja.clienteId);
    const noCarrinho = {};
    (loja.carrinho ? loja.carrinho.itens : []).forEach((i) => { noCarrinho[i.jogoId] = i.quantidade; });
    tbody.innerHTML = "";
    jogos.forEach((j) => {
      const disponivel = Math.max(0, j.disponivel - (noCarrinho[j.id] || 0));
      const tr = document.createElement("tr");
      tr.dataset.cy = "linha-jogo";
      tr.dataset.jogoId = j.id;
      tr.innerHTML = `
        <td>${j.titulo}</td>
        <td>${chipPlataforma(j.plataforma)}</td>
        <td>${j.genero}</td>
        <td>${seloClassificacao(j.classificacaoIndicativa)}</td>
        <td class="preco">${formatarMoeda(j.preco)}</td>
        <td data-cy="disponivel-jogo">${disponivel}</td>
        <td><input type="number" min="1" value="1" class="input-qtd" data-cy="qtd-jogo"></td>
        <td class="acoes">${iconBtn({ icon: "carrinho", titulo: "Adicionar ao carrinho", dataCy: "btn-adicionar-carrinho", id: j.id })}</td>`;
      tbody.appendChild(tr);
    });
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  } finally {
    tbody.dataset.pronto = "true";
  }
}

el("tbody-catalogo").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-cy=btn-adicionar-carrinho]");
  if (!btn) return;
  const quantidade = parseInt(btn.closest("tr").querySelector("[data-cy=qtd-jogo]").value, 10);
  await executarNoCarrinho(() => CarrinhoApi.adicionar(loja.clienteId, Number(btn.dataset.id), quantidade),
    "Jogo adicionado ao carrinho.");
});

// --------------------- carrinho ---------------------
async function carregarCarrinho() {
  try {
    renderizarCarrinho(await CarrinhoApi.obter(loja.clienteId));
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

async function executarNoCarrinho(acao, mensagemSucesso) {
  limparMensagem();
  try {
    renderizarCarrinho(await acao());
    mostrarMensagem(mensagemSucesso, "sucesso");
    el("checkout").hidden = true;
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  } finally {
    await carregarCatalogo();
  }
}

function renderizarCarrinho(c) {
  loja.carrinho = c;

  const avisos = el("carrinho-avisos");
  avisos.hidden = c.avisos.length === 0;
  avisos.innerHTML = c.avisos.map((a) => `<div data-cy="aviso-carrinho">${a}</div>`).join("");

  // RN0044 — notificação 5 minutos antes de o bloqueio expirar
  const alerta = el("carrinho-alerta-expiracao");
  alerta.hidden = !c.alertaExpiracao;
  if (c.alertaExpiracao) {
    const min = Math.floor(c.segundosRestantes / 60);
    const seg = c.segundosRestantes % 60;
    alerta.textContent = `Atenção: faltam ${min} min ${seg} s para os itens do carrinho serem liberados. Finalize a compra antes do prazo.`;
  }

  const tbody = el("tbody-carrinho");
  tbody.innerHTML = "";
  c.itens.forEach((i) => {
    const tr = document.createElement("tr");
    tr.dataset.cy = "linha-carrinho";
    tr.dataset.itemId = i.id;
    tr.innerHTML = `
      <td>${i.titulo}</td>
      <td class="preco">${formatarMoeda(i.precoUnitario)}</td>
      <td><input type="number" min="1" value="${i.quantidade}" class="input-qtd" data-cy="carrinho-qtd"></td>
      <td class="preco" data-cy="carrinho-item-subtotal">${formatarMoeda(i.subtotal)}</td>
      <td class="acoes">
        ${iconBtn({ icon: "salvar", titulo: "Atualizar quantidade", dataCy: "btn-atualizar-qtd", id: i.id, variant: "icon-btn--success" })}
        ${iconBtn({ icon: "remover", titulo: "Remover do carrinho", dataCy: "btn-remover-item", id: i.id, variant: "icon-btn--danger" })}
      </td>`;
    tbody.appendChild(tr);
  });
  el("carrinho-vazio").hidden = c.itens.length > 0;

  // RNF0042 — itens retirados por prazo, informando o tempo parametrizado
  el("carrinho-removidos").hidden = c.itensRemovidos.length === 0;
  document.querySelector("[data-cy=msg-itens-removidos]").textContent =
    `Os itens abaixo foram retirados do carrinho porque o prazo de bloqueio de ${descreverPrazo(c.prazoBloqueioSegundos)} ` +
    "expirou sem a finalização da compra. Adicione-os novamente (ou descarte-os) para poder comprar.";
  const tbodyRemovidos = el("tbody-removidos");
  tbodyRemovidos.innerHTML = "";
  c.itensRemovidos.forEach((i) => {
    const tr = document.createElement("tr");
    tr.dataset.cy = "linha-removido";
    tr.innerHTML = `
      <td>${i.titulo}</td>
      <td>${i.quantidade}</td>
      <td class="acoes">
        <button type="button" class="btn-secondary btn-sm" data-cy="btn-readicionar" data-jogo-id="${i.jogoId}" data-quantidade="${i.quantidade}">Adicionar novamente</button>
        <button type="button" class="btn-secondary btn-sm" data-cy="btn-descartar-removido" data-id="${i.id}">Descartar</button>
      </td>`;
    tbodyRemovidos.appendChild(tr);
  });

  document.querySelector("[data-cy=carrinho-subtotal]").textContent = formatarMoeda(c.subtotal);
  el("btn-comprar").disabled = !c.podeComprar;
  agendarRevisaoDoCarrinho(c);
}

// Recarrega o carrinho quando a notificação de 5 minutos ou a expiração do bloqueio devem aparecer.
function agendarRevisaoDoCarrinho(c) {
  clearTimeout(loja.timerCarrinho);
  if (c.itens.length === 0) return;
  const espera = c.segundosRestantes > 300 ? c.segundosRestantes - 300 : c.segundosRestantes;
  loja.timerCarrinho = setTimeout(async () => {
    if (!loja.clienteId) return;
    await carregarCarrinho();
    await carregarCatalogo();
  }, espera * 1000 + 1000);
}

el("tbody-carrinho").addEventListener("click", async (ev) => {
  const btnAtualizar = ev.target.closest("button[data-cy=btn-atualizar-qtd]");
  const btnRemover = ev.target.closest("button[data-cy=btn-remover-item]");
  if (btnAtualizar) {
    const quantidade = parseInt(btnAtualizar.closest("tr").querySelector("[data-cy=carrinho-qtd]").value, 10);
    await executarNoCarrinho(() => CarrinhoApi.alterarQuantidade(loja.clienteId, btnAtualizar.dataset.id, quantidade),
      "Quantidade atualizada.");
  } else if (btnRemover) {
    await executarNoCarrinho(() => CarrinhoApi.remover(loja.clienteId, btnRemover.dataset.id), "Item removido do carrinho.");
  }
});

el("tbody-removidos").addEventListener("click", async (ev) => {
  const btnReadicionar = ev.target.closest("button[data-cy=btn-readicionar]");
  const btnDescartar = ev.target.closest("button[data-cy=btn-descartar-removido]");
  if (btnReadicionar) {
    await executarNoCarrinho(() => CarrinhoApi.adicionar(loja.clienteId, Number(btnReadicionar.dataset.jogoId),
      Number(btnReadicionar.dataset.quantidade)), "Item adicionado novamente ao carrinho.");
  } else if (btnDescartar) {
    await executarNoCarrinho(() => CarrinhoApi.remover(loja.clienteId, btnDescartar.dataset.id), "Item descartado.");
  }
});

// --------------------- checkout ---------------------
el("btn-comprar").addEventListener("click", abrirCheckout);
el("btn-cancelar-checkout").addEventListener("click", () => { el("checkout").hidden = true; });

async function abrirCheckout() {
  limparMensagem();
  try {
    const [enderecos, cartoes, cuponsTroca] = await Promise.all([
      EnderecoApi.listar(loja.clienteId), CartaoApi.listar(loja.clienteId), CupomApi.trocaDoCliente(loja.clienteId)]);
    loja.enderecos = enderecos.filter((e) => e.tipo !== "COBRANCA");
    loja.cartoes = cartoes;
    loja.cupons = [];
    loja.frete = null;

    el("checkout-endereco").innerHTML =
      loja.enderecos.map((e) => `<option value="${e.id}">${e.apelido} — ${e.logradouro}, ${e.numero} — ${e.cidade}/${e.estado}</option>`).join("") +
      '<option value="novo">Novo endereço de entrega…</option>';
    el("checkout-novo-endereco").querySelectorAll("input, select").forEach((campo) => {
      if (campo.type === "checkbox") campo.checked = false; else campo.value = "";
    });

    el("tbody-pagto-cartoes").innerHTML = cartoes.map((c) => `
      <tr data-cy="linha-cartao-pagamento" data-cartao-id="${c.id}">
        <td><input type="checkbox" data-cy="pagto-cartao-check"></td>
        <td>${c.numeroMascarado}${c.preferencial ? " (preferencial)" : ""}</td>
        <td>${c.bandeira}</td>
        <td><input type="text" class="input-valor" data-cy="pagto-cartao-valor"></td>
      </tr>`).join("");
    el("pagto-novo-check").checked = false;
    el("pagto-novo-cartao").hidden = true;
    ["pagto-novo-numero", "pagto-novo-nome", "pagto-novo-bandeira", "pagto-novo-cvv", "pagto-novo-valor"].forEach((id) => { el(id).value = ""; });
    el("pagto-novo-salvar").checked = false;
    el("checkout-cupom-codigo").value = "";

    el("cupons-troca-disponiveis").innerHTML = cuponsTroca.length === 0 ? "" :
      '<span class="hint">Seus cupons de troca:</span> ' + cuponsTroca.map((c) =>
        `<button type="button" class="btn-secondary btn-sm" data-cy="btn-usar-cupom-troca" data-codigo="${c.codigo}">${c.codigo} (${formatarMoeda(c.valor)})</button>`).join(" ");

    el("checkout").hidden = false;
    renderizarCupons();
    await selecionarEndereco();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("checkout-endereco").addEventListener("change", selecionarEndereco);
el("checkout-novo-endereco").querySelector("[data-cy=novo-end-estado]").addEventListener("input", (ev) => {
  if (ev.target.value.trim().length === 2) calcularFrete(ev.target.value.trim());
});

async function selecionarEndereco() {
  const valor = el("checkout-endereco").value;
  const novo = valor === "novo";
  el("checkout-novo-endereco").hidden = !novo;
  loja.frete = null;
  if (novo) {
    const uf = el("checkout-novo-endereco").querySelector("[data-cy=novo-end-estado]").value.trim();
    if (uf.length === 2) await calcularFrete(uf); else atualizarTotais();
    return;
  }
  const endereco = loja.enderecos.find((e) => String(e.id) === valor);
  if (endereco) await calcularFrete(endereco.estado); else atualizarTotais();
}

// RF0034
async function calcularFrete(uf) {
  try {
    const resp = await CarrinhoApi.frete(loja.clienteId, uf);
    loja.frete = resp.valorFrete;
  } catch (erro) {
    loja.frete = null;
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
  atualizarTotais();
}

function totaisDaCompra() {
  const subtotal = Number(loja.carrinho ? loja.carrinho.subtotal : 0);
  const total = loja.frete == null ? null : subtotal + Number(loja.frete);
  const somaCupons = loja.cupons.reduce((s, c) => s + Number(c.valor), 0);
  const valorCupons = total == null ? somaCupons : Math.min(somaCupons, total);
  const restante = total == null ? null : Math.max(0, total - valorCupons);
  return { total, valorCupons, restante };
}

function atualizarTotais() {
  const { total, valorCupons, restante } = totaisDaCompra();
  document.querySelector("[data-cy=checkout-frete]").textContent = loja.frete == null ? "—" : formatarMoeda(loja.frete);
  document.querySelector("[data-cy=checkout-total]").textContent = total == null ? "—" : formatarMoeda(total);
  document.querySelector("[data-cy=checkout-valor-cupons]").textContent = formatarMoeda(valorCupons);
  document.querySelector("[data-cy=checkout-restante]").textContent = restante == null ? "—" : formatarMoeda(restante);
}

// --------------------- cupons (RF0037 / RN0033) ---------------------
async function aplicarCupom(codigo) {
  limparMensagem();
  if (!codigo) return;
  try {
    loja.cupons = await CupomApi.validar(loja.clienteId, [...loja.cupons.map((c) => c.codigo), codigo]);
    el("checkout-cupom-codigo").value = "";
    renderizarCupons();
    mostrarMensagem(`Cupom ${codigo.toUpperCase()} aplicado.`, "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

el("btn-aplicar-cupom").addEventListener("click", () => aplicarCupom(el("checkout-cupom-codigo").value.trim()));
el("cupons-troca-disponiveis").addEventListener("click", (ev) => {
  const btn = ev.target.closest("button[data-cy=btn-usar-cupom-troca]");
  if (btn) aplicarCupom(btn.dataset.codigo);
});

function renderizarCupons() {
  el("lista-cupons").innerHTML = loja.cupons.map((c) => `
    <li data-cy="linha-cupom">${c.codigo} — ${c.tipo === "TROCA" ? "troca" : "promocional"} — ${formatarMoeda(c.valor)}
      <button type="button" class="btn-secondary btn-sm" data-cy="btn-remover-cupom" data-codigo="${c.codigo}">Remover</button>
    </li>`).join("");
  atualizarTotais();
}

el("lista-cupons").addEventListener("click", (ev) => {
  const btn = ev.target.closest("button[data-cy=btn-remover-cupom]");
  if (!btn) return;
  loja.cupons = loja.cupons.filter((c) => c.codigo !== btn.dataset.codigo);
  renderizarCupons();
});

// --------------------- cartões (RF0036) ---------------------
function somaOutrosCartoes(campoAtual) {
  const campos = [...document.querySelectorAll("[data-cy=pagto-cartao-valor]")];
  if (el("pagto-novo-check").checked) campos.push(el("pagto-novo-valor"));
  return campos.filter((c) => c !== campoAtual && c.value).reduce((s, c) => s + lerValor(c.value), 0);
}

// Ao marcar um cartão, sugere o valor que ainda falta pagar.
function sugerirValor(campo) {
  const { restante } = totaisDaCompra();
  if (restante == null || campo.value) return;
  const falta = Math.max(0, restante - somaOutrosCartoes(campo));
  if (falta > 0) campo.value = falta.toFixed(2).replace(".", ",");
}

el("tbody-pagto-cartoes").addEventListener("change", (ev) => {
  if (!ev.target.matches("[data-cy=pagto-cartao-check]")) return;
  const campo = ev.target.closest("tr").querySelector("[data-cy=pagto-cartao-valor]");
  if (ev.target.checked) sugerirValor(campo); else campo.value = "";
});

el("pagto-novo-check").addEventListener("change", (ev) => {
  el("pagto-novo-cartao").hidden = !ev.target.checked;
  if (ev.target.checked) sugerirValor(el("pagto-novo-valor")); else el("pagto-novo-valor").value = "";
});

// --------------------- finalizar (RF0038) ---------------------
function montarPedido() {
  const pedido = { cupons: loja.cupons.map((c) => c.codigo), cartoes: [] };

  if (el("checkout-endereco").value === "novo") {
    const novo = {};
    el("checkout-novo-endereco").querySelectorAll("input[name], select[name]").forEach((campo) => { novo[campo.name] = campo.value.trim(); });
    pedido.novoEndereco = novo;
    pedido.salvarEnderecoNoPerfil = el("novo-end-salvar").checked;
  } else {
    pedido.enderecoId = Number(el("checkout-endereco").value);
  }

  document.querySelectorAll("[data-cy=linha-cartao-pagamento]").forEach((tr) => {
    if (tr.querySelector("[data-cy=pagto-cartao-check]").checked) {
      pedido.cartoes.push({ cartaoId: Number(tr.dataset.cartaoId), valor: lerValor(tr.querySelector("[data-cy=pagto-cartao-valor]").value) });
    }
  });
  if (el("pagto-novo-check").checked) {
    pedido.cartoes.push({
      novoCartao: {
        numero: el("pagto-novo-numero").value.trim(),
        nomeImpresso: el("pagto-novo-nome").value.trim(),
        bandeira: el("pagto-novo-bandeira").value || null,
        codigoSeguranca: el("pagto-novo-cvv").value.trim(),
        preferencial: false,
      },
      salvarNoPerfil: el("pagto-novo-salvar").checked,
      valor: lerValor(el("pagto-novo-valor").value),
    });
  }
  return pedido;
}

el("btn-finalizar-compra").addEventListener("click", async () => {
  limparMensagem();
  try {
    const pedido = await PedidoApi.finalizar(loja.clienteId, montarPedido());
    el("checkout").hidden = true;
    await carregarCarrinho();
    await Promise.all([carregarCatalogo(), carregarMinhasCompras()]);
    const troco = pedido.cupomTrocoGerado
      ? ` Cupom de troca gerado com a diferença: ${pedido.cupomTrocoGerado} (${formatarMoeda(pedido.valorTroco)}).` : "";
    mostrarMensagem(`Compra ${pedido.codigo} finalizada com sucesso! Status: ${pedido.statusDescricao}.${troco}`, "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
    if (["RN0032", "RN0044", "RNF0042"].includes(erro.codigoRegra)) {
      el("checkout").hidden = true;
      await carregarCarrinho();
      await carregarCatalogo();
    }
  }
});

// --------------------- minhas compras ---------------------
async function carregarMinhasCompras() {
  try {
    const pedidos = await PedidoApi.doCliente(loja.clienteId);
    el("tbody-minhas-compras").innerHTML = pedidos.map((p) => `
      <tr data-cy="linha-minha-compra">
        <td class="codigo">${p.codigo}</td>
        <td class="data">${formatarData(p.dataCompra)}</td>
        <td class="preco">${formatarMoeda(p.total)}</td>
        <td data-cy="cupom-troco-gerado">${p.cupomTrocoGerado ? `${p.cupomTrocoGerado} (${formatarMoeda(p.valorTroco)})` : "—"}</td>
        <td>${statusPedidoBadge(p)}</td>
      </tr>`).join("");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}
