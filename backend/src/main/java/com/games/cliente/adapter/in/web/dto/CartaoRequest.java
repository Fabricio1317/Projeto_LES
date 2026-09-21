package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.Bandeira;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** RF0027 / RN0024 — todos os campos obrigatórios. */
public record CartaoRequest(
        @NotBlank(message = "Número do cartão é obrigatório")
        @Pattern(regexp = "\\d{13,19}", message = "Número do cartão deve ter entre 13 e 19 dígitos")
        String numero,

        @NotBlank(message = "Nome impresso no cartão é obrigatório")
        String nomeImpresso,

        @NotNull(message = "Bandeira é obrigatória")
        Bandeira bandeira,

        @NotBlank(message = "Código de segurança é obrigatório")
        @Pattern(regexp = "\\d{3,4}", message = "Código de segurança deve ter 3 ou 4 dígitos")
        String codigoSeguranca,

        boolean preferencial
) {}
