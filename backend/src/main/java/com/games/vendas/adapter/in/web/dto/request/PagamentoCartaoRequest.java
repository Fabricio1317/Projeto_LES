package com.games.vendas.adapter.in.web.dto.request;

import com.games.cliente.adapter.in.web.dto.request.CartaoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * RF0036 — um cartão usado no pagamento: um já cadastrado no perfil
 * ({@code cartaoId}) ou um novo ({@code novoCartao}, validado pela RN0024/RN0025),
 * que pode ser incorporado ao perfil ({@code salvarNoPerfil}).
 */
public record PagamentoCartaoRequest(
        Long cartaoId,

        @Valid
        CartaoRequest novoCartao,

        boolean salvarNoPerfil,

        @NotNull(message = "Valor do pagamento é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor do pagamento deve ser maior que zero")
        BigDecimal valor
) {}
