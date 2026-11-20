package com.games.recomendacao.application.service;

import com.games.cliente.application.exception.IaIndisponivelException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.adapter.out.persistence.repository.TreinamentoModeloRepository;
import com.games.recomendacao.application.gateway.IaGenerativa;
import com.games.recomendacao.application.gateway.IaGenerativa.SituacaoAjuste;
import com.games.recomendacao.application.service.DatasetTreinamentoService.DatasetTreinamento;
import com.games.recomendacao.domain.entity.TreinamentoModelo;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import com.games.recomendacao.domain.enums.StatusTreinamento;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import com.games.vendas.application.event.CompraFinalizadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

/**
 * Ajuste fino do modelo generativo com os dados de vendas e o feedback do chat (RNF0044, item 3).
 *
 * <p>Procedimento: monta o dataset ({@link DatasetTreinamentoService}), publica o JSONL para o provedor,
 * inicia o ajuste fino (no provedor local: treino LoRA em Python e importação no Ollama) e acompanha
 * até o fim; o modelo ajustado passa a responder no chat. O modelo anterior continua respondendo enquanto o novo treina.
 *
 * <p>Personalização contínua: cada nova compra efetivada e cada nova avaliação no chat contam como dado
 * novo. Ao atingir o limite (padrão: 10), um novo ajuste fino começa sozinho, com todos os dados
 * acumulados até ali; o administrador também pode disparar um treino na tela "IA — Treinamento".
 */
@Service
public class TreinamentoService {

    private static final Logger log = LoggerFactory.getLogger(TreinamentoService.class);
    private static final List<StatusTreinamento> EM_ANDAMENTO = List.of(StatusTreinamento.PREPARANDO, StatusTreinamento.EM_TREINAMENTO);
    public static final int MINIMO_EXEMPLOS = 10;

    public record StatusIa(boolean configurada, String provedor, String motivoNaoConfigurada, String modeloBase,
                           String modeloEmUso, boolean modeloAjustado, boolean treinamentoEmAndamento,
                           long vendasEfetivadas, long feedbackUtil, long feedbackNaoUtil, long novosDados,
                           int limiarRetreino, boolean retreinoAutomatico) {}

    private final TreinamentoModeloRepository treinamentoRepository;
    private final DatasetTreinamentoService datasetService;
    private final IaGenerativa ia;
    private final TaskScheduler agendador;
    private final PedidoRepository pedidoRepository;
    private final MensagemAssistenteRepository mensagemRepository;
    private final int limiarRetreino;
    private final boolean retreinoAutomatico;
    private final Duration intervaloAcompanhamento;
    private ScheduledFuture<?> acompanhamento;

    public TreinamentoService(TreinamentoModeloRepository treinamentoRepository, DatasetTreinamentoService datasetService,
                              IaGenerativa ia, TaskScheduler agendador, PedidoRepository pedidoRepository,
                              MensagemAssistenteRepository mensagemRepository,
                              @Value("${nexus.ia.retreino.novos-dados:10}") int limiarRetreino,
                              @Value("${nexus.ia.retreino.automatico:true}") boolean retreinoAutomatico,
                              @Value("${nexus.ia.acompanhamento-segundos:20}") long acompanhamentoSegundos) {
        this.treinamentoRepository = treinamentoRepository;
        this.datasetService = datasetService;
        this.ia = ia;
        this.agendador = agendador;
        this.pedidoRepository = pedidoRepository;
        this.mensagemRepository = mensagemRepository;
        this.limiarRetreino = limiarRetreino;
        this.retreinoAutomatico = retreinoAutomatico;
        this.intervaloAcompanhamento = Duration.ofSeconds(acompanhamentoSegundos);
    }

    /** O último ajuste fino concluído: é o modelo que responde no chat. */
    public Optional<TreinamentoModelo> modeloEmUso() {
        return treinamentoRepository.findFirstByStatusOrderByIdDesc(StatusTreinamento.CONCLUIDO);
    }

    public List<TreinamentoModelo> historico() {
        return treinamentoRepository.findAllByOrderByIdDesc();
    }

    public DatasetTreinamento dataset() {
        return datasetService.montar();
    }

    public StatusIa status() {
        Optional<TreinamentoModelo> emUso = modeloEmUso();
        return new StatusIa(ia.configurada(), ia.descricao(), ia.motivoNaoConfigurada(), ia.modeloBase(),
                emUso.map(t -> t.getNomeModelo() + " (ajuste fino de " + t.getModeloBase() + ")").orElse(ia.modeloBase() + " (modelo base, sem ajuste fino)"),
                emUso.isPresent(), treinamentoRepository.existsByStatusIn(EM_ANDAMENTO),
                pedidoRepository.countByStatusIn(PerfilRecomendacaoService.EFETIVADAS),
                mensagemRepository.countByAvaliacao(AvaliacaoResposta.UTIL),
                mensagemRepository.countByAvaliacao(AvaliacaoResposta.NAO_UTIL),
                novosDados(), limiarRetreino, retreinoAutomatico);
    }

    /** Compras efetivadas e avaliações registradas depois do último treino (em andamento ou concluído). */
    public long novosDados() {
        Optional<TreinamentoModelo> ultimo = treinamentoRepository.findFirstByStatusInOrderByIdDesc(
                List.of(StatusTreinamento.EM_TREINAMENTO, StatusTreinamento.CONCLUIDO));
        long vendas = pedidoRepository.countByStatusIn(PerfilRecomendacaoService.EFETIVADAS) - ultimo.map(TreinamentoModelo::getVendasConsideradas).orElse(0L);
        long feedbacks = mensagemRepository.countByAvaliacaoIsNotNull() - ultimo.map(TreinamentoModelo::getFeedbacksConsiderados).orElse(0L);
        return Math.max(0, vendas) + Math.max(0, feedbacks);
    }

    /** Monta o dataset, publica e cria o job de ajuste fino. */
    public synchronized TreinamentoModelo iniciar(String motivo) {
        if (!ia.configurada()) {
            throw new IaIndisponivelException(ia.motivoNaoConfigurada());
        }
        if (treinamentoRepository.existsByStatusIn(EM_ANDAMENTO)) {
            throw new RegraNegocioException("RNF0044", "Já existe um ajuste fino em andamento. Aguarde ele terminar.");
        }
        DatasetTreinamento dataset = datasetService.montar();
        if (dataset.total() < MINIMO_EXEMPLOS) {
            throw new RegraNegocioException("RNF0044", "O ajuste fino precisa de pelo menos " + MINIMO_EXEMPLOS
                    + " exemplos; o dataset tem " + dataset.total() + ".");
        }

        TreinamentoModelo treinamento = treinamentoRepository.save(new TreinamentoModelo(
                proximaVersao(), motivo, ia.modeloBase(), dataset.exemplosVendas(),
                dataset.exemplosFeedback(), dataset.feedbackDescartado(), dataset.vendasConsideradas(), dataset.feedbacksConsiderados()));
        try {
            String arquivo = "treinamentos/" + treinamento.getNomeModelo() + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl";
            String datasetUri = ia.publicarDataset(arquivo, dataset.jsonl());
            treinamento.iniciado(datasetUri, ia.iniciarAjusteFino(datasetUri, treinamento.getNomeModelo()));
            treinamentoRepository.save(treinamento);
        } catch (RuntimeException e) {
            treinamento.falhar(e.getMessage());
            treinamentoRepository.save(treinamento);
            throw e;
        }
        log.info("Ajuste fino {} iniciado ({} exemplos de vendas, {} de feedback): {}", treinamento.getNomeModelo(),
                dataset.exemplosVendas(), dataset.exemplosFeedback(), treinamento.getJobId());
        agendarAcompanhamento();
        return treinamento;
    }

    /** Personalização contínua: novo ajuste fino quando há dados novos suficientes. */
    public void verificarRetreinoAutomatico() {
        if (!retreinoAutomatico || !ia.configurada() || treinamentoRepository.existsByStatusIn(EM_ANDAMENTO)
                || novosDados() < limiarRetreino) {
            return;
        }
        Optional<TreinamentoModelo> ultimo = treinamentoRepository.findAllByOrderByIdDesc().stream().findFirst();
        if (ultimo.isPresent() && ultimo.get().getStatus() == StatusTreinamento.FALHOU
                && ultimo.get().getConcluidoEm().isAfter(LocalDateTime.now().minusMinutes(30))) {
            return; // não repete em sequência um treino que acabou de falhar
        }
        agendador.schedule(() -> {
            try {
                iniciar("AUTOMATICO");
            } catch (RuntimeException e) {
                log.warn("Retreino automático não iniciado: {}", e.getMessage());
            }
        }, Instant.now());
    }

    /** Uma compra finalizada vira dado novo assim que o pagamento é aprovado (alguns segundos depois). */
    @TransactionalEventListener
    public void aoFinalizarCompra(CompraFinalizadaEvent evento) {
        agendador.schedule(this::verificarRetreinoAutomatico, Instant.now().plus(Duration.ofMinutes(1)));
    }

    /**
     * Na subida: o banco é recriado, mas o modelo ajustado continua no provedor. Registra o mais recente
     * como o modelo em uso (o chat não volta ao modelo base) e retoma um acompanhamento pendente.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void retomarAcompanhamento() {
        if (treinamentoRepository.count() == 0) {
            ia.ultimoModeloAjustado().ifPresent(m -> {
                treinamentoRepository.save(TreinamentoModelo.recuperado(m.versao(), ia.modeloBase(), m.nome(), m.detalhe(),
                        m.exemplos(), m.concluidoEm(), pedidoRepository.countByStatusIn(PerfilRecomendacaoService.EFETIVADAS),
                        mensagemRepository.countByAvaliacaoIsNotNull()));
                log.info("Modelo ajustado {} recuperado do provedor e em uso no chat.", m.nome());
            });
        }
        if (!treinamentoRepository.findByStatus(StatusTreinamento.EM_TREINAMENTO).isEmpty()) {
            agendarAcompanhamento();
        }
    }

    private int proximaVersao() {
        return treinamentoRepository.findAllByOrderByIdDesc().stream().mapToInt(TreinamentoModelo::getVersao).max().orElse(0) + 1;
    }

    private synchronized void agendarAcompanhamento() {
        if (acompanhamento == null || acompanhamento.isDone()) {
            acompanhamento = agendador.scheduleAtFixedRate(this::acompanhar, Instant.now().plus(intervaloAcompanhamento), intervaloAcompanhamento);
        }
    }

    /** Consulta o job no provedor enquanto houver ajuste fino em andamento (só nesse período). */
    void acompanhar() {
        List<TreinamentoModelo> emTreinamento = treinamentoRepository.findByStatus(StatusTreinamento.EM_TREINAMENTO);
        for (TreinamentoModelo treinamento : emTreinamento) {
            try {
                SituacaoAjuste situacao = ia.consultarAjuste(treinamento.getJobId());
                switch (situacao.estado()) {
                    case CONCLUIDO -> {
                        treinamento.atualizarProgresso(situacao.detalhe());
                        treinamento.concluir(situacao.modeloAjustado());
                        treinamentoRepository.save(treinamento);
                        log.info("Ajuste fino {} concluído: {}", treinamento.getNomeModelo(), situacao.modeloAjustado());
                    }
                    case FALHOU -> {
                        treinamento.falhar(situacao.erro());
                        treinamentoRepository.save(treinamento);
                        log.warn("Ajuste fino {} falhou: {}", treinamento.getNomeModelo(), situacao.erro());
                    }
                    default -> {
                        treinamento.atualizarProgresso(situacao.detalhe());
                        treinamentoRepository.save(treinamento);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Não foi possível consultar o ajuste fino {}: {}", treinamento.getNomeModelo(), e.getMessage());
            }
        }
        if (treinamentoRepository.findByStatus(StatusTreinamento.EM_TREINAMENTO).isEmpty()) {
            synchronized (this) {
                if (acompanhamento != null) acompanhamento.cancel(false);
            }
            verificarRetreinoAutomatico(); // dados que chegaram durante o treino
        }
    }
}
