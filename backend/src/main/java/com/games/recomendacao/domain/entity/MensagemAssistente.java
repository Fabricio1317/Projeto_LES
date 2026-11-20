package com.games.recomendacao.domain.entity;

import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Uma troca do chat (pergunta do cliente e resposta da IA) com a avaliação dada pelo cliente.
 * As respostas avaliadas como úteis viram exemplos do ajuste fino; as não úteis ficam de fora e
 * tiram peso dos jogos que elas sugeriram (RNF0044, item 3).
 */
@Entity
@Table(name = "mensagem_assistente", indexes = {
        @Index(name = "idx_mensagem_assistente_conversa", columnList = "conversa_id"),
        @Index(name = "idx_mensagem_assistente_avaliacao", columnList = "avaliacao")
})
public class MensagemAssistente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clienteId;

    @Column(name = "conversa_id", nullable = false, length = 60)
    private String conversaId;

    @Column(nullable = false, columnDefinition = "text")
    private String pergunta;

    @Column(nullable = false, columnDefinition = "text")
    private String resposta;

    /** Modelo que respondeu: o base ou a versão ajustada em uso naquele momento. */
    @Column(nullable = false, length = 120)
    private String modelo;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AvaliacaoResposta avaliacao;

    private LocalDateTime dataAvaliacao;

    protected MensagemAssistente() {
    }

    public MensagemAssistente(Long clienteId, String conversaId, String pergunta, String resposta, String modelo) {
        this.clienteId = clienteId;
        this.conversaId = conversaId;
        this.pergunta = pergunta;
        this.resposta = resposta;
        this.modelo = modelo;
        this.dataHora = LocalDateTime.now();
    }

    public void avaliar(AvaliacaoResposta avaliacao) {
        this.avaliacao = avaliacao;
        this.dataAvaliacao = LocalDateTime.now();
    }

    /** Massa de dados: registra a conversa e a avaliação no passado. */
    public void definirDatas(LocalDateTime dataHora, LocalDateTime dataAvaliacao) {
        this.dataHora = dataHora;
        this.dataAvaliacao = dataAvaliacao;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getConversaId() { return conversaId; }
    public String getPergunta() { return pergunta; }
    public String getResposta() { return resposta; }
    public String getModelo() { return modelo; }
    public LocalDateTime getDataHora() { return dataHora; }
    public AvaliacaoResposta getAvaliacao() { return avaliacao; }
    public LocalDateTime getDataAvaliacao() { return dataAvaliacao; }
}
