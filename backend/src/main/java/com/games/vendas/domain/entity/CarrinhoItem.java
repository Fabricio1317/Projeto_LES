package com.games.vendas.domain.entity;

import com.games.vendas.domain.enums.SituacaoItemCarrinho;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF0031 / RF0032 — item do carrinho com sua quantidade editável. */
@Entity
@Table(name = "carrinho_item", indexes = {
        @Index(name = "idx_carrinho_item_jogo", columnList = "jogo_id")
})
public class CarrinhoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    @ManyToOne(optional = false)
    @JoinColumn(name = "jogo_id", nullable = false)
    private Jogo jogo;

    @Column(nullable = false)
    private int quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoItemCarrinho situacao;

    @Column(name = "data_inclusao", nullable = false)
    private LocalDateTime dataInclusao;

    protected CarrinhoItem() {}

    CarrinhoItem(Carrinho carrinho, Jogo jogo, int quantidade) {
        this.carrinho = carrinho;
        this.jogo = jogo;
        this.quantidade = quantidade;
        this.situacao = SituacaoItemCarrinho.ATIVO;
        this.dataInclusao = LocalDateTime.now();
    }

    public void alterarQuantidade(int novaQuantidade) {
        this.quantidade = novaQuantidade;
    }

    /** Reinclusão de um item que havia sido removido por prazo (RNF0042). */
    public void reativar(int novaQuantidade) {
        this.quantidade = novaQuantidade;
        this.situacao = SituacaoItemCarrinho.ATIVO;
        this.dataInclusao = LocalDateTime.now();
    }

    void marcarRemovidoPorPrazo() {
        this.situacao = SituacaoItemCarrinho.REMOVIDO_PRAZO;
    }

    public boolean isAtivo() {
        return situacao == SituacaoItemCarrinho.ATIVO;
    }

    public BigDecimal getSubtotal() {
        return jogo.getPreco().multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public Carrinho getCarrinho() { return carrinho; }
    public Jogo getJogo() { return jogo; }
    public int getQuantidade() { return quantidade; }
    public SituacaoItemCarrinho getSituacao() { return situacao; }
    public LocalDateTime getDataInclusao() { return dataInclusao; }
}
