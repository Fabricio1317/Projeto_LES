package com.games.vendas.adapter.in.web.controller;

import com.games.vendas.adapter.in.web.dto.request.ConfirmarRecebimentoRequest;
import com.games.vendas.adapter.in.web.dto.request.SolicitarTrocaRequest;
import com.games.vendas.adapter.in.web.dto.response.TrocaResponse;
import com.games.vendas.application.service.TrocaService;
import com.games.vendas.domain.enums.StatusTroca;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Troca e devolução: solicitação pelo cliente (RF0040) e visualização (RF0042),
 * autorização (RF0041) e confirmação do recebimento (RF0043) pelo administrador.
 */
@RestController
@RequestMapping("/api")
public class TrocaController {

    private final TrocaService service;

    public TrocaController(TrocaService service) {
        this.service = service;
    }

    @PostMapping("/clientes/{clienteId}/pedidos/{pedidoId}/trocas")
    public ResponseEntity<List<TrocaResponse>> solicitar(@PathVariable Long clienteId, @PathVariable Long pedidoId,
                                                         @Valid @RequestBody SolicitarTrocaRequest req) {
        return ResponseEntity.status(201).body(service.solicitar(clienteId, pedidoId, req).stream()
                .map(TrocaResponse::from).toList());
    }

    @GetMapping("/clientes/{clienteId}/trocas")
    public ResponseEntity<List<TrocaResponse>> doCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(service.listarDoCliente(clienteId).stream().map(TrocaResponse::from).toList());
    }

    @GetMapping("/trocas")
    public ResponseEntity<List<TrocaResponse>> listar(@RequestParam(required = false) StatusTroca status) {
        return ResponseEntity.ok(service.listar(status).stream().map(TrocaResponse::from).toList());
    }

    @PatchMapping("/trocas/{id}/autorizar")
    public ResponseEntity<TrocaResponse> autorizar(@PathVariable Long id) {
        return ResponseEntity.ok(TrocaResponse.from(service.autorizar(id)));
    }

    @PatchMapping("/trocas/{id}/confirmar-recebimento")
    public ResponseEntity<TrocaResponse> confirmarRecebimento(@PathVariable Long id,
                                                              @RequestBody ConfirmarRecebimentoRequest req) {
        return ResponseEntity.ok(TrocaResponse.from(service.confirmarRecebimento(id, req.retornarEstoque())));
    }
}
