package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Item da compra escolhido para troca e a quantidade a trocar. */
public record ItemTrocaRequest(
        @NotNull(message = "Item da compra é obrigatório")
        Long pedidoItemId,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade a trocar deve ser de pelo menos 1 item")
        Integer quantidade
) {}
