package com.games.vendas.domain.enums;

/** RN0041 — troca de um item da compra ou da compra inteira. */
public enum TipoTroca {
    ITEM("Item"),
    COMPRA("Compra inteira");

    private final String descricao;

    TipoTroca(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
