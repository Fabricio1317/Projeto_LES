package com.games.cliente.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** RF0028 — alteração isolada de senha, sem exigir os demais dados cadastrais. */
public record AlterarSenhaRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        String novaSenha,

        @NotBlank(message = "Confirmação da nova senha é obrigatória")
        String confirmacaoNovaSenha
) {}
