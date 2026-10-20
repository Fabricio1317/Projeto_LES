package com.games.vendas.adapter.in.web.dto.request;

/** RF0043 — ao confirmar o recebimento, o administrador informa se os itens voltam ao estoque (RF0054). */
public record ConfirmarRecebimentoRequest(boolean retornarEstoque) {}
