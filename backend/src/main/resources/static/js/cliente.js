// Lógica de interface do módulo de Gestão de Clientes (Nexus).

let ultimaListaClientes = [];

// --------------------- ícones (inline SVG, estilo consistente) ---------------------
const ICONS = {
  editar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg>',
  endereco: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>',
  cartao: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>',
  senha: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>',
  inativar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="4.9" y1="4.9" x2="19.1" y2="19.1"/></svg>',
  reativar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>',
  remover: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
  estrela: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
};

function iconBtn({ icon, titulo, dataCy, id, variant = "", extraClass = "" }) {
  return `<button type="button" class="icon-btn ${variant} ${extraClass}" title="${titulo}" aria-label="${titulo}" data-cy="${dataCy}" data-id="${id}">${ICONS[icon]}</button>`;
}

// --------------------- mensagens ---------------------
function mostrarMensagem(texto, tipo) {
  const el = document.getElementById("mensagem");
  el.textContent = texto;
  el.className = `mensagem ${tipo}`;
  el.hidden = false;
  el.scrollIntoView({ behavior: "smooth", block: "start" });
}
function limparMensagem() {
  const el = document.getElementById("mensagem");
  el.hidden = true;
  el.textContent = "";
}
function mensagemDeErro(erro) {
  const prefixo = erro.codigoRegra ? `[${erro.codigoRegra}] ` : "";
  const detalhes = erro.detalhes && erro.detalhes.length ? " — " + erro.detalhes.join("; ") : "";
  return prefixo + erro.message + detalhes;
}

// --------------------- abas ---------------------
document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tab-btn").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".tab-content").forEach((s) => (s.hidden = true));
    btn.classList.add("active");
    document.getElementById(btn.dataset.tab).hidden = false;
    if (btn.dataset.tab === "tab-consulta") carregarClientes();
    if (btn.dataset.tab === "tab-auditoria") carregarAuditoria();
  });
});

// --------------------- cadastro (RF0021) ---------------------
document.getElementById("form-cadastro").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  limparMensagem();
  const form = ev.target;
  const dados = Object.fromEntries(new FormData(form).entries());

  try {
    await ClienteApi.cadastrar(dados);
    mostrarMensagem("Cliente cadastrado com sucesso!", "sucesso");
    form.reset();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- consulta (RF0024) ---------------------
document.getElementById("form-filtros").addEventListener("submit", (ev) => {
  ev.preventDefault();
  carregarClientes();
});

async function carregarClientes() {
  limparMensagem();
  const form = document.getElementById("form-filtros");
  const filtros = Object.fromEntries(new FormData(form).entries());

  try {
    ultimaListaClientes = await ClienteApi.consultar(filtros);
    renderizarTabela(ultimaListaClientes);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

function renderizarTabela(clientes) {
  const tbody = document.getElementById("tbody-clientes");
  tbody.innerHTML = "";

  clientes.forEach((c) => {
    const tr = document.createElement("tr");
    tr.dataset.cy = "linha-cliente";
    tr.dataset.id = c.id;
    if (c.status === "INATIVO") tr.classList.add("inativo");

    const acaoStatus = c.status === "ATIVO"
      ? iconBtn({ icon: "inativar", titulo: "Inativar", dataCy: "btn-inativar", id: c.id, variant: "icon-btn--danger" })
      : iconBtn({ icon: "reativar", titulo: "Reativar", dataCy: "btn-reativar", id: c.id, variant: "icon-btn--success" });

    tr.innerHTML = `
      <td>${c.codigoCliente}</td>
      <td>${c.nome}</td>
      <td>${formatarCpf(c.cpf)}</td>
      <td>${c.email}</td>
      <td>${c.ranking}</td>
      <td><span class="status-badge ${c.status}" data-cy="status-cliente">${c.status}</span></td>
      <td class="acoes">
        ${iconBtn({ icon: "editar", titulo: "Editar dados", dataCy: "btn-editar", id: c.id })}
        ${iconBtn({ icon: "endereco", titulo: "Endereços", dataCy: "btn-enderecos", id: c.id })}
        ${iconBtn({ icon: "cartao", titulo: "Cartões", dataCy: "btn-cartoes", id: c.id })}
        ${iconBtn({ icon: "senha", titulo: "Alterar senha", dataCy: "btn-senha", id: c.id })}
        ${acaoStatus}
      </td>`;
    tbody.appendChild(tr);
  });
}

function formatarCpf(cpf) {
  return cpf.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, "$1.$2.$3-$4");
}

// delegação de eventos para os botões da tabela (gerados dinamicamente)
document.getElementById("tbody-clientes").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button");
  if (!btn) return;
  const id = btn.dataset.id;
  limparMensagem();

  if (btn.dataset.cy === "btn-editar") {
    abrirModalAlterar(id);
  } else if (btn.dataset.cy === "btn-enderecos") {
    abrirModalEnderecos(id);
  } else if (btn.dataset.cy === "btn-cartoes") {
    abrirModalCartoes(id);
  } else if (btn.dataset.cy === "btn-senha") {
    abrirModalSenha(id);
  } else if (btn.dataset.cy === "btn-inativar") {
    if (!confirm("Confirma a inativação deste cliente? O cadastro não será excluído, apenas marcado como inativo.")) return;
    try {
      await ClienteApi.inativar(id);
      mostrarMensagem("Cliente inativado com sucesso.", "sucesso");
      carregarClientes();
    } catch (erro) {
      mostrarMensagem(mensagemDeErro(erro), "erro");
    }
  } else if (btn.dataset.cy === "btn-reativar") {
    try {
      await ClienteApi.reativar(id);
      mostrarMensagem("Cliente reativado com sucesso.", "sucesso");
      carregarClientes();
    } catch (erro) {
      mostrarMensagem(mensagemDeErro(erro), "erro");
    }
  }
});

// --------------------- alterar (RF0022) ---------------------
function abrirModalAlterar(id) {
  const cliente = ultimaListaClientes.find((c) => String(c.id) === String(id));
  if (!cliente) return;

  const form = document.getElementById("form-alterar");
  form.elements["id"].value = cliente.id;
  form.elements["nome"].value = cliente.nome;
  form.elements["telefoneTipo"].value = cliente.telefoneTipo || "";
  form.elements["telefoneDdd"].value = cliente.telefoneDdd || "";
  form.elements["telefoneNumero"].value = cliente.telefoneNumero || "";
  form.elements["email"].value = cliente.email;

  // NOVOS CAMPOS ADICIONADOS AQUI
  form.elements["enderecoTipoResidencia"].value = cliente.enderecoTipoResidencia || "Casa";
  form.elements["enderecoTipoLogradouro"].value = cliente.enderecoTipoLogradouro || "Rua";
  form.elements["pais"].value = cliente.pais || "";

  form.elements["enderecoLogradouro"].value = cliente.enderecoLogradouro;
  form.elements["enderecoNumero"].value = cliente.enderecoNumero;
  form.elements["enderecoBairro"].value = cliente.enderecoBairro;
  form.elements["enderecoCep"].value = cliente.enderecoCep;
  form.elements["enderecoCidade"].value = cliente.enderecoCidade;
  form.elements["enderecoEstado"].value = cliente.enderecoEstado;

  document.getElementById("modal-alterar").hidden = false;
}

document.getElementById("btn-cancelar-alterar").addEventListener("click", () => {
  document.getElementById("modal-alterar").hidden = true;
});

document.getElementById("form-alterar").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const form = ev.target;
  const { id, ...dados } = Object.fromEntries(new FormData(form).entries());

  try {
    await ClienteApi.alterar(id, dados);
    document.getElementById("modal-alterar").hidden = true;
    mostrarMensagem("Dados do cliente atualizados com sucesso.", "sucesso");
    carregarClientes();
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- endereços (RF0026) ---------------------
async function abrirModalEnderecos(clienteId) {
  document.getElementById("enderecos-cliente-id").value = clienteId;
  document.getElementById("form-endereco").reset();
  document.getElementById("modal-enderecos").hidden = false;
  await recarregarEnderecos(clienteId);
}

async function recarregarEnderecos(clienteId) {
  try {
    const enderecos = await EnderecoApi.listar(clienteId);
    const tbody = document.getElementById("tbody-enderecos");
    tbody.innerHTML = "";
    enderecos.forEach((e) => {
      const tr = document.createElement("tr");
      tr.dataset.cy = "linha-endereco";
      tr.innerHTML = `
        <td>${e.apelido}</td>
        <td>${e.tipo}</td>
        <td>${e.logradouro}, ${e.numero} — ${e.bairro}, ${e.cidade}/${e.estado}</td>
        <td class="acoes">${iconBtn({ icon: "remover", titulo: "Remover endereço", dataCy: "btn-remover-endereco", id: e.id, variant: "icon-btn--danger" })}</td>`;
      tbody.appendChild(tr);
    });
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

document.getElementById("btn-fechar-enderecos").addEventListener("click", () => {
  document.getElementById("modal-enderecos").hidden = true;
});

document.getElementById("form-endereco").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const clienteId = document.getElementById("enderecos-cliente-id").value;
  const dados = Object.fromEntries(new FormData(ev.target).entries());
  try {
    await EnderecoApi.cadastrar(clienteId, dados);
    mostrarMensagem("Endereço adicionado com sucesso.", "sucesso");
    ev.target.reset();
    await recarregarEnderecos(clienteId);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

document.getElementById("tbody-enderecos").addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-cy=btn-remover-endereco]");
  if (!btn) return;
  const clienteId = document.getElementById("enderecos-cliente-id").value;
  try {
    await EnderecoApi.remover(clienteId, btn.dataset.id);
    mostrarMensagem("Endereço removido com sucesso.", "sucesso");
    await recarregarEnderecos(clienteId);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- cartões (RF0027) ---------------------
async function abrirModalCartoes(clienteId) {
  document.getElementById("cartoes-cliente-id").value = clienteId;
  document.getElementById("form-cartao").reset();
  document.getElementById("modal-cartoes").hidden = false;
  await recarregarCartoes(clienteId);
}

async function recarregarCartoes(clienteId) {
  try {
    const cartoes = await CartaoApi.listar(clienteId);
    const tbody = document.getElementById("tbody-cartoes");
    tbody.innerHTML = "";
    cartoes.forEach((c) => {
      const tr = document.createElement("tr");
      tr.dataset.cy = "linha-cartao";
      const estrelaBtn = iconBtn({
        icon: "estrela",
        titulo: c.preferencial ? "Cartão preferencial" : "Tornar preferencial",
        dataCy: c.preferencial ? "cartao-preferencial-badge" : "btn-marcar-preferencial",
        id: c.id,
        variant: "icon-btn--star",
        extraClass: c.preferencial ? "is-active" : "",
      });
      tr.innerHTML = `
        <td>${c.numeroMascarado}</td>
        <td>${c.nomeImpresso}</td>
        <td>${c.bandeira}</td>
        <td>${estrelaBtn}</td>
        <td class="acoes">${iconBtn({ icon: "remover", titulo: "Remover cartão", dataCy: "btn-remover-cartao", id: c.id, variant: "icon-btn--danger" })}</td>`;
      tbody.appendChild(tr);
    });
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

document.getElementById("btn-fechar-cartoes").addEventListener("click", () => {
  document.getElementById("modal-cartoes").hidden = true;
});

document.getElementById("form-cartao").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const clienteId = document.getElementById("cartoes-cliente-id").value;
  const form = ev.target;
  const dados = Object.fromEntries(new FormData(form).entries());
  dados.preferencial = form.elements["preferencial"].checked;
  try {
    await CartaoApi.cadastrar(clienteId, dados);
    mostrarMensagem("Cartão adicionado com sucesso.", "sucesso");
    form.reset();
    await recarregarCartoes(clienteId);
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

document.getElementById("tbody-cartoes").addEventListener("click", async (ev) => {
  const clienteId = document.getElementById("cartoes-cliente-id").value;
  const btnRemover = ev.target.closest("button[data-cy=btn-remover-cartao]");
  const btnPreferencial = ev.target.closest("button[data-cy=btn-marcar-preferencial]");

  try {
    if (btnRemover) {
      await CartaoApi.remover(clienteId, btnRemover.dataset.id);
      mostrarMensagem("Cartão removido com sucesso.", "sucesso");
      await recarregarCartoes(clienteId);
    } else if (btnPreferencial) {
      await CartaoApi.marcarPreferencial(clienteId, btnPreferencial.dataset.id);
      mostrarMensagem("Cartão definido como preferencial.", "sucesso");
      await recarregarCartoes(clienteId);
    }
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- senha (RF0028) ---------------------
function abrirModalSenha(clienteId) {
  const form = document.getElementById("form-senha");
  form.reset();
  form.elements["id"].value = clienteId;
  document.getElementById("modal-senha").hidden = false;
}

document.getElementById("btn-cancelar-senha").addEventListener("click", () => {
  document.getElementById("modal-senha").hidden = true;
});

document.getElementById("form-senha").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const form = ev.target;
  const { id, ...dados } = Object.fromEntries(new FormData(form).entries());
  try {
    await ClienteApi.alterarSenha(id, dados);
    document.getElementById("modal-senha").hidden = true;
    mostrarMensagem("Senha alterada com sucesso.", "sucesso");
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- auditoria (RNF0012) ---------------------
async function carregarAuditoria() {
  limparMensagem();
  try {
    const logs = await AuditoriaApi.listar();
    const tbody = document.getElementById("tbody-auditoria");
    tbody.innerHTML = "";
    logs.forEach((l) => {
      const tr = document.createElement("tr");
      tr.dataset.cy = "linha-auditoria";
      tr.innerHTML = `
        <td>${new Date(l.dataHora).toLocaleString("pt-BR")}</td>
        <td>${l.entidade} #${l.entidadeId}</td>
        <td>${l.operacao}</td>
        <td>${l.usuario}</td>
        <td>${l.dadoAlterado}</td>`;
      tbody.appendChild(tr);
    });
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}
