// Helpers compartilhados pelas suítes de vendas. As pré-condições (cliente,
// endereço, cartões, jogos com estoque e cupons) são criadas pelas funcionalidades
// já implementadas, via API; as ações e verificações do fluxo são feitas pela interface.

const { gerarCpfValido, formatarCpf } = require("./cpf-generator");

const API = "/api";
const PRAZO_PADRAO = "1800";
const CARTAO_VISA = { numero: "4111111111111111", nomeImpresso: "CLIENTE TESTE", bandeira: "VISA", codigoSeguranca: "123" };
const CARTAO_MASTER = { numero: "5555555555554444", nomeImpresso: "CLIENTE TESTE", bandeira: "MASTERCARD", codigoSeguranca: "321" };
const CARTAO_RECUSADO = { numero: "4000000000000000", nomeImpresso: "CLIENTE TESTE", bandeira: "VISA", codigoSeguranca: "999" };

let sequencia = 0;
function unico() {
  sequencia += 1;
  return `${Date.now()}${sequencia}`;
}

// --------------------- pré-condições via API ---------------------
/** Cliente com o endereço "Residencial" (AMBOS, em SP) criado no cadastro. */
function criarCliente(nome = `Comprador ${unico()}`) {
  const cpf = gerarCpfValido();
  return cy.request("POST", `${API}/clientes`, {
    genero: "FEMININO", nome, dataNascimento: "1992-03-15", cpf: formatarCpf(cpf),
    telefoneTipo: "Celular", telefoneDdd: "11", telefoneNumero: "988887777",
    email: `comprador.${cpf}@example.com`, senha: "Senha@Forte1", confirmacaoSenha: "Senha@Forte1",
    enderecoTipoResidencia: "Casa", enderecoTipoLogradouro: "Rua", enderecoLogradouro: "Rua das Flores",
    enderecoNumero: "100", enderecoBairro: "Centro", enderecoCep: "01310100", enderecoCidade: "São Paulo",
    enderecoEstado: "SP", pais: "Brasil",
  }).its("body");
}

function criarCartao(clienteId, cartao = CARTAO_VISA) {
  return cy.request("POST", `${API}/clientes/${clienteId}/cartoes`, { ...cartao, preferencial: false }).its("body");
}

/** Edição física de R$ 50,00 e 0,5 kg: 2 unidades + frete SP (R$ 10,00 + 1 kg × R$ 5,00) = R$ 115,00. */
function criarJogo({ preco = 50, estoque = 10, pesoKg = 0.5 } = {}) {
  return cy.request("POST", `${API}/jogos`, {
    titulo: `Jogo [${unico()}]`, plataforma: "PlayStation 5", genero: "Ação", desenvolvedora: "Estúdio Teste",
    distribuidora: "Distribuidora Teste", classificacaoIndicativa: "12", ano: 2024, codigoBarras: "7890000000000",
    preco, estoque, pesoKg,
  }).its("body");
}

function criarCupomTroca(clienteId, valor) {
  return cy.request("POST", `${API}/cupons`, { codigo: `TROCA-T${unico()}`, tipo: "TROCA", valor, clienteId }).its("body");
}

function definirPrazoCarrinho(segundos) {
  cy.request("PUT", `${API}/parametros/PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS`, { valor: String(segundos) });
}

function ajustarEstoqueViaApi(jogoId, estoque) {
  cy.request("PATCH", `${API}/jogos/${jogoId}/estoque`, { estoque });
}

// --------------------- ações pela interface ---------------------
function abrirLoja(clienteId) {
  cy.visit("/index.html");
  cy.get("[data-cy=nav-loja]").click();
  cy.get(`[data-cy=loja-cliente] option[value="${clienteId}"]`).should("exist");
  cy.get("[data-cy=loja-cliente]").select(String(clienteId));
  cy.get("[data-cy=loja-conteudo]").should("be.visible");
  catalogoPronto();
}

function linhaJogo(titulo) {
  return cy.contains("[data-cy=linha-jogo]", titulo);
}

/**
 * RF0032 — a quantidade é definida no momento da adição. Depois da adição a tela
 * recarrega o catálogo; o helper espera essa recarga para que a próxima ação não
 * interaja com uma linha que está sendo substituída.
 */
function adicionarAoCarrinho(titulo, quantidade) {
  catalogoPronto();
  linhaJogo(titulo).find("[data-cy=qtd-jogo]").clear().type(String(quantidade));
  cy.intercept("GET", "/api/jogos*").as("catalogo");
  linhaJogo(titulo).find("[data-cy=btn-adicionar-carrinho]").click();
  cy.wait("@catalogo");
  catalogoPronto();
}

/** Espera o catálogo terminar de ser redesenhado (a tabela marca data-pronto="true"). */
function catalogoPronto() {
  cy.get("[data-cy=tbody-catalogo]").should("have.attr", "data-pronto", "true");
}

function linhaCarrinho(titulo) {
  return cy.contains("[data-cy=linha-carrinho]", titulo);
}

/** RF0033 — inicia a compra a partir do carrinho. */
function abrirCheckout() {
  cy.get("[data-cy=btn-comprar]").should("not.be.disabled").click();
  cy.get("[data-cy=checkout]").should("be.visible");
  cy.get("[data-cy=checkout-frete]").should("not.have.text", "—");
}

function pagarComPrimeiroCartaoCadastrado() {
  cy.get("[data-cy=pagto-cartao-check]").first().check();
}

function preencherNovoEndereco({ apelido = "Casa da Praia", estado = "BA" } = {}) {
  cy.get("[data-cy=checkout-endereco]").select("novo");
  cy.get("[data-cy=novo-end-apelido]").type(apelido);
  cy.get("[data-cy=novo-end-tipo-residencia]").select("Casa");
  cy.get("[data-cy=novo-end-tipo-logradouro]").select("Avenida");
  cy.get("[data-cy=novo-end-logradouro]").type("Oceânica");
  cy.get("[data-cy=novo-end-numero]").type("500");
  cy.get("[data-cy=novo-end-bairro]").type("Barra");
  cy.get("[data-cy=novo-end-cep]").type("40140130");
  cy.get("[data-cy=novo-end-cidade]").type("Salvador");
  cy.get("[data-cy=novo-end-estado]").type(estado);
  cy.get("[data-cy=novo-end-pais]").type("Brasil");
}

function preencherNovoCartao(cartao) {
  cy.get("[data-cy=pagto-novo-check]").check();
  if (cartao.numero) cy.get("[data-cy=pagto-novo-numero]").type(cartao.numero);
  if (cartao.nomeImpresso) cy.get("[data-cy=pagto-novo-nome]").type(cartao.nomeImpresso);
  if (cartao.bandeira) cy.get("[data-cy=pagto-novo-bandeira]").select(cartao.bandeira);
  if (cartao.codigoSeguranca) cy.get("[data-cy=pagto-novo-cvv]").type(cartao.codigoSeguranca);
}

function aplicarCupomPorCodigo(codigo) {
  cy.get("[data-cy=checkout-cupom-codigo]").clear().type(codigo);
  cy.get("[data-cy=btn-aplicar-cupom]").click();
}

function usarCupomDeTroca(codigo) {
  cy.contains("[data-cy=btn-usar-cupom-troca]", codigo).click();
  cy.contains("[data-cy=linha-cupom]", codigo).should("exist");
}

/** RF0038 — clica em "Finalizar compra" e devolve o código do pedido exibido na mensagem de sucesso. */
function finalizarCompra() {
  cy.get("[data-cy=btn-finalizar-compra]").click();
  return cy.get("[data-cy=mensagem]")
    .should("contain.text", "finalizada com sucesso")
    .invoke("text")
    .then((texto) => texto.match(/PED-\d{4}-[0-9A-F]{8}/)[0]);
}

/** Compra completa pela interface usando o endereço e o cartão já cadastrados. */
function comprarViaInterface(clienteId, titulo, quantidade) {
  abrirLoja(clienteId);
  adicionarAoCarrinho(titulo, quantidade);
  linhaCarrinho(titulo).should("exist");
  abrirCheckout();
  pagarComPrimeiroCartaoCadastrado();
  return finalizarCompra();
}

function abrirGestaoVendas() {
  cy.get("[data-cy=nav-vendas]").click();
  cy.get("[data-cy=tabela-pedidos]").should("be.visible");
}

function linhaPedido(codigo) {
  return cy.get(`[data-cy=linha-pedido][data-codigo="${codigo}"]`);
}

function executarAcaoNoPedido(codigo, acao, statusEsperado) {
  linhaPedido(codigo).find(`[data-cy=${acao}]`).click();
  linhaPedido(codigo).find("[data-cy=status-pedido]").should("have.text", statusEsperado);
}

function linhaEstoque(titulo) {
  return cy.contains("[data-cy=linha-estoque]", titulo);
}

function abrirConsultaDoCliente(nome) {
  cy.get("[data-cy=nav-consulta]").click();
  cy.get("[data-cy=filtro-nome]").clear().type(nome);
  cy.get("[data-cy=btn-filtrar]").click();
  cy.contains("[data-cy=linha-cliente]", nome).should("exist");
}

module.exports = {
  API, PRAZO_PADRAO, CARTAO_VISA, CARTAO_MASTER, CARTAO_RECUSADO, unico,
  criarCliente, criarCartao, criarJogo, criarCupomTroca, definirPrazoCarrinho, ajustarEstoqueViaApi,
  abrirLoja, linhaJogo, adicionarAoCarrinho, linhaCarrinho, abrirCheckout, pagarComPrimeiroCartaoCadastrado,
  preencherNovoEndereco, preencherNovoCartao, aplicarCupomPorCodigo, usarCupomDeTroca, finalizarCompra,
  comprarViaInterface, abrirGestaoVendas, linhaPedido, executarAcaoNoPedido, linhaEstoque, abrirConsultaDoCliente,
};
