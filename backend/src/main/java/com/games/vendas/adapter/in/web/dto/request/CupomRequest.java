package com.games.vendas.adapter.in.web.dto.request;

import com.games.vendas.domain.enums.TipoCupom;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cadastro de cupom pelo administrador (promocional, ou de troca para um cliente). */
public record CupomRequest(
        @NotBlank(message = "Código do cupom é obrigatório")
        String codigo,

        @NotNull(message = "Tipo do cupom é obrigatório (PROMOCIONAL ou TROCA)")
        TipoCupom tipo,

        @NotNull(message = "Valor do cupom é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor do cupom deve ser maior que zero")
        BigDecimal valor,

        Long clienteId,

        LocalDate validade
) {}
