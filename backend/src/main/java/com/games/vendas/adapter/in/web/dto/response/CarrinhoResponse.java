package com.games.vendas.adapter.in.web.dto.response;

import com.games.vendas.application.CarrinhoService.CarrinhoView;
import com.games.vendas.domain.entity.CarrinhoItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF0031 — visualização do carrinho.
 * {@code avisos}: notificações da RN0032 (estoque) e RN0044 (prazo expirado).
 * {@code alertaExpiracao}: faltam 5 minutos ou menos para o bloqueio expirar (RN0044).
 * {@code itensRemovidos} / {@code podeComprar}: RNF0042.
 */
public record CarrinhoResponse(
        Long clienteId,
        List<Item> itens,
        List<ItemRemovido> itensRemovidos,
        List<String> avisos,
        BigDecimal subtotal,
        LocalDateTime expiraEm,
        long segundosRestantes,
        long prazoBloqueioSegundos,
        boolean alertaExpiracao,
        boolean podeComprar
) {
    public record Item(Long id, Long jogoId, String titulo, BigDecimal precoUnitario, int quantidade,
                       BigDecimal subtotal, int disponivel) {}

    public record ItemRemovido(Long id, Long jogoId, String titulo, int quantidade) {}

    public static CarrinhoResponse from(CarrinhoView v) {
        List<Item> itens = v.itensAtivos().stream().map(i -> new Item(i.getId(), i.getJogo().getId(),
                i.getJogo().getTitulo(), i.getJogo().getPreco(), i.getQuantidade(), i.getSubtotal(),
                v.disponivelPorJogo().getOrDefault(i.getJogo().getId(), 0))).toList();
        List<ItemRemovido> removidos = v.itensRemovidos().stream().map(CarrinhoResponse::removido).toList();
        return new CarrinhoResponse(v.clienteId(), itens, removidos, v.avisos(), v.subtotal(), v.expiraEm(),
                v.segundosRestantes(), v.prazoBloqueioSegundos(), v.alertaExpiracao(), v.podeComprar());
    }

    private static ItemRemovido removido(CarrinhoItem i) {
        return new ItemRemovido(i.getId(), i.getJogo().getId(), i.getJogo().getTitulo(), i.getQuantidade());
    }
}
