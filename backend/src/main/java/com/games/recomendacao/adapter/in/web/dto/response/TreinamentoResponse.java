package com.games.recomendacao.adapter.in.web.dto.response;

import com.games.recomendacao.domain.entity.TreinamentoModelo;

import java.time.LocalDateTime;

public record TreinamentoResponse(Long id, int versao, String nomeModelo, String motivo, String modeloBase, String status,
                                  String statusDescricao, int exemplosVendas, int exemplosFeedback, int feedbackDescartado,
                                  String datasetUri, String jobId, String modeloAjustado, String mensagemErro, String progresso,
                                  LocalDateTime iniciadoEm, LocalDateTime concluidoEm) {

    public static TreinamentoResponse from(TreinamentoModelo t) {
        return new TreinamentoResponse(t.getId(), t.getVersao(), t.getNomeModelo(), t.getMotivo(), t.getModeloBase(),
                t.getStatus().name(), t.getStatus().getDescricao(), t.getExemplosVendas(), t.getExemplosFeedback(),
                t.getFeedbackDescartado(), t.getDatasetUri(), t.getJobId(), t.getModeloAjustado(), t.getMensagemErro(), t.getProgresso(),
                t.getIniciadoEm(), t.getConcluidoEm());
    }
}
