package com.games.recomendacao.adapter.in.web.dto.request;

import com.games.recomendacao.domain.enums.AvaliacaoResposta;

/** Avaliação de uma resposta do assistente: UTIL ou NAO_UTIL. */
public record AvaliacaoRequest(AvaliacaoResposta avaliacao) {
}
