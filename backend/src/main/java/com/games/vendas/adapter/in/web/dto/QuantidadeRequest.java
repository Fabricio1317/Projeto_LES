package com.games.vendas.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** RF0032 — nova quantidade de um item já no carrinho. */
public record QuantidadeRequest(
        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser de pelo menos 1 item")
        Integer quantidade
) {}
