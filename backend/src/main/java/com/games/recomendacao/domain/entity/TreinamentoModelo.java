package com.games.recomendacao.domain.entity;

import com.games.recomendacao.domain.enums.StatusTreinamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Uma rodada de ajuste fino do modelo generativo (RNF0044, item 3): com quais dados foi feita,
 * o job no provedor e o modelo ajustado que resultou. O último CONCLUIDO é o modelo em uso no chat.
 */
@Entity
@Table(name = "treinamento_modelo")
public class TreinamentoModelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int versao;

    /** MANUAL (botão do administrador) ou AUTOMATICO (novos dados de venda e feedback). */
    @Column(nullable = false, length = 20)
    private String motivo;

    @Column(nullable = false, length = 80)
    private String modeloBase;

    @Column(nullable = false, length = 80)
    private String nomeModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTreinamento status = StatusTreinamento.PREPARANDO;

    private int exemplosVendas;
    private int exemplosFeedback;
    private int feedbackDescartado;

    /** Quantidades na base no momento do treino, para medir os dados novos desde então. */
    private long vendasConsideradas;
    private long feedbacksConsiderados;

    @Column(length = 300)
    private String datasetUri;

    @Column(length = 300)
    private String jobId;

    @Column(length = 300)
    private String modeloAjustado;

    @Column(length = 2000)
    private String mensagemErro;

    /** Andamento informado pelo provedor (etapa, porcentagem, perda) ou o resumo ao concluir. */
    @Column(length = 300)
    private String progresso;

    @Column(nullable = false)
    private LocalDateTime iniciadoEm;

    private LocalDateTime concluidoEm;

    protected TreinamentoModelo() {
    }

    public TreinamentoModelo(int versao, String motivo, String modeloBase, int exemplosVendas, int exemplosFeedback,
                             int feedbackDescartado, long vendasConsideradas, long feedbacksConsiderados) {
        this.versao = versao;
        this.motivo = motivo;
        this.modeloBase = modeloBase;
        this.nomeModelo = "nexus-recomendador-v" + versao;
        this.exemplosVendas = exemplosVendas;
        this.exemplosFeedback = exemplosFeedback;
        this.feedbackDescartado = feedbackDescartado;
        this.vendasConsideradas = vendasConsideradas;
        this.feedbacksConsiderados = feedbacksConsiderados;
        this.iniciadoEm = LocalDateTime.now();
    }

    /** Modelo ajustado antes de a aplicação reiniciar (o banco é recriado, o modelo continua no provedor). */
    public static TreinamentoModelo recuperado(int versao, String modeloBase, String nomeModelo, String detalhe, int exemplos,
                                               LocalDateTime concluidoEm, long vendasConsideradas, long feedbacksConsiderados) {
        TreinamentoModelo t = new TreinamentoModelo(versao, "RECUPERADO", modeloBase, exemplos, 0, 0,
                vendasConsideradas, feedbacksConsiderados);
        t.nomeModelo = nomeModelo;
        t.iniciadoEm = concluidoEm;
        t.atualizarProgresso(detalhe);
        t.concluir(nomeModelo);
        t.concluidoEm = concluidoEm;
        return t;
    }

    public void iniciado(String datasetUri, String jobId) {
        this.datasetUri = datasetUri;
        this.jobId = jobId;
        this.status = StatusTreinamento.EM_TREINAMENTO;
    }

    public void atualizarProgresso(String progresso) {
        this.progresso = progresso == null ? null : progresso.substring(0, Math.min(290, progresso.length()));
    }

    public void concluir(String modeloAjustado) {
        this.modeloAjustado = modeloAjustado;
        this.status = StatusTreinamento.CONCLUIDO;
        this.concluidoEm = LocalDateTime.now();
    }

    public void falhar(String mensagemErro) {
        String texto = mensagemErro == null ? "Erro desconhecido" : mensagemErro;
        this.mensagemErro = texto.substring(0, Math.min(1990, texto.length()));
        this.status = StatusTreinamento.FALHOU;
        this.concluidoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public int getVersao() { return versao; }
    public String getMotivo() { return motivo; }
    public String getModeloBase() { return modeloBase; }
    public String getNomeModelo() { return nomeModelo; }
    public StatusTreinamento getStatus() { return status; }
    public int getExemplosVendas() { return exemplosVendas; }
    public int getExemplosFeedback() { return exemplosFeedback; }
    public int getFeedbackDescartado() { return feedbackDescartado; }
    public long getVendasConsideradas() { return vendasConsideradas; }
    public long getFeedbacksConsiderados() { return feedbacksConsiderados; }
    public String getDatasetUri() { return datasetUri; }
    public String getJobId() { return jobId; }
    public String getModeloAjustado() { return modeloAjustado; }
    public String getMensagemErro() { return mensagemErro; }
    public String getProgresso() { return progresso; }
    public LocalDateTime getIniciadoEm() { return iniciadoEm; }
    public LocalDateTime getConcluidoEm() { return concluidoEm; }
}
