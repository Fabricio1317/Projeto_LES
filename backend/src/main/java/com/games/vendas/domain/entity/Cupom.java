package com.games.vendas.domain.entity;

import com.games.vendas.domain.enums.TipoCupom;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RF0037 — cupom usado como forma de pagamento.
 * PROMOCIONAL: código divulgado a qualquer cliente, reutilizável enquanto válido.
 * TROCA: pertence a um cliente e só pode ser usado uma vez (inclusive o
 * cupom de troco gerado pela RN0036).
 */
@Entity
@Table(name = "cupom", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cupom_codigo", columnNames = "codigo")
})
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoCupom tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "cliente_id")
    private Long clienteId;

    private LocalDate validade;

    @Column(nullable = false)
    private boolean utilizado;

    /** Pedido que originou este cupom, quando ele é um troco gerado pela RN0036. */
    @Column(name = "pedido_origem_id")
    private Long pedidoOrigemId;

    protected Cupom() {}

    public Cupom(String codigo, TipoCupom tipo, BigDecimal valor, Long clienteId, LocalDate validade, Long pedidoOrigemId) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.valor = valor;
        this.clienteId = clienteId;
        this.validade = validade;
        this.pedidoOrigemId = pedidoOrigemId;
        this.utilizado = false;
    }

    public boolean isVencido(LocalDate hoje) {
        return validade != null && validade.isBefore(hoje);
    }

    public boolean isUsoUnico() {
        return tipo == TipoCupom.TROCA;
    }

    public void marcarUtilizado() { this.utilizado = true; }
    public void liberar() { this.utilizado = false; }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public TipoCupom getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public Long getClienteId() { return clienteId; }
    public LocalDate getValidade() { return validade; }
    public boolean isUtilizado() { return utilizado; }
    public Long getPedidoOrigemId() { return pedidoOrigemId; }
}
