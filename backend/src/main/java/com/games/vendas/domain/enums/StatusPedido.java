package com.games.vendas.domain.enums;

/**
 * Status da compra conforme o DRS: EM PROCESSAMENTO (RF0038), APROVADA ou
 * REPROVADA (RN0038), EM TRANSPORTE (RF0039/RN0039) e ENTREGUE (RF0040/RN0040).
 * A descrição reproduz literalmente a nomenclatura do DRS.
 */
public enum StatusPedido {
    EM_PROCESSAMENTO("EM PROCESSAMENTO"),
    APROVADA("APROVADA"),
    REPROVADA("REPROVADA"),
    EM_TRANSPORTE("EM TRANSPORTE"),
    ENTREGUE("ENTREGUE");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Compras efetivadas: contam para o ranking (RN0027) e já tiveram baixa no estoque (RN0028). */
    public boolean isEfetivada() {
        return this == APROVADA || this == EM_TRANSPORTE || this == ENTREGUE;
    }
}
