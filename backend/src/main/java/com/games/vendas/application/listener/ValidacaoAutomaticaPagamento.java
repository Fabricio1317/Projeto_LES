package com.games.vendas.application.listener;

import com.games.vendas.application.event.CompraFinalizadaEvent;
import com.games.vendas.application.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * RN0037 / RN0038 — comportamento automático do sistema: depois de finalizada (EM PROCESSAMENTO),
 * a compra tem o pagamento validado e passa a APROVADA ou REPROVADA, sem ação do administrador.
 * Cada compra finalizada agenda a sua própria validação; o atraso simula o tempo de resposta da
 * operadora de cartão. Sem compras novas, nada é executado (não há consulta periódica ao banco).
 */
@Component
public class ValidacaoAutomaticaPagamento {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoAutomaticaPagamento.class);

    private final PedidoService pedidoService;
    private final TaskScheduler agendador;
    private final Duration atraso;

    public ValidacaoAutomaticaPagamento(PedidoService pedidoService, TaskScheduler agendador,
                                        @Value("${nexus.pagamento.atraso-segundos:3}") long atrasoSegundos) {
        this.pedidoService = pedidoService;
        this.agendador = agendador;
        this.atraso = Duration.ofSeconds(atrasoSegundos);
    }

    /** Depois que a compra finalizada é gravada, agenda a validação do seu pagamento. */
    @TransactionalEventListener
    public void aoFinalizarCompra(CompraFinalizadaEvent evento) {
        agendador.schedule(() -> validar(evento.pedidoId()), Instant.now().plus(atraso));
    }

    /** Na subida da aplicação, valida as compras que tenham ficado EM PROCESSAMENTO. */
    @EventListener(ApplicationReadyEvent.class)
    public void validarPendentes() {
        pedidoService.listarAguardandoValidacao(LocalDateTime.now()).forEach(this::validar);
    }

    private void validar(Long pedidoId) {
        try {
            pedidoService.validarPagamento(pedidoId);
        } catch (RuntimeException e) {
            log.warn("Falha ao validar o pagamento do pedido {}: {}", pedidoId, e.getMessage());
        }
    }
}
