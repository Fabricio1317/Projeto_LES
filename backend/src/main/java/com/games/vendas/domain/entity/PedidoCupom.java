package com.games.vendas.domain.entity;

import jakarta.persistence.*;

/** Cupom aplicado como forma de pagamento de uma compra (RF0037). */
@Entity
@Table(name = "pedido_cupom")
public class PedidoCupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cupom_id", nullable = false)
    private Cupom cupom;

    protected PedidoCupom() {}

    PedidoCupom(Pedido pedido, Cupom cupom) {
        this.pedido = pedido;
        this.cupom = cupom;
    }

    public Long getId() { return id; }
    public Cupom getCupom() { return cupom; }
}
