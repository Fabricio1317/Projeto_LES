// Cliente HTTP simples para a API REST do módulo de Cliente (Nexus).
// Mesma origem do backend (servido pelo próprio Spring Boot) - sem necessidade de CORS.

const API_BASE = "/api/clientes";

function apiRequest(path, options = {}) {
  return apiRequestUrl(API_BASE + path, options);
}

async function apiRequestUrl(url, options = {}) {
  const resp = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  if (resp.status === 204) return null;

  const data = await resp.json().catch(() => null);

  if (!resp.ok) {
    const erro = new Error((data && data.mensagem) || "Erro inesperado.");
    erro.codigoRegra = data && data.codigoRegra;
    erro.detalhes = (data && data.detalhes) || [];
    erro.status = resp.status;
    throw erro;
  }
  return data;
}

const ClienteApi = {
  cadastrar: (payload) => apiRequest("", { method: "POST", body: JSON.stringify(payload) }),
  consultar: (filtros) => {
    const params = new URLSearchParams();
    Object.entries(filtros || {}).forEach(([k, v]) => { if (v) params.set(k, v); });
    const qs = params.toString();
    return apiRequest(qs ? `?${qs}` : "");
  },
  buscarPorId: (id) => apiRequest(`/${id}`),
  alterar: (id, payload) => apiRequest(`/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
  alterarSenha: (id, payload) => apiRequest(`/${id}/senha`, { method: "PATCH", body: JSON.stringify(payload) }),
  inativar: (id) => apiRequest(`/${id}/inativar`, { method: "PATCH" }),
  reativar: (id) => apiRequest(`/${id}/reativar`, { method: "PATCH" }),
};

// RF0026 — endereços do cliente
const EnderecoApi = {
  listar: (clienteId) => apiRequest(`/${clienteId}/enderecos`),
  cadastrar: (clienteId, payload) => apiRequest(`/${clienteId}/enderecos`, { method: "POST", body: JSON.stringify(payload) }),
  remover: (clienteId, enderecoId) => apiRequest(`/${clienteId}/enderecos/${enderecoId}`, { method: "DELETE" }),
};

// RF0027 — cartões do cliente
const CartaoApi = {
  listar: (clienteId) => apiRequest(`/${clienteId}/cartoes`),
  cadastrar: (clienteId, payload) => apiRequest(`/${clienteId}/cartoes`, { method: "POST", body: JSON.stringify(payload) }),
  remover: (clienteId, cartaoId) => apiRequest(`/${clienteId}/cartoes/${cartaoId}`, { method: "DELETE" }),
  marcarPreferencial: (clienteId, cartaoId) => apiRequest(`/${clienteId}/cartoes/${cartaoId}/preferencial`, { method: "PATCH" }),
};

// ===================== Vendas =====================
const json = (method, body) => ({ method, body: JSON.stringify(body) });

const JogoApi = {
  catalogo: (clienteId) => apiRequestUrl(`/api/jogos${clienteId ? `?clienteId=${clienteId}` : ""}`),
  estoque: () => apiRequestUrl("/api/jogos/estoque"),
  cadastrar: (payload) => apiRequestUrl("/api/jogos", json("POST", payload)),
  ajustarEstoque: (id, estoque) => apiRequestUrl(`/api/jogos/${id}/estoque`, json("PATCH", { estoque })),
};

// RF0031 / RF0032 / RF0034
const CarrinhoApi = {
  obter: (clienteId) => apiRequest(`/${clienteId}/carrinho`),
  adicionar: (clienteId, jogoId, quantidade) => apiRequest(`/${clienteId}/carrinho/itens`, json("POST", { jogoId, quantidade })),
  alterarQuantidade: (clienteId, itemId, quantidade) => apiRequest(`/${clienteId}/carrinho/itens/${itemId}`, json("PUT", { quantidade })),
  remover: (clienteId, itemId) => apiRequest(`/${clienteId}/carrinho/itens/${itemId}`, { method: "DELETE" }),
  frete: (clienteId, estado) => apiRequest(`/${clienteId}/carrinho/frete`, json("POST", { estado })),
};

// RF0033 / RF0038 / RF0025 / RF0039 / RF0040
const PedidoApi = {
  finalizar: (clienteId, payload) => apiRequest(`/${clienteId}/pedidos`, json("POST", payload)),
  doCliente: (clienteId) => apiRequest(`/${clienteId}/pedidos`),
  listar: (status) => apiRequestUrl(`/api/pedidos${status ? `?status=${status}` : ""}`),
  validarPagamento: (id) => apiRequestUrl(`/api/pedidos/${id}/validar-pagamento`, { method: "PATCH" }),
  despachar: (id) => apiRequestUrl(`/api/pedidos/${id}/despachar`, { method: "PATCH" }),
  confirmarEntrega: (id) => apiRequestUrl(`/api/pedidos/${id}/confirmar-entrega`, { method: "PATCH" }),
};

// RF0036 / RF0037
const CupomApi = {
  listar: () => apiRequestUrl("/api/cupons"),
  cadastrar: (payload) => apiRequestUrl("/api/cupons", json("POST", payload)),
  trocaDoCliente: (clienteId) => apiRequest(`/${clienteId}/cupons`),
  validar: (clienteId, codigos) => apiRequest(`/${clienteId}/cupons/validar`, json("POST", codigos)),
};

// RN0044 — prazo de bloqueio do carrinho
const ParametroApi = {
  listar: () => apiRequestUrl("/api/parametros"),
  alterar: (chave, valor) => apiRequestUrl(`/api/parametros/${chave}`, json("PUT", { valor })),
};

// RNF0012 — log de auditoria (fora do prefixo /api/clientes)
const AuditoriaApi = {
  listar: async () => {
    const resp = await fetch("/api/auditoria", { headers: { "Content-Type": "application/json" } });
    if (!resp.ok) throw new Error("Erro ao carregar auditoria.");
    return resp.json();
  },
};
