package com.games.vendas.adapter.in.web.dto.response;

import com.games.vendas.application.service.AnaliseVendasService.HistoricoVendas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RF0055 / RNF0043 — histórico de vendas para o gráfico de linhas: {@code meses} é o eixo horizontal
 * (mês/ano) e cada série traz um valor em reais por mês, na mesma ordem.
 */
public record HistoricoVendasResponse(LocalDate inicio, LocalDate fim, List<Mes> meses, List<Serie> series) {

    public record Mes(String chave, String rotulo) {}

    public record Serie(String categoria, List<BigDecimal> valores, BigDecimal total) {}

    private static final DateTimeFormatter ROTULO = DateTimeFormatter.ofPattern("MM/yyyy");

    public static HistoricoVendasResponse from(HistoricoVendas h) {
        List<Mes> meses = h.meses().stream().map(m -> new Mes(m.toString(), m.format(ROTULO))).toList();
        List<Serie> series = h.series().stream().map(s -> new Serie(s.categoria(), s.valores(), s.total())).toList();
        return new HistoricoVendasResponse(h.inicio(), h.fim(), meses, series);
    }
}
