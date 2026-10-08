package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** RF0034 — UF do endereço de entrega usada no cálculo do frete. */
public record FreteRequest(
        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve ser a sigla com 2 letras")
        String estado
) {}
