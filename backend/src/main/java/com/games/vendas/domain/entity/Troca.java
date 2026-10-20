package com.games.vendas.domain.entity;

import com.games.vendas.domain.enums.StatusTroca;
import com.games.vendas.domain.enums.TipoTroca;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de troca (RN0041): gerado para cada item que o cliente escolhe trocar,
 * ou uma vez para a compra inteira. Segue o ciclo EM TROCA → TROCA AUTORIZADA →
 * TROCADO; ao ser concluído, gera um cupom de troca com o valor dos itens devolvidos.
 */
@Entity
@Table(name = "troca", uniqueConstraints = {
        @UniqueConstraint(name = "uk_troca_codigo", columnNames = "codigo")
}, indexes = {
        @Index(name = "idx_troca_status", columnList = "status"),
        @Index(name = "idx_troca_cliente", columnList = "cliente_id")
})
public class Troca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTroca tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTroca status;

    @Column(name = "data_solicitacao", nullable = false)
    private LocalDateTime dataSolicitacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    /** Informado pelo administrador ao confirmar o recebimento (RF0054). */
    @Column(name = "retornou_estoque")
    private Boolean retornouEstoque;

    @Column(name = "cupom_gerado", length = 30)
    private String cupomGerado;

    @OneToMany(mappedBy = "troca", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TrocaItem> itens = new ArrayList<>();

    protected Troca() {}

    public Troca(String codigo, Pedido pedido, TipoTroca tipo) {
        this.codigo = codigo;
        this.pedido = pedido;
        this.clienteId = pedido.getClienteId();
        this.tipo = tipo;
        this.status = StatusTroca.EM_TROCA;
        this.dataSolicitacao = LocalDateTime.now();
        this.dataAtualizacao = this.dataSolicitacao;
    }

    public void adicionarItem(PedidoItem pedidoItem, int quantidade) {
        itens.add(new TrocaItem(this, pedidoItem, quantidade));
    }

    public void autorizar() {
        mudarStatus(StatusTroca.TROCA_AUTORIZADA);
    }

    /** RN0042 — itens recebidos: a troca passa a TROCADO. */
    public void confirmarRecebimento(boolean retornouEstoque, String cupomGerado) {
        this.retornouEstoque = retornouEstoque;
        this.cupomGerado = cupomGerado;
        mudarStatus(StatusTroca.TROCADO);
    }

    /** Valor dos itens devolvidos, pelo preço pago na compra; vira o valor do cupom de troca. */
    public BigDecimal getValor() {
        return itens.stream().map(TrocaItem::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void mudarStatus(StatusTroca novo) {
        this.status = novo;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Pedido getPedido() { return pedido; }
    public Long getClienteId() { return clienteId; }
    public TipoTroca getTipo() { return tipo; }
    public StatusTroca getStatus() { return status; }
    public LocalDateTime getDataSolicitacao() { return dataSolicitacao; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public Boolean getRetornouEstoque() { return retornouEstoque; }
    public String getCupomGerado() { return cupomGerado; }
    public List<TrocaItem> getItens() { return itens; }
}
