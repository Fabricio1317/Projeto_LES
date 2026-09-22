package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.entity.LogTransacao;
import java.time.LocalDateTime;

public record LogTransacaoResponse(
        Long id, String entidade, Long entidadeId, String operacao,
        String usuario, LocalDateTime dataHora, String dadoAlterado
) {
    public static LogTransacaoResponse from(LogTransacao l) {
        return new LogTransacaoResponse(l.getId(), l.getEntidade(), l.getEntidadeId(),
                l.getOperacao(), l.getUsuario(), l.getDataHora(), l.getDadoAlterado());
    }
}
