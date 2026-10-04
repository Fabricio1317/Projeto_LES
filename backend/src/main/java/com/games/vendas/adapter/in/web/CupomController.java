package com.games.vendas.adapter.in.web;

import com.games.vendas.adapter.in.web.dto.CupomRequest;
import com.games.vendas.adapter.in.web.dto.CupomResponse;
import com.games.vendas.application.CupomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cupons de troca e promocionais (RF0036 / RF0037). */
@RestController
@RequestMapping("/api")
public class CupomController {

    private final CupomService service;

    public CupomController(CupomService service) {
        this.service = service;
    }

    @GetMapping("/cupons")
    public ResponseEntity<List<CupomResponse>> listar() {
        return ResponseEntity.ok(service.listarTodos().stream().map(CupomResponse::from).toList());
    }

    @PostMapping("/cupons")
    public ResponseEntity<CupomResponse> cadastrar(@Valid @RequestBody CupomRequest req) {
        return ResponseEntity.status(201).body(CupomResponse.from(service.cadastrar(req)));
    }

    /** Cupons de troca disponíveis para o cliente usar na compra. */
    @GetMapping("/clientes/{clienteId}/cupons")
    public ResponseEntity<List<CupomResponse>> doCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(service.listarTrocaDisponiveis(clienteId).stream().map(CupomResponse::from).toList());
    }

    /** RN0033 / RN0037 — valida o conjunto de cupons que o cliente quer aplicar na compra. */
    @PostMapping("/clientes/{clienteId}/cupons/validar")
    public ResponseEntity<List<CupomResponse>> validar(@PathVariable Long clienteId, @RequestBody List<String> codigos) {
        return ResponseEntity.ok(service.resolverParaCompra(clienteId, codigos).stream().map(CupomResponse::from).toList());
    }
}
