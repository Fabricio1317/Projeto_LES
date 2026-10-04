package com.games.vendas.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Item da compra, com o preço praticado no momento da finalização. */
@Entity
@Table(name = "pedido_item", indexes = {
        @Index(name = "idx_pedido_item_jogo", columnList = "jogo_id")
})
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "jogo_id", nullable = false)
    private Jogo jogo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false)
    private int quantidade;

    protected PedidoItem() {}

    PedidoItem(Pedido pedido, Jogo jogo, int quantidade) {
        this.pedido = pedido;
        this.jogo = jogo;
        this.titulo = jogo.getTitulo();
        this.precoUnitario = jogo.getPreco();
        this.quantidade = quantidade;
    }

    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public Jogo getJogo() { return jogo; }
    public String getTitulo() { return titulo; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public int getQuantidade() { return quantidade; }
}
