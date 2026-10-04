package com.games.vendas.adapter.in.web;

import com.games.vendas.adapter.in.web.dto.AjusteEstoqueRequest;
import com.games.vendas.adapter.in.web.dto.JogoRequest;
import com.games.vendas.adapter.in.web.dto.JogoResponse;
import com.games.vendas.application.EstoqueService;
import com.games.vendas.application.JogoService;
import com.games.vendas.application.JogoService.JogoComDisponibilidade;
import com.games.vendas.domain.entity.Jogo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catálogo da loja e visão administrativa do estoque. */
@RestController
@RequestMapping("/api/jogos")
public class JogoController {

    private final JogoService service;
    private final EstoqueService estoqueService;

    public JogoController(JogoService service, EstoqueService estoqueService) {
        this.service = service;
        this.estoqueService = estoqueService;
    }

    /** Catálogo com a disponibilidade para o cliente informado (RN0031 / RN0044). */
    @GetMapping
    public ResponseEntity<List<JogoResponse>> catalogo(@RequestParam(required = false) Long clienteId) {
        return ResponseEntity.ok(service.listarCatalogo(clienteId).stream().map(JogoResponse::from).toList());
    }

    /** Estoque físico e disponível de todos os jogos (administrador). */
    @GetMapping("/estoque")
    public ResponseEntity<List<JogoResponse>> estoque() {
        return ResponseEntity.ok(service.listarEstoque().stream().map(JogoResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<JogoResponse> cadastrar(@Valid @RequestBody JogoRequest req) {
        Jogo jogo = service.cadastrar(req);
        return ResponseEntity.status(201).body(JogoResponse.from(new JogoComDisponibilidade(jogo, jogo.getEstoque())));
    }

    @PatchMapping("/{id}/estoque")
    public ResponseEntity<JogoResponse> ajustarEstoque(@PathVariable Long id, @Valid @RequestBody AjusteEstoqueRequest req) {
        Jogo jogo = service.ajustarEstoque(id, req.estoque());
        return ResponseEntity.ok(JogoResponse.from(new JogoComDisponibilidade(jogo, estoqueService.disponivelGeral(jogo))));
    }
}
