package com.games.vendas.application.service;

import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.application.service.AuditoriaService;
import com.games.vendas.adapter.in.web.dto.request.ItemTrocaRequest;
import com.games.vendas.adapter.in.web.dto.request.SolicitarTrocaRequest;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import com.games.vendas.adapter.out.persistence.repository.TrocaItemRepository;
import com.games.vendas.adapter.out.persistence.repository.TrocaRepository;
import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.entity.PedidoItem;
import com.games.vendas.domain.entity.Troca;
import com.games.vendas.domain.entity.TrocaItem;
import com.games.vendas.domain.enums.StatusPedido;
import com.games.vendas.domain.enums.StatusTroca;
import com.games.vendas.domain.enums.TipoTroca;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Troca e devolução (numeração da atividade):
 * RF0040 / RN0041 / RN0043 — o cliente solicita a troca de itens ou da compra inteira de um pedido ENTREGUE;
 *                            cada item selecionado gera um pedido de troca EM TROCA.
 * RF0042 — o administrador visualiza os pedidos de troca.
 * RF0041 / RNF0046 — o administrador autoriza a troca e o cliente é notificado.
 * RF0043 / RN0042 — o administrador confirma o recebimento e a troca passa a TROCADO.
 * RF0044 — é gerado um cupom de troca com o valor dos itens devolvidos.
 * RF0054 — os itens devolvidos voltam ao estoque, se o administrador indicar.
 */
@Service
public class TrocaService {

    private final TrocaRepository repository;
    private final TrocaItemRepository trocaItemRepository;
    private final PedidoRepository pedidoRepository;
    private final CupomService cupomService;
    private final NotificacaoService notificacaoService;
    private final AuditoriaService auditoria;

    public TrocaService(TrocaRepository repository, TrocaItemRepository trocaItemRepository,
                        PedidoRepository pedidoRepository, CupomService cupomService,
                        NotificacaoService notificacaoService, AuditoriaService auditoria) {
        this.repository = repository;
        this.trocaItemRepository = trocaItemRepository;
        this.pedidoRepository = pedidoRepository;
        this.cupomService = cupomService;
        this.notificacaoService = notificacaoService;
        this.auditoria = auditoria;
    }

    /** RF0040 / RN0041 / RN0043 — solicitação de troca pelo cliente. */
    @Transactional
    public List<Troca> solicitar(Long clienteId, Long pedidoId, SolicitarTrocaRequest req) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .filter(p -> p.getClienteId().equals(clienteId))
                .orElseThrow(() -> new RegraNegocioException("RF0040", "Pedido não encontrado para este cliente."));
        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new RegraNegocioException("RN0043",
                    "Somente itens de pedidos ENTREGUE podem ter troca solicitada. Este pedido está "
                            + pedido.getStatus().getDescricao() + ".");
        }

        List<Troca> geradas = req.compraInteira() ? List.of(trocarCompraInteira(pedido)) : trocarItens(pedido, req.itens());
        List<Troca> salvas = repository.saveAll(geradas);
        salvas.forEach(t -> auditoria.registrar("Troca", t.getId(), "INSERT", "cliente-" + clienteId,
                "Troca " + t.getCodigo() + " (" + t.getTipo().getDescricao() + ") solicitada para o pedido "
                        + pedido.getCodigo() + ": status " + t.getStatus().getDescricao()));
        if (req.compraInteira()) {
            pedidoRepository.save(pedido);
            registrarStatusPedido(pedido, "cliente-" + clienteId);
        }
        return salvas;
    }

    /** RF0041 / RNF0046 — autoriza a troca e notifica o cliente. */
    @Transactional
    public Troca autorizar(Long trocaId) {
        Troca troca = buscar(trocaId);
        if (troca.getStatus() != StatusTroca.EM_TROCA) {
            throw new RegraNegocioException("RF0041", "Somente pedidos de troca EM TROCA podem ser autorizados.");
        }
        troca.autorizar();
        if (troca.getTipo() == TipoTroca.COMPRA) {
            troca.getPedido().autorizarTroca();
            registrarStatusPedido(troca.getPedido(), "admin");
        }
        Troca salva = repository.save(troca);
        registrarStatusTroca(salva);
        notificacaoService.notificar(salva.getClienteId(), "Sua troca " + salva.getCodigo() + " do pedido "
                + salva.getPedido().getCodigo() + " foi autorizada. Envie o(s) item(ns) para concluirmos a troca.");
        return salva;
    }

    /** RF0043 / RN0042 / RF0044 / RF0054 — confirma o recebimento, gera o cupom e, se indicado, devolve ao estoque. */
    @Transactional
    public Troca confirmarRecebimento(Long trocaId, boolean retornarEstoque) {
        Troca troca = buscar(trocaId);
        if (troca.getStatus() != StatusTroca.TROCA_AUTORIZADA) {
            throw new RegraNegocioException("RF0043",
                    "Somente trocas com status TROCA AUTORIZADA podem ter o recebimento confirmado.");
        }

        if (retornarEstoque) {
            for (TrocaItem item : troca.getItens()) {
                var jogo = item.getPedidoItem().getJogo();
                int anterior = jogo.getEstoque();
                jogo.darReentrada(item.getQuantidade());
                auditoria.registrar("Jogo", jogo.getId(), "UPDATE", "admin", "Reentrada no estoque de "
                        + jogo.getTitulo() + " pela troca " + troca.getCodigo() + ": de " + anterior + " para " + jogo.getEstoque());
            }
        }

        Cupom cupom = cupomService.gerarCupomDevolucao(troca.getClienteId(), troca.getValor(),
                troca.getPedido().getId(), troca.getCodigo());
        troca.confirmarRecebimento(retornarEstoque, cupom.getCodigo());
        if (troca.getTipo() == TipoTroca.COMPRA) {
            troca.getPedido().concluirTroca();
            registrarStatusPedido(troca.getPedido(), "admin");
        }
        Troca salva = repository.save(troca);
        registrarStatusTroca(salva);
        notificacaoService.notificar(salva.getClienteId(), "Recebemos os itens da troca " + salva.getCodigo()
                + ". O cupom de troca " + cupom.getCodigo() + " de R$ " + formatar(cupom.getValor())
                + " está disponível para suas próximas compras.");
        return salva;
    }

    /** RF0042 — pedidos de troca, opcionalmente filtrados pelo status. */
    @Transactional(readOnly = true)
    public List<Troca> listar(StatusTroca status) {
        return status == null ? repository.findAllByOrderByDataSolicitacaoDesc()
                : repository.findByStatusOrderByDataSolicitacaoDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Troca> listarDoCliente(Long clienteId) {
        return repository.findByClienteIdOrderByDataSolicitacaoDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public Troca buscar(Long trocaId) {
        return repository.findById(trocaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido de troca não encontrado: id=" + trocaId));
    }

    /** RN0041 — troca de toda a compra: um pedido de troca com todos os itens e a compra fica EM TROCA. */
    private Troca trocarCompraInteira(Pedido pedido) {
        if (repository.existsByPedidoId(pedido.getId())) {
            throw new RegraNegocioException("RN0041",
                    "Já existe troca solicitada para itens desta compra; solicite a troca dos itens restantes individualmente.");
        }
        Troca troca = new Troca(gerarCodigo(), pedido, TipoTroca.COMPRA);
        pedido.getItens().forEach(i -> troca.adicionarItem(i, i.getQuantidade()));
        pedido.entrarEmTroca();
        return troca;
    }

    /** RN0041 — cada item selecionado gera o seu próprio pedido de troca. */
    private List<Troca> trocarItens(Pedido pedido, List<ItemTrocaRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("RF0040", "Selecione ao menos um item para trocar ou escolha trocar a compra inteira.");
        }
        List<Troca> trocas = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (ItemTrocaRequest req : itens) {
            PedidoItem item = pedido.getItens().stream().filter(i -> i.getId().equals(req.pedidoItemId())).findFirst()
                    .orElseThrow(() -> new RegraNegocioException("RF0040", "O item informado não pertence a este pedido."));
            if (!vistos.add(item.getId())) {
                throw new RegraNegocioException("RF0040", "O item " + item.getTitulo() + " foi informado mais de uma vez.");
            }
            long jaEmTroca = trocaItemRepository.somarQuantidadeEmTroca(item.getId());
            long disponivel = item.getQuantidade() - jaEmTroca;
            if (req.quantidade() > disponivel) {
                throw new RegraNegocioException("RF0040", "Quantidade a trocar de " + item.getTitulo() + " ("
                        + req.quantidade() + ") maior que a disponível para troca (" + disponivel + ").");
            }
            Troca troca = new Troca(gerarCodigo(), pedido, TipoTroca.ITEM);
            troca.adicionarItem(item, req.quantidade());
            trocas.add(troca);
        }
        return trocas;
    }

    private void registrarStatusTroca(Troca troca) {
        auditoria.registrar("Troca", troca.getId(), "UPDATE", "admin",
                "Troca " + troca.getCodigo() + " alterada para " + troca.getStatus().getDescricao()
                        + (troca.getCupomGerado() != null ? ", cupom de troca " + troca.getCupomGerado() : ""));
    }

    private void registrarStatusPedido(Pedido pedido, String usuario) {
        auditoria.registrar("Pedido", pedido.getId(), "UPDATE", usuario,
                "Compra " + pedido.getCodigo() + " alterada para " + pedido.getStatus().getDescricao());
    }

    private String gerarCodigo() {
        return "TRC-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String formatar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
