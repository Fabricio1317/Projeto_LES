package com.games.cliente.adapter.in.web;

import com.games.cliente.adapter.in.web.dto.LogTransacaoResponse;
import com.games.cliente.application.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RNF0012 — consulta do log de auditoria (data, hora, usuário e dado alterado). */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<LogTransacaoResponse>> listar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) Long entidadeId) {
        List<LogTransacaoResponse> resultado = (entidade != null && entidadeId != null
                ? service.listarPorEntidade(entidade, entidadeId)
                : service.listarTudo())
                .stream().map(LogTransacaoResponse::from).toList();
        return ResponseEntity.ok(resultado);
    }
}
