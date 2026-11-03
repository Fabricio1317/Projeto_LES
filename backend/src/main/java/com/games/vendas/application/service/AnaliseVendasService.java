package com.games.vendas.application.service;

import com.games.cliente.application.exception.RegraNegocioException;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.adapter.out.persistence.repository.PedidoItemRepository;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * RF0055 — histórico de vendas por período, comparando categorias de produto (gêneros dos jogos).
 * Critérios da atividade: somente compras ENTREGUE, valor total vendido em reais (preço × quantidade
 * dos itens; o frete não pertence a uma categoria) agrupado por mês/ano da data da compra.
 */
@Service
public class AnaliseVendasService {

    /** Cada categoria é uma linha do gráfico; o limite acompanha as cores distinguíveis da paleta. */
    public static final int MAXIMO_CATEGORIAS = 8;

    public record SerieCategoria(String categoria, List<BigDecimal> valores, BigDecimal total) {}

    public record HistoricoVendas(LocalDate inicio, LocalDate fim, List<YearMonth> meses, List<SerieCategoria> series) {}

    private final PedidoItemRepository pedidoItemRepository;
    private final JogoRepository jogoRepository;

    public AnaliseVendasService(PedidoItemRepository pedidoItemRepository, JogoRepository jogoRepository) {
        this.pedidoItemRepository = pedidoItemRepository;
        this.jogoRepository = jogoRepository;
    }

    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        return jogoRepository.listarCategorias();
    }

    @Transactional(readOnly = true)
    public HistoricoVendas historicoPorCategoria(LocalDate inicio, LocalDate fim, List<String> categoriasInformadas) {
        if (inicio == null || fim == null) {
            throw new RegraNegocioException("RF0055", "Informe a data de início e a data de fim do período.");
        }
        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("RF0055", "A data de início deve ser anterior ou igual à data de fim.");
        }
        List<String> categorias = new ArrayList<>(new LinkedHashSet<>(
                categoriasInformadas == null ? List.of() : categoriasInformadas.stream().filter(c -> !c.isBlank()).toList()));
        if (categorias.isEmpty()) {
            throw new RegraNegocioException("RF0055", "Selecione ao menos uma categoria para a consulta.");
        }
        if (categorias.size() > MAXIMO_CATEGORIAS) {
            throw new RegraNegocioException("RF0055", "Selecione no máximo " + MAXIMO_CATEGORIAS + " categorias para comparar.");
        }

        List<YearMonth> meses = new ArrayList<>();
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
            meses.add(m);
        }

        Map<String, Map<YearMonth, BigDecimal>> vendas = new HashMap<>();
        for (Object[] linha : pedidoItemRepository.somarVendasPorCategoriaEMes(StatusPedido.ENTREGUE,
                inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay(), categorias)) {
            YearMonth mes = YearMonth.of(((Number) linha[1]).intValue(), ((Number) linha[2]).intValue());
            vendas.computeIfAbsent((String) linha[0], c -> new HashMap<>()).put(mes, new BigDecimal(linha[3].toString()));
        }

        List<SerieCategoria> series = categorias.stream().map(categoria -> {
            Map<YearMonth, BigDecimal> porMes = vendas.getOrDefault(categoria, Map.of());
            List<BigDecimal> valores = meses.stream().map(m -> porMes.getOrDefault(m, BigDecimal.ZERO)).toList();
            return new SerieCategoria(categoria, valores, valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }).toList();
        return new HistoricoVendas(inicio, fim, meses, series);
    }
}
