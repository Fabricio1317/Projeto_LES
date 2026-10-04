package com.games.vendas.adapter.in.web.dto;

import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.enums.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        String codigo,
        Long clienteId,
        LocalDateTime dataCompra,
        LocalDateTime dataAtualizacao,
        StatusPedido status,
        String statusDescricao,
        String enderecoEntrega,
        BigDecimal subtotal,
        BigDecimal frete,
        BigDecimal total,
        BigDecimal valorCupons,
        BigDecimal valorTroco,
        String cupomTrocoGerado,
        String motivoReprovacao,
        List<Item> itens,
        List<Pagamento> pagamentos,
        List<CupomUsado> cupons
) {
    public record Item(String titulo, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {}

    public record Pagamento(String numeroMascarado, String bandeira, BigDecimal valor, Boolean aprovado) {}

    public record CupomUsado(String codigo, String tipo, BigDecimal valor) {}

    public static PedidoResponse from(Pedido p) {
        return new PedidoResponse(p.getId(), p.getCodigo(), p.getClienteId(), p.getDataCompra(), p.getDataAtualizacao(),
                p.getStatus(), p.getStatus().getDescricao(), p.getEnderecoEntrega().resumo(),
                p.getSubtotal(), p.getFrete(), p.getTotal(), p.getValorCupons(), p.getValorTroco(),
                p.getCupomTrocoGerado(), p.getMotivoReprovacao(),
                p.getItens().stream().map(i -> new Item(i.getTitulo(), i.getQuantidade(), i.getPrecoUnitario(),
                        i.getSubtotal())).toList(),
                p.getPagamentos().stream().map(pg -> new Pagamento(pg.getNumeroMascarado(), pg.getBandeira().name(),
                        pg.getValor(), pg.getAprovado())).toList(),
                p.getCupons().stream().map(pc -> new CupomUsado(pc.getCupom().getCodigo(), pc.getCupom().getTipo().name(),
                        pc.getCupom().getValor())).toList());
    }
}
