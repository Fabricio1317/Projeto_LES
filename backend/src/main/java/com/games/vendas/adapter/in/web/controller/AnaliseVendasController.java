package com.games.vendas.adapter.in.web.controller;

import com.games.vendas.adapter.in.web.dto.response.HistoricoVendasResponse;
import com.games.vendas.application.service.AnaliseVendasService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Análise do histórico de vendas por categoria (RF0055 / RNF0043), restrita ao perfil administrador
 * pelo {@code PerfilAdministradorInterceptor}.
 */
@RestController
@RequestMapping("/api/analises")
public class AnaliseVendasController {

    private final AnaliseVendasService service;

    public AnaliseVendasController(AnaliseVendasService service) {
        this.service = service;
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<String>> categorias() {
        return ResponseEntity.ok(service.listarCategorias());
    }

    /** Ex.: /api/analises/vendas-por-categoria?inicio=2026-01-01&fim=2026-06-30&categorias=RPG&categorias=Ação */
    @GetMapping("/vendas-por-categoria")
    public ResponseEntity<HistoricoVendasResponse> vendasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<String> categorias) {
        return ResponseEntity.ok(HistoricoVendasResponse.from(service.historicoPorCategoria(inicio, fim, categorias)));
    }
}
