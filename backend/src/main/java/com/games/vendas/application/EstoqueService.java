package com.games.vendas.application;

import com.games.vendas.adapter.out.persistence.CarrinhoItemRepository;
import com.games.vendas.adapter.out.persistence.PedidoItemRepository;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.enums.SituacaoItemCarrinho;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Quantidade disponível para venda de um jogo:
 * estoque físico − itens reservados em compras EM PROCESSAMENTO (RN0028)
 * − itens bloqueados em carrinhos de outros clientes ainda no prazo (RN0044).
 */
@Service
public class EstoqueService {

    private static final long NENHUM_CLIENTE = 0L;

    private final CarrinhoItemRepository carrinhoItemRepository;
    private final PedidoItemRepository pedidoItemRepository;

    public EstoqueService(CarrinhoItemRepository carrinhoItemRepository, PedidoItemRepository pedidoItemRepository) {
        this.carrinhoItemRepository = carrinhoItemRepository;
        this.pedidoItemRepository = pedidoItemRepository;
    }

    /** Disponível para o cliente informado: os itens no próprio carrinho dele não contam como bloqueio. */
    @Transactional(readOnly = true)
    public int disponivelPara(Jogo jogo, Long clienteId) {
        long reservado = pedidoItemRepository.somarReservado(jogo.getId(), StatusPedido.EM_PROCESSAMENTO);
        long bloqueado = carrinhoItemRepository.somarBloqueado(jogo.getId(),
                clienteId == null ? NENHUM_CLIENTE : clienteId, SituacaoItemCarrinho.ATIVO, LocalDateTime.now());
        return (int) Math.max(0, jogo.getEstoque() - reservado - bloqueado);
    }

    /** Disponível considerando os bloqueios de todos os carrinhos (visão do administrador). */
    @Transactional(readOnly = true)
    public int disponivelGeral(Jogo jogo) {
        return disponivelPara(jogo, null);
    }
}
