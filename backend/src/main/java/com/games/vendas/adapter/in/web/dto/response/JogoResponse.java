package com.games.vendas.adapter.in.web.dto.response;

import com.games.vendas.application.JogoService.JogoComDisponibilidade;
import com.games.vendas.domain.entity.Jogo;

import java.math.BigDecimal;

public record JogoResponse(
        Long id,
        String titulo,
        String plataforma,
        String genero,
        String desenvolvedora,
        String distribuidora,
        String classificacaoIndicativa,
        Integer ano,
        String codigoBarras,
        BigDecimal preco,
        BigDecimal pesoKg,
        int estoque,
        int disponivel
) {
    public static JogoResponse from(JogoComDisponibilidade d) {
        Jogo j = d.jogo();
        return new JogoResponse(j.getId(), j.getTitulo(), j.getPlataforma(), j.getGenero(), j.getDesenvolvedora(),
                j.getDistribuidora(), j.getClassificacaoIndicativa(), j.getAno(), j.getCodigoBarras(),
                j.getPreco(), j.getPesoKg(), j.getEstoque(), d.disponivel());
    }
}
