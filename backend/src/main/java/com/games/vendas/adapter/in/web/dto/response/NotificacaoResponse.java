package com.games.vendas.adapter.in.web.dto.response;

import com.games.vendas.domain.entity.Notificacao;

import java.time.LocalDateTime;

public record NotificacaoResponse(Long id, String mensagem, LocalDateTime dataHora, boolean lida) {
    public static NotificacaoResponse from(Notificacao n) {
        return new NotificacaoResponse(n.getId(), n.getMensagem(), n.getDataHora(), n.isLida());
    }
}
