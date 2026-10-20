package com.games.vendas.domain.enums;

/** Ciclo do pedido de troca: EM TROCA → TROCA AUTORIZADA → TROCADO (RN0041, RN0042). */
public enum StatusTroca {
    EM_TROCA("EM TROCA"),
    TROCA_AUTORIZADA("TROCA AUTORIZADA"),
    TROCADO("TROCADO");

    private final String descricao;

    StatusTroca(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
