package com.games.recomendacao.domain.enums;

/** Situação de um ajuste fino do modelo generativo (RNF0044, item 3). */
public enum StatusTreinamento {
    PREPARANDO("Preparando"),
    EM_TREINAMENTO("Em treinamento"),
    CONCLUIDO("Concluído"),
    FALHOU("Falhou");

    private final String descricao;

    StatusTreinamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
