// Cliente HTTP simples para a API REST do módulo de Cliente (Nexus).
// Mesma origem do backend (servido pelo próprio Spring Boot) - sem necessidade de CORS.

const API_BASE = "/api/clientes";

async function apiRequest(path, options = {}) {
  const resp = await fetch(API_BASE + path, {
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

// RNF0012 — log de auditoria (fora do prefixo /api/clientes)
const AuditoriaApi = {
  listar: async () => {
    const resp = await fetch("/api/auditoria", { headers: { "Content-Type": "application/json" } });
    if (!resp.ok) throw new Error("Erro ao carregar auditoria.");
    return resp.json();
  },
};
