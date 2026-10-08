package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/**
 * RF0033 / RF0038 — dados para finalizar a compra a partir do carrinho:
 * endereço de entrega (RF0035), cupons (RF0037) e cartões (RF0036).
 */
public record FinalizarCompraRequest(
        Long enderecoId,

        @Valid
        NovoEnderecoEntregaRequest novoEndereco,

        boolean salvarEnderecoNoPerfil,

        List<String> cupons,

        List<@Valid PagamentoCartaoRequest> cartoes
) {}
