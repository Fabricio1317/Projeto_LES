package com.games.vendas.adapter.in.web.dto;

import com.games.vendas.domain.entity.ParametroSistema;

public record ParametroResponse(String chave, String valor, String descricao) {
    public static ParametroResponse from(ParametroSistema p) {
        return new ParametroResponse(p.getChave(), p.getValor(), p.getDescricao());
    }
}
