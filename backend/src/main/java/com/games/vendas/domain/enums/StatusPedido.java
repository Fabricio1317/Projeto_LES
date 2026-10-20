package com.games.vendas.domain.enums;

/**
 * Ciclo de vida da compra. A descrição reproduz literalmente a nomenclatura do DRS.
 * EM PROCESSAMENTO → APROVADA / REPROVADA (sistema, após validar o pagamento)
 * APROVADA → EM TRANSPORTE → ENTREGUE (administrador)
 * ENTREGUE → EM TROCA (cliente pede a troca da compra inteira) → TROCA AUTORIZADA → TROCADO (administrador)
 */
public enum StatusPedido {
    EM_PROCESSAMENTO("EM PROCESSAMENTO"),
    APROVADA("APROVADA"),
    REPROVADA("REPROVADA"),
    EM_TRANSPORTE("EM TRANSPORTE"),
    ENTREGUE("ENTREGUE"),
    EM_TROCA("EM TROCA"),
    TROCA_AUTORIZADA("TROCA AUTORIZADA"),
    TROCADO("TROCADO");

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
