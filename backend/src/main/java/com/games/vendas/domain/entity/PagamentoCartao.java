package com.games.vendas.domain.entity;

import com.games.cliente.domain.enums.Bandeira;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Parcela da compra paga com um cartão (RF0036 / RN0034). Guarda os dados do
 * cartão usados na compra mesmo quando o cliente optou por não incorporá-lo
 * ao perfil — necessários para a validação pela operadora (RN0037). Como em
 * {@link com.games.cliente.domain.entity.Cartao}, o número em texto puro é
 * aceitável apenas neste contexto acadêmico.
 */
@Entity
@Table(name = "pagamento_cartao")
public class PagamentoCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    /** Preenchido quando o cartão pertence ao perfil do cliente. */
    @Column(name = "cartao_id")
    private Long cartaoId;

    @Column(nullable = false, length = 19)
    private String numero;

    @Column(name = "nome_impresso", nullable = false, length = 100)
    private String nomeImpresso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Bandeira bandeira;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Resultado da operadora (RN0037): null enquanto a compra está EM PROCESSAMENTO. */
    private Boolean aprovado;

    protected PagamentoCartao() {}

    PagamentoCartao(Pedido pedido, Long cartaoId, String numero, String nomeImpresso, Bandeira bandeira, BigDecimal valor) {
        this.pedido = pedido;
        this.cartaoId = cartaoId;
        this.numero = numero;
        this.nomeImpresso = nomeImpresso;
        this.bandeira = bandeira;
        this.valor = valor;
    }

    public void registrarRetornoOperadora(boolean aprovado) {
        this.aprovado = aprovado;
    }

    public String getNumeroMascarado() {
        return "**** **** **** " + numero.substring(numero.length() - 4);
    }

    public Long getId() { return id; }
    public Long getCartaoId() { return cartaoId; }
    public String getNumero() { return numero; }
    public String getNomeImpresso() { return nomeImpresso; }
    public Bandeira getBandeira() { return bandeira; }
    public BigDecimal getValor() { return valor; }
    public Boolean getAprovado() { return aprovado; }
}
