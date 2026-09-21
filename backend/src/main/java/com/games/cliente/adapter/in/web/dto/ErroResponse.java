package com.games.cliente.adapter.in.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato padrão de erro da API. O campo {@code codigoRegra} permite que os
 * testes automatizados verifiquem explicitamente qual RF/RN/RNF motivou o erro.
 */
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String codigoRegra,
        String mensagem,
        List<String> detalhes
) {
    public static ErroResponse of(int status, String codigoRegra, String mensagem) {
        return new ErroResponse(LocalDateTime.now(), status, codigoRegra, mensagem, List.of());
    }

    public static ErroResponse of(int status, String codigoRegra, String mensagem, List<String> detalhes) {
        return new ErroResponse(LocalDateTime.now(), status, codigoRegra, mensagem, detalhes);
    }
}
