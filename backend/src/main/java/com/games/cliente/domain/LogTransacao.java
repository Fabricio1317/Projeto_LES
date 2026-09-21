package com.games.cliente.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * RNF0012 — toda operação de inserção ou alteração deve gerar um registro
 * de log com data, hora, usuário responsável e o dado alterado.
 *
 * Nota sobre "usuário responsável": este módulo isolado de Cliente não
 * implementa autenticação/login de um ator administrativo separado (isso
 * existe em outros módulos do Nexus). Por isso, registramos como usuário
 * o próprio código do cliente afetado, já que toda ação aqui é o cliente
 * gerenciando seus próprios dados. Em um sistema com login, este campo
 * viria do usuário autenticado na sessão.
 */
@Entity
@Table(name = "log_transacao", indexes = {
        @Index(name = "idx_log_entidade", columnList = "entidade,entidade_id")
})
public class LogTransacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String entidade; // "Cliente", "Endereco", "Cartao"

    @Column(name = "entidade_id", nullable = false)
    private Long entidadeId;

    @Column(nullable = false, length = 20)
    private String operacao; // "INSERT", "UPDATE"

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "dado_alterado", nullable = false, length = 1000)
    private String dadoAlterado;

    protected LogTransacao() {}

    public LogTransacao(String entidade, Long entidadeId, String operacao, String usuario, String dadoAlterado) {
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.operacao = operacao;
        this.usuario = usuario;
        this.dataHora = LocalDateTime.now();
        this.dadoAlterado = dadoAlterado;
    }

    public Long getId() { return id; }
    public String getEntidade() { return entidade; }
    public Long getEntidadeId() { return entidadeId; }
    public String getOperacao() { return operacao; }
    public String getUsuario() { return usuario; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getDadoAlterado() { return dadoAlterado; }
}
