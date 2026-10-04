package com.games.vendas.domain.entity;

import jakarta.persistence.*;

/** Parâmetros configuráveis do sistema (ex.: prazo de bloqueio do carrinho, RN0044). */
@Entity
@Table(name = "parametro_sistema")
public class ParametroSistema {

    public static final String PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS = "PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS";

    @Id
    @Column(length = 60)
    private String chave;

    @Column(nullable = false, length = 100)
    private String valor;

    @Column(length = 255)
    private String descricao;

    protected ParametroSistema() {}

    public ParametroSistema(String chave, String valor, String descricao) {
        this.chave = chave;
        this.valor = valor;
        this.descricao = descricao;
    }

    public void alterarValor(String novoValor) {
        this.valor = novoValor;
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }
    public String getDescricao() { return descricao; }
}
