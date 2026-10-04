package com.games.vendas.application;

import com.games.cliente.adapter.in.web.dto.EnderecoRequest;
import com.games.cliente.adapter.out.persistence.CartaoRepository;
import com.games.cliente.adapter.out.persistence.EnderecoRepository;
import com.games.cliente.application.AuditoriaService;
import com.games.cliente.application.CartaoService;
import com.games.cliente.application.ClienteService;
import com.games.cliente.application.EnderecoService;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cartao;
import com.games.cliente.domain.entity.Endereco;
import com.games.cliente.domain.enums.TipoEndereco;
import com.games.vendas.adapter.in.web.dto.FinalizarCompraRequest;
import com.games.vendas.adapter.in.web.dto.NovoEnderecoEntregaRequest;
import com.games.vendas.adapter.in.web.dto.PagamentoCartaoRequest;
import com.games.vendas.adapter.out.persistence.CarrinhoRepository;
import com.games.vendas.adapter.out.persistence.PedidoRepository;
import com.games.vendas.domain.entity.*;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;

/**
 * Fluxo de venda, da finalização à entrega:
 * RF0033 / RF0038 — realizar e finalizar a compra (status EM PROCESSAMENTO).
 * RF0034 — frete pelo peso dos itens e pela UF de entrega.
 * RF0035 — endereço de entrega cadastrado ou novo, opcionalmente incorporado ao perfil.
 * RF0036 / RF0037 — cartões (cadastrados ou novos) e cupons de troca/promocionais.
 * RN0034 — vários cartões, com mínimo de R$ 10,00 por cartão.
 * RN0035 — com cupons, o restante pode ser pago no cartão mesmo abaixo de R$ 10,00.
 * RN0036 — cupons desnecessários são recusados; o excedente vira cupom de troca na finalização.
 * RN0037 / RN0038 — validação do pagamento: APROVADA ou REPROVADA (a reprovação cancela o cupom de troca emitido).
 * RN0028 / RF0053 — baixa no estoque apenas quando a compra é efetivada.
 * RN0039 / RF0039 — despachar compras aprovadas (EM TRANSPORTE).
 * RN0040 / RF0040 — confirmar a entrega (ENTREGUE).
 * RN0027 — ranking do cliente pelo perfil de compra.
 * RF0025 — consulta das transações do cliente.
 */
@Service
public class PedidoService {

    public static final BigDecimal VALOR_MINIMO_CARTAO = new BigDecimal("10.00");
    private static final BigDecimal VALOR_POR_PONTO_RANKING = new BigDecimal("100");

    private final PedidoRepository repository;
    private final CarrinhoService carrinhoService;
    private final CarrinhoRepository carrinhoRepository;
    private final CupomService cupomService;
    private final FreteService freteService;
    private final EnderecoRepository enderecoRepository;
    private final EnderecoService enderecoService;
    private final CartaoRepository cartaoRepository;
    private final CartaoService cartaoService;
    private final ClienteService clienteService;
    private final OperadoraCartaoSimulada operadora;
    private final AuditoriaService auditoria;

    public PedidoService(PedidoRepository repository, CarrinhoService carrinhoService, CarrinhoRepository carrinhoRepository,
                         CupomService cupomService, FreteService freteService, EnderecoRepository enderecoRepository,
                         EnderecoService enderecoService, CartaoRepository cartaoRepository, CartaoService cartaoService,
                         ClienteService clienteService, OperadoraCartaoSimulada operadora, AuditoriaService auditoria) {
        this.repository = repository;
        this.carrinhoService = carrinhoService;
        this.carrinhoRepository = carrinhoRepository;
        this.cupomService = cupomService;
        this.freteService = freteService;
        this.enderecoRepository = enderecoRepository;
        this.enderecoService = enderecoService;
        this.cartaoRepository = cartaoRepository;
        this.cartaoService = cartaoService;
        this.clienteService = clienteService;
        this.operadora = operadora;
        this.auditoria = auditoria;
    }

    /** RF0033 / RF0038 — finaliza a compra a partir do carrinho; o pedido nasce EM PROCESSAMENTO. */
    @Transactional
    public Pedido finalizar(Long clienteId, FinalizarCompraRequest req) {
        Carrinho carrinho = carrinhoService.revisarParaCompra(clienteId);
        List<CarrinhoItem> itens = carrinho.getItensAtivos();

        EnderecoEntrega endereco = resolverEndereco(clienteId, req);
        BigDecimal frete = freteService.calcular(carrinhoService.pesoTotal(itens), endereco.getEstado());

        Pedido pedido = new Pedido(gerarCodigo(), clienteId, endereco, frete);
        itens.forEach(i -> pedido.adicionarItem(i.getJogo(), i.getQuantidade()));
        BigDecimal total = pedido.getTotal();

        List<Cupom> cupons = cupomService.resolverParaCompra(clienteId, req.cupons());
        BigDecimal somaCupons = cupons.stream().map(Cupom::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        validarCuponsNecessarios(cupons, somaCupons, total);
        BigDecimal valorCupons = somaCupons.min(total);
        BigDecimal troco = somaCupons.subtract(total).max(BigDecimal.ZERO);
        BigDecimal restante = total.subtract(valorCupons);

        List<PagamentoCartaoRequest> cartoes = req.cartoes() == null ? List.of() : req.cartoes();
        validarValoresDosCartoes(cartoes, restante, !cupons.isEmpty());
        registrarPagamentosCartao(clienteId, pedido, cartoes);

        cupons.forEach(c -> {
            pedido.adicionarCupom(c);
            if (c.isUsoUnico()) c.marcarUtilizado();
        });
        pedido.registrarCupons(valorCupons, troco);

        Pedido salvo = repository.save(pedido);
        if (troco.signum() > 0) {
            salvo.registrarCupomTroco(cupomService.gerarCupomTroca(clienteId, troco, salvo.getId()).getCodigo());
        }
        carrinho.esvaziarItensAtivos();
        carrinhoRepository.save(carrinho);

        auditoria.registrar("Pedido", salvo.getId(), "INSERT", "cliente-" + clienteId,
                "Compra " + salvo.getCodigo() + " finalizada: total R$ " + salvo.getTotal()
                        + ", status " + salvo.getStatus().getDescricao()
                        + (salvo.getCupomTrocoGerado() != null ? ", cupom de troca " + salvo.getCupomTrocoGerado() : ""));
        return salvo;
    }

    /** RN0037 / RN0038 — valida cupons e cartões; aprova (com baixa no estoque) ou reprova a compra. */
    @Transactional
    public Pedido validarPagamento(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        if (pedido.getStatus() != StatusPedido.EM_PROCESSAMENTO) {
            throw new RegraNegocioException("RN0037", "Somente compras EM PROCESSAMENTO têm a forma de pagamento validada.");
        }

        List<String> problemas = new ArrayList<>();
        pedido.getCupons().stream().map(PedidoCupom::getCupom)
                .filter(c -> c.isVencido(pedido.getDataCompra().toLocalDate()))
                .forEach(c -> problemas.add("Cupom " + c.getCodigo() + " vencido"));
        for (PagamentoCartao pagamento : pedido.getPagamentos()) {
            boolean autorizado = operadora.autorizar(pagamento.getNumero(), pagamento.getValor());
            pagamento.registrarRetornoOperadora(autorizado);
            if (!autorizado) {
                problemas.add("Cartão " + pagamento.getNumeroMascarado() + " recusado pela operadora");
            }
        }
        pedido.getItens().stream()
                .filter(i -> i.getJogo().getEstoque() < i.getQuantidade())
                .forEach(i -> problemas.add("Estoque insuficiente para " + i.getTitulo()));

        if (problemas.isEmpty()) {
            pedido.getItens().forEach(i -> i.getJogo().darBaixa(i.getQuantidade()));
            pedido.aprovar();
        } else {
            pedido.reprovar(String.join("; ", problemas));
            pedido.getCupons().stream().map(PedidoCupom::getCupom).filter(Cupom::isUsoUnico).forEach(Cupom::liberar);
            if (pedido.getCupomTrocoGerado() != null) {
                cupomService.cancelar(pedido.getCupomTrocoGerado());
            }
        }

        Pedido salvo = repository.save(pedido);
        registrarMudancaDeStatus(salvo);
        if (salvo.getStatus() == StatusPedido.APROVADA) {
            atualizarRanking(salvo.getClienteId());
        }
        return salvo;
    }

    /** RF0039 / RN0039 — somente compras aprovadas são despachadas. */
    @Transactional
    public Pedido despachar(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        if (pedido.getStatus() != StatusPedido.APROVADA) {
            throw new RegraNegocioException("RN0039", "Somente compras APROVADAS podem ser despachadas para entrega.");
        }
        pedido.despachar();
        Pedido salvo = repository.save(pedido);
        registrarMudancaDeStatus(salvo);
        return salvo;
    }

    /** RF0040 / RN0040 — somente compras em transporte têm a entrega confirmada. */
    @Transactional
    public Pedido confirmarEntrega(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        if (pedido.getStatus() != StatusPedido.EM_TRANSPORTE) {
            throw new RegraNegocioException("RN0040", "Somente compras EM TRANSPORTE podem ter a entrega confirmada.");
        }
        pedido.confirmarEntrega();
        Pedido salvo = repository.save(pedido);
        registrarMudancaDeStatus(salvo);
        return salvo;
    }

    /** RF0025 — todas as transações (compras) do cliente. */
    @Transactional(readOnly = true)
    public List<Pedido> listarDoCliente(Long clienteId) {
        return repository.findByClienteIdOrderByDataCompraDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar(StatusPedido status) {
        return status == null ? repository.findAllByOrderByDataCompraDesc() : repository.findByStatusOrderByDataCompraDesc(status);
    }

    @Transactional(readOnly = true)
    public Pedido buscar(Long pedidoId) {
        return repository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: id=" + pedidoId));
    }

    /** RF0035 — endereço cadastrado (de entrega) ou novo, opcionalmente incorporado ao perfil. */
    private EnderecoEntrega resolverEndereco(Long clienteId, FinalizarCompraRequest req) {
        boolean temCadastrado = req.enderecoId() != null;
        boolean temNovo = req.novoEndereco() != null;
        if (temCadastrado == temNovo) {
            throw new RegraNegocioException("RF0035", "Selecione um endereço de entrega cadastrado ou informe um novo endereço.");
        }

        if (temCadastrado) {
            Endereco e = enderecoRepository.findById(req.enderecoId())
                    .filter(end -> end.getClienteId().equals(clienteId))
                    .orElseThrow(() -> new RegraNegocioException("RF0035", "Endereço de entrega não encontrado para este cliente."));
            if (e.getTipo() == TipoEndereco.COBRANCA) {
                throw new RegraNegocioException("RF0035", "O endereço selecionado é apenas de cobrança; escolha um endereço de entrega.");
            }
            return new EnderecoEntrega(e.getApelido(), e.getTipoResidencia(), e.getTipoLogradouro(), e.getLogradouro(),
                    e.getNumero(), e.getBairro(), e.getCep(), e.getCidade(), e.getEstado(), e.getPais());
        }

        NovoEnderecoEntregaRequest n = req.novoEndereco();
        if (req.salvarEnderecoNoPerfil()) {
            enderecoService.cadastrar(clienteId, new EnderecoRequest(n.apelido(), TipoEndereco.ENTREGA,
                    n.tipoResidencia(), n.tipoLogradouro(), n.logradouro(), n.numero(), n.bairro(), n.cep(),
                    n.cidade(), n.estado().toUpperCase(), n.pais(), n.observacoes()));
        }
        return new EnderecoEntrega(n.apelido(), n.tipoResidencia(), n.tipoLogradouro(), n.logradouro(), n.numero(),
                n.bairro(), n.cep(), n.cidade(), n.estado().toUpperCase(), n.pais());
    }

    /** RN0036 — não permite cupons desnecessários: sem cada um deles, os demais já cobririam a compra. */
    private void validarCuponsNecessarios(List<Cupom> cupons, BigDecimal somaCupons, BigDecimal total) {
        if (somaCupons.compareTo(total) <= 0) return;
        for (Cupom cupom : cupons) {
            if (somaCupons.subtract(cupom.getValor()).compareTo(total) >= 0) {
                throw new RegraNegocioException("RN0036", "O cupom " + cupom.getCodigo()
                        + " é desnecessário: os demais cupons já cobrem o valor da compra (R$ " + formatar(total) + ").");
            }
        }
    }

    /** RF0036 / RN0034 / RN0035 — valores pagos em cartão. */
    private void validarValoresDosCartoes(List<PagamentoCartaoRequest> cartoes, BigDecimal restante, boolean usouCupons) {
        if (restante.signum() == 0) {
            if (!cartoes.isEmpty()) {
                throw new RegraNegocioException("RF0037", "Os cupons já cobrem o valor total da compra; não informe cartões.");
            }
            return;
        }
        if (cartoes.isEmpty()) {
            throw new RegraNegocioException("RF0036", "Informe ao menos um cartão para pagar o restante de R$ " + formatar(restante) + ".");
        }
        BigDecimal somaCartoes = cartoes.stream().map(PagamentoCartaoRequest::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaCartoes.compareTo(restante) != 0) {
            throw new RegraNegocioException("RF0036", "A soma dos valores dos cartões (R$ " + formatar(somaCartoes)
                    + ") deve ser igual ao valor a pagar (R$ " + formatar(restante) + ").");
        }
        boolean excecaoComCupom = usouCupons && cartoes.size() == 1;
        for (PagamentoCartaoRequest cartao : cartoes) {
            if (cartao.valor().compareTo(VALOR_MINIMO_CARTAO) < 0 && !excecaoComCupom) {
                throw new RegraNegocioException("RN0034", "Cada cartão deve pagar no mínimo R$ 10,00 "
                        + "(só é permitido valor menor quando um único cartão completa o pagamento feito com cupons).");
            }
        }
    }

    /** RF0036 — cartão do perfil ou novo cartão, opcionalmente incorporado ao perfil. */
    private void registrarPagamentosCartao(Long clienteId, Pedido pedido, List<PagamentoCartaoRequest> cartoes) {
        Set<Long> usados = new HashSet<>();
        for (PagamentoCartaoRequest p : cartoes) {
            boolean temCadastrado = p.cartaoId() != null;
            boolean temNovo = p.novoCartao() != null;
            if (temCadastrado == temNovo) {
                throw new RegraNegocioException("RF0036", "Em cada pagamento, informe um cartão cadastrado ou um novo cartão.");
            }

            Cartao cartao = null;
            if (temCadastrado) {
                cartao = cartaoRepository.findById(p.cartaoId())
                        .filter(c -> c.getClienteId().equals(clienteId))
                        .orElseThrow(() -> new RegraNegocioException("RF0036", "Cartão não encontrado para este cliente."));
            } else if (p.salvarNoPerfil()) {
                cartao = cartaoService.cadastrar(clienteId, p.novoCartao());
            }

            if (cartao != null) {
                if (!usados.add(cartao.getId())) {
                    throw new RegraNegocioException("RF0036", "O mesmo cartão foi informado mais de uma vez.");
                }
                pedido.adicionarPagamentoCartao(cartao.getId(), cartao.getNumero(), cartao.getNomeImpresso(),
                        cartao.getBandeira(), p.valor());
            } else {
                pedido.adicionarPagamentoCartao(null, p.novoCartao().numero(), p.novoCartao().nomeImpresso(),
                        p.novoCartao().bandeira(), p.valor());
            }
        }
    }

    /** RN0027 — 1 ponto de ranking a cada R$ 100,00 em compras efetivadas. */
    private void atualizarRanking(Long clienteId) {
        List<StatusPedido> efetivadas = Arrays.stream(StatusPedido.values()).filter(StatusPedido::isEfetivada).toList();
        BigDecimal totalComprado = repository.findByClienteIdAndStatusIn(clienteId, efetivadas).stream()
                .map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int ranking = totalComprado.divide(VALOR_POR_PONTO_RANKING, 0, RoundingMode.DOWN).intValue();
        clienteService.atualizarRanking(clienteId, ranking);
    }

    private void registrarMudancaDeStatus(Pedido pedido) {
        String detalhe = pedido.getMotivoReprovacao() != null && pedido.getStatus() == StatusPedido.REPROVADA
                ? " — " + pedido.getMotivoReprovacao() : "";
        auditoria.registrar("Pedido", pedido.getId(), "UPDATE", "admin",
                "Compra " + pedido.getCodigo() + " alterada para " + pedido.getStatus().getDescricao() + detalhe);
    }

    private String gerarCodigo() {
        return "PED-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String formatar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
