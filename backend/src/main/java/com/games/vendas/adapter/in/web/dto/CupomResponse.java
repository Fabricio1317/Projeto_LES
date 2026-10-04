package com.games.vendas.adapter.in.web.dto;

import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.enums.TipoCupom;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CupomResponse(Long id, String codigo, TipoCupom tipo, BigDecimal valor, Long clienteId,
                            LocalDate validade, boolean utilizado) {
    public static CupomResponse from(Cupom c) {
        return new CupomResponse(c.getId(), c.getCodigo(), c.getTipo(), c.getValor(), c.getClienteId(),
                c.getValidade(), c.isUtilizado());
    }
}
