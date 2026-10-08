package com.games.vendas.adapter.in.web;

import com.games.vendas.adapter.in.web.dto.request.FinalizarCompraRequest;
import com.games.vendas.adapter.in.web.dto.response.PedidoResponse;
import com.games.vendas.application.PedidoService;
import com.games.vendas.domain.enums.StatusPedido;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Compras: finalização pelo cliente (RF0033/RF0038), transações do cliente
 * (RF0025) e gestão pelo administrador (RN0037/RN0038, RF0039, RF0040).
 */
@RestController
@RequestMapping("/api")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping("/clientes/{clienteId}/pedidos")
    public ResponseEntity<PedidoResponse> finalizar(@PathVariable Long clienteId, @Valid @RequestBody FinalizarCompraRequest req) {
        return ResponseEntity.status(201).body(PedidoResponse.from(service.finalizar(clienteId, req)));
    }

    /** RF0025 — consulta de transações do cliente. */
    @GetMapping("/clientes/{clienteId}/pedidos")
    public ResponseEntity<List<PedidoResponse>> doCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(service.listarDoCliente(clienteId).stream().map(PedidoResponse::from).toList());
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<PedidoResponse>> listar(@RequestParam(required = false) StatusPedido status) {
        return ResponseEntity.ok(service.listar(status).stream().map(PedidoResponse::from).toList());
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(PedidoResponse.from(service.buscar(id)));
    }

    @PatchMapping("/pedidos/{id}/validar-pagamento")
    public ResponseEntity<PedidoResponse> validarPagamento(@PathVariable Long id) {
        return ResponseEntity.ok(PedidoResponse.from(service.validarPagamento(id)));
    }

    @PatchMapping("/pedidos/{id}/despachar")
    public ResponseEntity<PedidoResponse> despachar(@PathVariable Long id) {
        return ResponseEntity.ok(PedidoResponse.from(service.despachar(id)));
    }

    @PatchMapping("/pedidos/{id}/confirmar-entrega")
    public ResponseEntity<PedidoResponse> confirmarEntrega(@PathVariable Long id) {
        return ResponseEntity.ok(PedidoResponse.from(service.confirmarEntrega(id)));
    }
}
