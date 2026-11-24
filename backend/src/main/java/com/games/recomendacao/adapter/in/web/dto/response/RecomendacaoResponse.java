package com.games.recomendacao.adapter.in.web.dto.response;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.Recomendacao;

import java.math.BigDecimal;
import java.util.List;

/** Um jogo recomendado pela pontuação (preferências + histórico + vendas), com os motivos. */
public record RecomendacaoResponse(Long jogoId, String titulo, String plataforma, String genero, BigDecimal preco,
                                   double pontuacao, List<String> motivos) {

    public static RecomendacaoResponse from(Recomendacao r) {
        return new RecomendacaoResponse(r.jogo().id(), r.jogo().titulo(), r.jogo().plataforma(), r.jogo().genero(),
                r.jogo().preco(), r.pontuacao(), r.motivos());
    }
}
