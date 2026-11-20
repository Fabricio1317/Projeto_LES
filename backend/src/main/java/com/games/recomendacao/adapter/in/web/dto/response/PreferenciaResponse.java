package com.games.recomendacao.adapter.in.web.dto.response;

import com.games.recomendacao.domain.entity.PreferenciaCliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PreferenciaResponse(Long clienteId, List<String> generos, List<String> plataformas, BigDecimal orcamentoMaximo,
                                  String observacoes, LocalDateTime atualizadoEm) {

    public static PreferenciaResponse from(PreferenciaCliente p) {
        return new PreferenciaResponse(p.getClienteId(), List.copyOf(p.getGeneros()), List.copyOf(p.getPlataformas()),
                p.getOrcamentoMaximo(), p.getObservacoes(), p.getAtualizadoEm());
    }
}
