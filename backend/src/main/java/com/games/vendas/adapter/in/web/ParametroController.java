package com.games.vendas.adapter.in.web;

import com.games.vendas.adapter.in.web.dto.ParametroRequest;
import com.games.vendas.adapter.in.web.dto.ParametroResponse;
import com.games.vendas.application.ParametroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Parâmetros do sistema, como o prazo de bloqueio do carrinho (RN0044). */
@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    private final ParametroService service;

    public ParametroController(ParametroService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ParametroResponse>> listar() {
        return ResponseEntity.ok(service.listar().stream().map(ParametroResponse::from).toList());
    }

    @PutMapping("/{chave}")
    public ResponseEntity<ParametroResponse> alterar(@PathVariable String chave, @Valid @RequestBody ParametroRequest req) {
        return ResponseEntity.ok(ParametroResponse.from(service.alterar(chave, req.valor())));
    }
}
