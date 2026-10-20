package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/**
 * RF0040 / RN0041 — o cliente troca itens selecionados ({@code itens}) ou a
 * compra inteira ({@code compraInteira = true}, quando {@code itens} é ignorado).
 */
public record SolicitarTrocaRequest(
        boolean compraInteira,

        List<@Valid ItemTrocaRequest> itens
) {}
