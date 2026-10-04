package com.games.vendas.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Produto vendido na loja Nexus (equivalente ao "livro" do DRS). Contém os
 * dados de identificação do jogo e os necessários ao fluxo de vendas (preço,
 * estoque e peso da mídia física para o frete). O cadastro completo do
 * produto (RF0011–RF0016 / RN0011) pertence a outra entrega.
 */
@Entity
@Table(name = "jogo")
public class Jogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 50)
    private String plataforma;

    /** Gênero do jogo (equivalente à "categoria" do DRS). */
    @Column(nullable = false, length = 60)
    private String genero;

    @Column(nullable = false, length = 150)
    private String desenvolvedora;

    @Column(length = 150)
    private String distribuidora;

    /** Classificação indicativa brasileira: L, 10, 12, 14, 16 ou 18. */
    @Column(name = "classificacao_indicativa", nullable = false, length = 2)
    private String classificacaoIndicativa;

    private Integer ano;

    @Column(name = "codigo_barras", length = 20)
    private String codigoBarras;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    /** Quantidade física em estoque; só diminui quando a compra é efetivada (RN0028 / RF0053). */
    @Column(nullable = false)
    private int estoque;

    @Column(name = "peso_kg", nullable = false, precision = 6, scale = 3)
    private BigDecimal pesoKg;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Jogo() {}

    public Jogo(String titulo, String plataforma, String genero, String desenvolvedora, String distribuidora,
                String classificacaoIndicativa, Integer ano, String codigoBarras, BigDecimal preco,
                int estoque, BigDecimal pesoKg) {
        this.titulo = titulo;
        this.plataforma = plataforma;
        this.genero = genero;
        this.desenvolvedora = desenvolvedora;
        this.distribuidora = distribuidora;
        this.classificacaoIndicativa = classificacaoIndicativa;
        this.ano = ano;
        this.codigoBarras = codigoBarras;
        this.preco = preco;
        this.estoque = estoque;
        this.pesoKg = pesoKg;
        this.ativo = true;
    }

    /** RF0053 — baixa no estoque do total de itens vendidos. */
    public void darBaixa(int quantidade) {
        if (quantidade > estoque) {
            throw new IllegalStateException("Estoque insuficiente para dar baixa em " + titulo);
        }
        this.estoque -= quantidade;
    }

    public void ajustarEstoque(int novoEstoque) {
        this.estoque = novoEstoque;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getPlataforma() { return plataforma; }
    public String getGenero() { return genero; }
    public String getDesenvolvedora() { return desenvolvedora; }
    public String getDistribuidora() { return distribuidora; }
    public String getClassificacaoIndicativa() { return classificacaoIndicativa; }
    public Integer getAno() { return ano; }
    public String getCodigoBarras() { return codigoBarras; }
    public BigDecimal getPreco() { return preco; }
    public int getEstoque() { return estoque; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public boolean isAtivo() { return ativo; }
}
