package com.games.vendas.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** RF0031 / RF0032 — jogo e quantidade escolhidos ao adicionar no carrinho. */
public record ItemCarrinhoRequest(
        @NotNull(message = "Jogo é obrigatório")
        Long jogoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser de pelo menos 1 item")
        Integer quantidade
) {}
