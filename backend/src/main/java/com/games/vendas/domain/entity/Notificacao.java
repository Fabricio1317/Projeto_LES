package com.games.vendas.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Notificação enviada ao cliente, como a de troca autorizada (RN0046 / RNF0046). */
@Entity
@Table(name = "notificacao", indexes = {
        @Index(name = "idx_notificacao_cliente", columnList = "cliente_id")
})
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 300)
    private String mensagem;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false)
    private boolean lida;

    protected Notificacao() {}

    public Notificacao(Long clienteId, String mensagem) {
        this.clienteId = clienteId;
        this.mensagem = mensagem;
        this.dataHora = LocalDateTime.now();
        this.lida = false;
    }

    public void marcarComoLida() {
        this.lida = true;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getDataHora() { return dataHora; }
    public boolean isLida() { return lida; }
}
