package com.games.vendas.adapter.in.web;

import com.games.vendas.adapter.in.web.dto.response.CarrinhoResponse;
import com.games.vendas.adapter.in.web.dto.request.FreteRequest;
import com.games.vendas.adapter.in.web.dto.response.FreteResponse;
import com.games.vendas.adapter.in.web.dto.request.ItemCarrinhoRequest;
import com.games.vendas.adapter.in.web.dto.request.QuantidadeRequest;
import com.games.vendas.application.CarrinhoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** RF0031 / RF0032 / RF0034 — carrinho de compras do cliente. */
@RestController
@RequestMapping("/api/clientes/{clienteId}/carrinho")
public class CarrinhoController {

    private final CarrinhoService service;

    public CarrinhoController(CarrinhoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CarrinhoResponse> visualizar(@PathVariable Long clienteId) {
        return ResponseEntity.ok(CarrinhoResponse.from(service.obter(clienteId)));
    }

    @PostMapping("/itens")
    public ResponseEntity<CarrinhoResponse> adicionar(@PathVariable Long clienteId, @Valid @RequestBody ItemCarrinhoRequest req) {
        return ResponseEntity.status(201).body(CarrinhoResponse.from(service.adicionar(clienteId, req.jogoId(), req.quantidade())));
    }

    @PutMapping("/itens/{itemId}")
    public ResponseEntity<CarrinhoResponse> alterarQuantidade(@PathVariable Long clienteId, @PathVariable Long itemId,
                                                              @Valid @RequestBody QuantidadeRequest req) {
        return ResponseEntity.ok(CarrinhoResponse.from(service.alterarQuantidade(clienteId, itemId, req.quantidade())));
    }

    @DeleteMapping("/itens/{itemId}")
    public ResponseEntity<CarrinhoResponse> remover(@PathVariable Long clienteId, @PathVariable Long itemId) {
        return ResponseEntity.ok(CarrinhoResponse.from(service.remover(clienteId, itemId)));
    }

    @PostMapping("/frete")
    public ResponseEntity<FreteResponse> calcularFrete(@PathVariable Long clienteId, @Valid @RequestBody FreteRequest req) {
        String uf = req.estado().toUpperCase();
        return ResponseEntity.ok(new FreteResponse(uf, service.calcularFrete(clienteId, uf)));
    }
}
