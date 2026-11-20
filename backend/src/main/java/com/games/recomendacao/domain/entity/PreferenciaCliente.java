package com.games.recomendacao.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Preferências declaradas pelo cliente no próprio perfil (critério "e" da atividade: o DRS não diz
 * como obtê-las). Gêneros e plataformas favoritos, orçamento por jogo e observações livres; somadas
 * ao histórico de compras e ao feedback do chat, personalizam as recomendações.
 */
@Entity
@Table(name = "preferencia_cliente")
public class PreferenciaCliente {

    @Id
    private Long clienteId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preferencia_cliente_genero", joinColumns = @JoinColumn(name = "cliente_id"))
    @Column(name = "genero", length = 60)
    @OrderBy
    private Set<String> generos = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preferencia_cliente_plataforma", joinColumns = @JoinColumn(name = "cliente_id"))
    @Column(name = "plataforma", length = 60)
    @OrderBy
    private Set<String> plataformas = new LinkedHashSet<>();

    @Column(precision = 10, scale = 2)
    private BigDecimal orcamentoMaximo;

    @Column(length = 500)
    private String observacoes;

    private LocalDateTime atualizadoEm;

    protected PreferenciaCliente() {
    }

    public PreferenciaCliente(Long clienteId) {
        this.clienteId = clienteId;
    }

    public void atualizar(Collection<String> generos, Collection<String> plataformas, BigDecimal orcamentoMaximo, String observacoes) {
        this.generos.clear();
        this.generos.addAll(generos);
        this.plataformas.clear();
        this.plataformas.addAll(plataformas);
        this.orcamentoMaximo = orcamentoMaximo;
        this.observacoes = observacoes == null || observacoes.isBlank() ? null : observacoes.trim();
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getClienteId() { return clienteId; }
    public Set<String> getGeneros() { return generos; }
    public Set<String> getPlataformas() { return plataformas; }
    public BigDecimal getOrcamentoMaximo() { return orcamentoMaximo; }
    public String getObservacoes() { return observacoes; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
