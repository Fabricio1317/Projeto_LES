package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ParametroRequest(
        @NotBlank(message = "Valor do parâmetro é obrigatório")
        String valor
) {}
