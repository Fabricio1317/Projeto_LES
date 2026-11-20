package com.games.recomendacao.adapter.in.web.dto.response;

import com.games.recomendacao.domain.entity.MensagemAssistente;

import java.time.LocalDateTime;

public record MensagemAssistenteResponse(Long id, Long clienteId, String clienteNome, String conversaId, String pergunta,
                                         String resposta, String modelo, LocalDateTime dataHora, String avaliacao,
                                         String avaliacaoDescricao, LocalDateTime dataAvaliacao) {

    public static MensagemAssistenteResponse from(MensagemAssistente m) {
        return from(m, null);
    }

    public static MensagemAssistenteResponse from(MensagemAssistente m, String clienteNome) {
        return new MensagemAssistenteResponse(m.getId(), m.getClienteId(), clienteNome, m.getConversaId(), m.getPergunta(),
                m.getResposta(), m.getModelo(), m.getDataHora(),
                m.getAvaliacao() == null ? null : m.getAvaliacao().name(),
                m.getAvaliacao() == null ? null : m.getAvaliacao().getDescricao(), m.getDataAvaliacao());
    }
}
