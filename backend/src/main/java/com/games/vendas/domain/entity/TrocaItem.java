package com.games.vendas.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Item da compra (e quantidade) incluído em um pedido de troca. */
@Entity
@Table(name = "troca_item", indexes = {
        @Index(name = "idx_troca_item_pedido_item", columnList = "pedido_item_id")
})
public class TrocaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "troca_id", nullable = false)
    private Troca troca;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_item_id", nullable = false)
    private PedidoItem pedidoItem;

    @Column(nullable = false)
    private int quantidade;

    protected TrocaItem() {}

    TrocaItem(Troca troca, PedidoItem pedidoItem, int quantidade) {
        this.troca = troca;
        this.pedidoItem = pedidoItem;
        this.quantidade = quantidade;
    }

    public BigDecimal getValor() {
        return pedidoItem.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public PedidoItem getPedidoItem() { return pedidoItem; }
    public int getQuantidade() { return quantidade; }
}
