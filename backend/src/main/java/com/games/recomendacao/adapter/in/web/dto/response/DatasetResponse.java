package com.games.recomendacao.adapter.in.web.dto.response;

import com.games.recomendacao.application.service.DatasetTreinamentoService.DatasetTreinamento;

import java.util.Arrays;
import java.util.List;

/** Resumo do dataset de ajuste fino que seria enviado agora, com as primeiras linhas como amostra. */
public record DatasetResponse(int exemplosVendas, int exemplosFeedback, int feedbackDescartado, int total, int clientes,
                              long vendasConsideradas, long feedbacksConsiderados, List<String> amostra) {

    public static DatasetResponse from(DatasetTreinamento d) {
        List<String> linhas = Arrays.stream(d.jsonl().split("\n")).filter(l -> !l.isBlank()).limit(2).toList();
        return new DatasetResponse(d.exemplosVendas(), d.exemplosFeedback(), d.feedbackDescartado(), d.total(), d.clientes(),
                d.vendasConsideradas(), d.feedbacksConsiderados(), linhas);
    }
}
