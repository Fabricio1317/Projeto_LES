package com.games.cliente.adapter.in.web;

import com.games.cliente.adapter.in.web.dto.EnderecoRequest;
import com.games.cliente.adapter.in.web.dto.EnderecoResponse;
import com.games.cliente.application.EnderecoService;
import com.games.cliente.domain.entity.Endereco;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF0026 — gestão dos endereços de cobrança/entrega do cliente. */
@RestController
@RequestMapping("/api/clientes/{clienteId}/enderecos")
public class EnderecoController {

    private final EnderecoService service;

    public EnderecoController(EnderecoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EnderecoResponse> cadastrar(@PathVariable Long clienteId, @Valid @RequestBody EnderecoRequest req) {
        Endereco endereco = service.cadastrar(clienteId, req);
        return ResponseEntity.status(201).body(EnderecoResponse.from(endereco));
    }

    @GetMapping
    public ResponseEntity<List<EnderecoResponse>> listar(@PathVariable Long clienteId) {
        List<EnderecoResponse> resultado = service.listar(clienteId).stream().map(EnderecoResponse::from).toList();
        return ResponseEntity.ok(resultado);
    }

    @PutMapping("/{enderecoId}")
    public ResponseEntity<EnderecoResponse> alterar(@PathVariable Long clienteId, @PathVariable Long enderecoId,
                                                     @Valid @RequestBody EnderecoRequest req) {
        Endereco endereco = service.alterar(clienteId, enderecoId, req);
        return ResponseEntity.ok(EnderecoResponse.from(endereco));
    }

    @DeleteMapping("/{enderecoId}")
    public ResponseEntity<Void> remover(@PathVariable Long clienteId, @PathVariable Long enderecoId) {
        service.remover(clienteId, enderecoId);
        return ResponseEntity.noContent().build();
    }
}
