package com.games.vendas.domain.entity;

import com.games.cliente.domain.enums.Bandeira;
import com.games.vendas.domain.enums.StatusPedido;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra realizada a partir do carrinho (RF0033 / RF0038). Nasce
 * EM PROCESSAMENTO e segue o ciclo APROVADA/REPROVADA (RN0038) →
 * EM TRANSPORTE (RN0039) → ENTREGUE (RN0040).
 */
@Entity
@Table(name = "pedido", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pedido_codigo", columnNames = "codigo")
}, indexes = {
        @Index(name = "idx_pedido_cliente_id", columnList = "cliente_id"),
        @Index(name = "idx_pedido_status", columnList = "status")
})
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "data_compra", nullable = false)
    private LocalDateTime dataCompra;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status;

    @Embedded
    private EnderecoEntrega enderecoEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal frete;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Parte do total coberta por cupons (nunca maior que o total). */
    @Column(name = "valor_cupons", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorCupons;

    /** Excedente dos cupons sobre o total; vira um cupom de troca na finalização da compra (RN0036). */
    @Column(name = "valor_troco", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTroco;

    @Column(name = "cupom_troco_gerado", length = 30)
    private String cupomTrocoGerado;

    @Column(name = "motivo_reprovacao", length = 255)
    private String motivoReprovacao;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PedidoItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PagamentoCartao> pagamentos = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PedidoCupom> cupons = new ArrayList<>();

    protected Pedido() {}

    public Pedido(String codigo, Long clienteId, EnderecoEntrega enderecoEntrega, BigDecimal frete) {
        this.codigo = codigo;
        this.clienteId = clienteId;
        this.enderecoEntrega = enderecoEntrega;
        this.frete = frete;
        this.subtotal = BigDecimal.ZERO;
        this.total = frete;
        this.valorCupons = BigDecimal.ZERO;
        this.valorTroco = BigDecimal.ZERO;
        this.status = StatusPedido.EM_PROCESSAMENTO;
        this.dataCompra = LocalDateTime.now();
        this.dataAtualizacao = this.dataCompra;
    }

    public void adicionarItem(Jogo jogo, int quantidade) {
        PedidoItem item = new PedidoItem(this, jogo, quantidade);
        itens.add(item);
        subtotal = subtotal.add(item.getSubtotal());
        total = subtotal.add(frete);
    }

    public void adicionarCupom(Cupom cupom) {
        cupons.add(new PedidoCupom(this, cupom));
    }

    public void adicionarPagamentoCartao(Long cartaoId, String numero, String nomeImpresso, Bandeira bandeira, BigDecimal valor) {
        pagamentos.add(new PagamentoCartao(this, cartaoId, numero, nomeImpresso, bandeira, valor));
    }

    public void registrarCupons(BigDecimal valorCupons, BigDecimal valorTroco) {
        this.valorCupons = valorCupons;
        this.valorTroco = valorTroco;
    }

    /** RN0036 — código do cupom de troca emitido com a diferença dos cupons. */
    public void registrarCupomTroco(String codigo) {
        this.cupomTrocoGerado = codigo;
    }

    /** RN0038 — pagamento validado com sucesso. */
    public void aprovar() {
        mudarStatus(StatusPedido.APROVADA);
    }

    /** RN0038 — pagamento não validado. */
    public void reprovar(String motivo) {
        this.motivoReprovacao = motivo;
        mudarStatus(StatusPedido.REPROVADA);
    }

    /** RN0039 */
    public void despachar() {
        mudarStatus(StatusPedido.EM_TRANSPORTE);
    }

    /** RN0040 */
    public void confirmarEntrega() {
        mudarStatus(StatusPedido.ENTREGUE);
    }

    private void mudarStatus(StatusPedido novo) {
        this.status = novo;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Long getClienteId() { return clienteId; }
    public LocalDateTime getDataCompra() { return dataCompra; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public StatusPedido getStatus() { return status; }
    public EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getFrete() { return frete; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getValorCupons() { return valorCupons; }
    public BigDecimal getValorTroco() { return valorTroco; }
    public String getCupomTrocoGerado() { return cupomTrocoGerado; }
    public String getMotivoReprovacao() { return motivoReprovacao; }
    public List<PedidoItem> getItens() { return itens; }
    public List<PagamentoCartao> getPagamentos() { return pagamentos; }
    public List<PedidoCupom> getCupons() { return cupons; }
}
