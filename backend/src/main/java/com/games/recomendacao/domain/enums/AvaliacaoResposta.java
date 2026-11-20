package com.games.recomendacao.domain.enums;

/** RNF0044 (item 3) — avaliação de uma resposta do assistente pelo cliente, no próprio chat. */
public enum AvaliacaoResposta {
    UTIL("Útil"),
    NAO_UTIL("Não útil");

    private final String descricao;

    AvaliacaoResposta(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
