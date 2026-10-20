package com.games.vendas.adapter.in.web.controller;

import com.games.vendas.adapter.in.web.dto.response.NotificacaoResponse;
import com.games.vendas.application.service.NotificacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Notificações do cliente (RNF0046). */
@RestController
@RequestMapping("/api/clientes/{clienteId}/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<NotificacaoResponse>> listar(@PathVariable Long clienteId) {
        return ResponseEntity.ok(service.listar(clienteId).stream().map(NotificacaoResponse::from).toList());
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarComoLidas(@PathVariable Long clienteId) {
        service.marcarTodasComoLidas(clienteId);
        return ResponseEntity.noContent().build();
    }
}
