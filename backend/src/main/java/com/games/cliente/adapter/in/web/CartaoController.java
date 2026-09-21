package com.games.cliente.adapter.in.web;

import com.games.cliente.adapter.in.web.dto.CartaoRequest;
import com.games.cliente.adapter.in.web.dto.CartaoResponse;
import com.games.cliente.application.CartaoService;
import com.games.cliente.domain.Cartao;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF0027 — gestão dos cartões de crédito do cliente. */
@RestController
@RequestMapping("/api/clientes/{clienteId}/cartoes")
public class CartaoController {

    private final CartaoService service;

    public CartaoController(CartaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CartaoResponse> cadastrar(@PathVariable Long clienteId, @Valid @RequestBody CartaoRequest req) {
        Cartao cartao = service.cadastrar(clienteId, req);
        return ResponseEntity.status(201).body(CartaoResponse.from(cartao));
    }

    @GetMapping
    public ResponseEntity<List<CartaoResponse>> listar(@PathVariable Long clienteId) {
        List<CartaoResponse> resultado = service.listar(clienteId).stream().map(CartaoResponse::from).toList();
        return ResponseEntity.ok(resultado);
    }

    @DeleteMapping("/{cartaoId}")
    public ResponseEntity<Void> remover(@PathVariable Long clienteId, @PathVariable Long cartaoId) {
        service.remover(clienteId, cartaoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{cartaoId}/preferencial")
    public ResponseEntity<CartaoResponse> marcarComoPreferencial(@PathVariable Long clienteId, @PathVariable Long cartaoId) {
        Cartao cartao = service.marcarComoPreferencial(clienteId, cartaoId);
        return ResponseEntity.ok(CartaoResponse.from(cartao));
    }
}
