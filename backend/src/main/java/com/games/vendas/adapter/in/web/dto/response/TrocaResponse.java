package com.games.vendas.adapter.in.web.dto.response;

import com.games.vendas.domain.entity.Troca;
import com.games.vendas.domain.enums.StatusTroca;
import com.games.vendas.domain.enums.TipoTroca;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TrocaResponse(
        Long id,
        String codigo,
        Long pedidoId,
        String pedidoCodigo,
        Long clienteId,
        TipoTroca tipo,
        String tipoDescricao,
        StatusTroca status,
        String statusDescricao,
        LocalDateTime dataSolicitacao,
        LocalDateTime dataAtualizacao,
        BigDecimal valor,
        Boolean retornouEstoque,
        String cupomGerado,
        List<Item> itens
) {
    public record Item(Long pedidoItemId, String titulo, int quantidade, BigDecimal valor) {}

    public static TrocaResponse from(Troca t) {
        return new TrocaResponse(t.getId(), t.getCodigo(), t.getPedido().getId(), t.getPedido().getCodigo(),
                t.getClienteId(), t.getTipo(), t.getTipo().getDescricao(), t.getStatus(), t.getStatus().getDescricao(),
                t.getDataSolicitacao(), t.getDataAtualizacao(), t.getValor(), t.getRetornouEstoque(), t.getCupomGerado(),
                t.getItens().stream().map(i -> new Item(i.getPedidoItem().getId(), i.getPedidoItem().getTitulo(),
                        i.getQuantidade(), i.getValor())).toList());
    }
}
