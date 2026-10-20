package com.games.cliente.adapter.in.web.dto.response;

import com.games.cliente.domain.entity.Cartao;
import com.games.cliente.domain.enums.Bandeira;

/** Nunca inclui o número completo nem o código de segurança na resposta da API. */
public record CartaoResponse(
        Long id, Long clienteId, String numeroMascarado, String nomeImpresso,
        Bandeira bandeira, boolean preferencial
) {
    public static CartaoResponse from(Cartao c) {
        return new CartaoResponse(c.getId(), c.getClienteId(), c.getNumeroMascarado(),
                c.getNomeImpresso(), c.getBandeira(), c.isPreferencial());
    }
}
