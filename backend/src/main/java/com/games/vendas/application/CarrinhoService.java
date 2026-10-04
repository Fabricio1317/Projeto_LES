package com.games.vendas.application;

import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.application.AuditoriaService;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cliente;
import com.games.vendas.adapter.out.persistence.CarrinhoRepository;
import com.games.vendas.domain.entity.Carrinho;
import com.games.vendas.domain.entity.CarrinhoItem;
import com.games.vendas.domain.entity.Jogo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF0031 — adicionar, alterar, excluir e visualizar itens do carrinho.
 * RF0032 — quantidade editável ao adicionar e na visualização do carrinho.
 * RN0031 — não permite adicionar item sem estoque nem acima do disponível.
 * RN0032 — ao revisar o carrinho, ajusta quantidades e remove itens que
 *          ficaram indisponíveis, notificando o cliente.
 * RN0044 — itens bloqueados até o prazo parametrizado (relativo ao último
 *          item incluído); o cliente é notificado 5 minutos antes de expirar.
 * RN0045 — ao expirar, os itens são desbloqueados e retirados do carrinho.
 * RNF0042 — itens retirados por prazo seguem listados e impedem a compra
 *          até serem adicionados novamente ou descartados.
 */
@Service
public class CarrinhoService {

    public static final long SEGUNDOS_ALERTA_EXPIRACAO = 300;

    public record CarrinhoView(
            Long clienteId,
            List<CarrinhoItem> itensAtivos,
            Map<Long, Integer> disponivelPorJogo,
            List<CarrinhoItem> itensRemovidos,
            List<String> avisos,
            BigDecimal subtotal,
            LocalDateTime expiraEm,
            long segundosRestantes,
            long prazoBloqueioSegundos,
            boolean alertaExpiracao,
            boolean podeComprar) {}

    private final CarrinhoRepository repository;
    private final JogoService jogoService;
    private final EstoqueService estoqueService;
    private final ParametroService parametroService;
    private final FreteService freteService;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public CarrinhoService(CarrinhoRepository repository, JogoService jogoService, EstoqueService estoqueService,
                           ParametroService parametroService, FreteService freteService,
                           ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.repository = repository;
        this.jogoService = jogoService;
        this.estoqueService = estoqueService;
        this.parametroService = parametroService;
        this.freteService = freteService;
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    /** RF0031 — visualizar o carrinho (revisando prazo e estoque antes). */
    @Transactional
    public CarrinhoView obter(Long clienteId) {
        validarClienteAtivo(clienteId);
        return repository.findByClienteId(clienteId)
                .map(c -> montarView(c, revisar(c)))
                .orElseGet(() -> new CarrinhoView(clienteId, List.of(), Map.of(), List.of(), List.of(),
                        BigDecimal.ZERO, null, 0, parametroService.prazoBloqueioCarrinhoSegundos(), false, false));
    }

    /** RF0031 / RF0032 / RN0031 / RN0044 — adiciona o jogo com a quantidade escolhida. */
    @Transactional
    public CarrinhoView adicionar(Long clienteId, Long jogoId, int quantidade) {
        validarClienteAtivo(clienteId);
        Jogo jogo = jogoService.buscar(jogoId);
        if (!jogo.isAtivo()) {
            throw new RegraNegocioException("RN0031", "O jogo " + jogo.getTitulo() + " não está disponível para venda.");
        }
        Carrinho carrinho = repository.findByClienteId(clienteId).orElseGet(() -> repository.save(new Carrinho(clienteId)));
        List<String> avisos = revisar(carrinho);

        CarrinhoItem existente = carrinho.buscarItemDoJogo(jogoId).orElse(null);
        int jaNoCarrinho = existente != null && existente.isAtivo() ? existente.getQuantidade() : 0;
        int disponivel = estoqueService.disponivelPara(jogo, clienteId);
        validarQuantidade(jogo, jaNoCarrinho + quantidade, disponivel);

        if (existente == null) {
            carrinho.adicionarItem(jogo, quantidade);
        } else if (existente.isAtivo()) {
            existente.alterarQuantidade(jaNoCarrinho + quantidade);
        } else {
            existente.reativar(quantidade);
        }
        carrinho.renovarBloqueio(LocalDateTime.now().plusSeconds(parametroService.prazoBloqueioCarrinhoSegundos()));
        carrinho = repository.save(carrinho);

        auditoria.registrar("CarrinhoItem", carrinho.getId(), "INSERT", "cliente-" + clienteId,
                "Adicionado ao carrinho: " + jogo.getTitulo() + " (quantidade " + quantidade + ")");
        return montarView(carrinho, avisos);
    }

    /** RF0032 / RN0031 — altera a quantidade de um item já no carrinho. */
    @Transactional
    public CarrinhoView alterarQuantidade(Long clienteId, Long itemId, int quantidade) {
        Carrinho carrinho = buscarCarrinho(clienteId);
        List<String> avisos = revisar(carrinho);
        CarrinhoItem item = buscarItem(carrinho, itemId);
        if (!item.isAtivo()) {
            throw new RegraNegocioException("RNF0042",
                    "Este item foi retirado do carrinho por prazo expirado. Adicione-o novamente.");
        }
        validarQuantidade(item.getJogo(), quantidade, estoqueService.disponivelPara(item.getJogo(), clienteId));
        item.alterarQuantidade(quantidade);
        carrinho = repository.save(carrinho);

        auditoria.registrar("CarrinhoItem", item.getId(), "UPDATE", "cliente-" + clienteId,
                "Quantidade de " + item.getJogo().getTitulo() + " alterada para " + quantidade);
        return montarView(carrinho, avisos);
    }

    /** RF0031 — exclui um item (ativo ou retirado por prazo) do carrinho. */
    @Transactional
    public CarrinhoView remover(Long clienteId, Long itemId) {
        Carrinho carrinho = buscarCarrinho(clienteId);
        carrinho.removerItem(buscarItem(carrinho, itemId));
        carrinho = repository.save(carrinho);
        return montarView(carrinho, revisar(carrinho));
    }

    /** RF0034 — frete dos itens do carrinho para a UF informada. */
    @Transactional(readOnly = true)
    public BigDecimal calcularFrete(Long clienteId, String estado) {
        Carrinho carrinho = buscarCarrinho(clienteId);
        return freteService.calcular(pesoTotal(carrinho.getItensAtivos()), estado);
    }

    /**
     * Usado na finalização da compra: revisa o carrinho e recusa a compra se o
     * prazo expirou (RN0044), se o estoque mudou (RN0032) ou se há itens
     * retirados por prazo pendentes (RNF0042).
     */
    @Transactional
    public Carrinho revisarParaCompra(Long clienteId) {
        Carrinho carrinho = repository.findByClienteId(clienteId)
                .orElseThrow(() -> new RegraNegocioException("CARRINHO_VAZIO", "O carrinho está vazio."));
        List<String> avisos = revisar(carrinho);
        if (!avisos.isEmpty()) {
            String codigo = avisos.get(0).startsWith("RN0044") ? "RN0044" : "RN0032";
            throw new RegraNegocioException(codigo, String.join(" ", avisos) + " Revise o carrinho antes de finalizar a compra.");
        }
        if (!carrinho.getItensRemovidos().isEmpty()) {
            throw new RegraNegocioException("RNF0042",
                    "Há itens retirados do carrinho por prazo expirado. Adicione-os novamente ou descarte-os antes de comprar.");
        }
        if (carrinho.getItensAtivos().isEmpty()) {
            throw new RegraNegocioException("CARRINHO_VAZIO", "O carrinho está vazio.");
        }
        return carrinho;
    }

    public BigDecimal pesoTotal(List<CarrinhoItem> itens) {
        return itens.stream()
                .map(i -> i.getJogo().getPesoKg().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** RN0044/RN0045 (expiração) e RN0032 (mudança de estoque). Retorna as notificações geradas. */
    private List<String> revisar(Carrinho carrinho) {
        List<String> avisos = new ArrayList<>();
        Long clienteId = carrinho.getClienteId();

        if (carrinho.isExpirado(LocalDateTime.now())) {
            List<CarrinhoItem> expirados = carrinho.expirarBloqueio();
            avisos.add("RN0044 — O prazo de bloqueio expirou: " + expirados.size()
                    + " item(ns) foram retirados do carrinho e liberados para outros clientes.");
            auditoria.registrar("CarrinhoItem", carrinho.getId(), "UPDATE", "cliente-" + clienteId,
                    "Prazo de bloqueio expirado: " + expirados.size() + " item(ns) retirados do carrinho");
        }

        for (CarrinhoItem item : carrinho.getItensAtivos()) {
            int disponivel = estoqueService.disponivelPara(item.getJogo(), clienteId);
            String titulo = item.getJogo().getTitulo();
            if (disponivel <= 0) {
                carrinho.removerItem(item);
                avisos.add("RN0032 — \"" + titulo + "\" ficou indisponível no estoque e foi removido do carrinho.");
            } else if (item.getQuantidade() > disponivel) {
                item.alterarQuantidade(disponivel);
                avisos.add("RN0032 — A disponibilidade de \"" + titulo + "\" mudou: quantidade ajustada para "
                        + disponivel + ".");
            }
        }
        if (avisos.stream().anyMatch(a -> a.startsWith("RN0032"))) {
            auditoria.registrar("CarrinhoItem", carrinho.getId(), "UPDATE", "cliente-" + clienteId,
                    "Carrinho ajustado por mudança de estoque");
        }
        repository.save(carrinho);
        return avisos;
    }

    private CarrinhoView montarView(Carrinho carrinho, List<String> avisos) {
        List<CarrinhoItem> ativos = carrinho.getItensAtivos();
        List<CarrinhoItem> removidos = carrinho.getItensRemovidos();
        Map<Long, Integer> disponivel = new HashMap<>();
        ativos.forEach(i -> disponivel.put(i.getJogo().getId(),
                estoqueService.disponivelPara(i.getJogo(), carrinho.getClienteId())));

        BigDecimal subtotal = ativos.stream().map(CarrinhoItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long segundosRestantes = ativos.isEmpty() || carrinho.getExpiraEm() == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), carrinho.getExpiraEm()).getSeconds());
        boolean alerta = !ativos.isEmpty() && segundosRestantes <= SEGUNDOS_ALERTA_EXPIRACAO;
        boolean podeComprar = !ativos.isEmpty() && removidos.isEmpty();

        return new CarrinhoView(carrinho.getClienteId(), ativos, disponivel, removidos, avisos, subtotal,
                ativos.isEmpty() ? null : carrinho.getExpiraEm(), segundosRestantes,
                parametroService.prazoBloqueioCarrinhoSegundos(), alerta, podeComprar);
    }

    private void validarQuantidade(Jogo jogo, int quantidadeDesejada, int disponivel) {
        if (disponivel <= 0) {
            throw new RegraNegocioException("RN0031", "O jogo " + jogo.getTitulo() + " não possui estoque disponível.");
        }
        if (quantidadeDesejada > disponivel) {
            throw new RegraNegocioException("RN0031", "Quantidade solicitada (" + quantidadeDesejada
                    + ") maior que a disponível em estoque (" + disponivel + ") para " + jogo.getTitulo() + ".");
        }
    }

    private void validarClienteAtivo(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
        if (!cliente.isAtivo()) {
            throw new RegraNegocioException("CLIENTE_INATIVO", "Clientes inativos não podem realizar compras.");
        }
    }

    private Carrinho buscarCarrinho(Long clienteId) {
        return repository.findByClienteId(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Carrinho não encontrado para o cliente " + clienteId));
    }

    private CarrinhoItem buscarItem(Carrinho carrinho, Long itemId) {
        return carrinho.buscarItem(itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado no carrinho: id=" + itemId));
    }
}
