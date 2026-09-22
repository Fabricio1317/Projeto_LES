package com.games.cliente.adapter.in.web;

import com.games.cliente.adapter.in.web.dto.*;
import com.games.cliente.application.ClienteService;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.StatusCliente;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * API REST do módulo de Cliente — CRUD completo:
 * cadastrar (RF0021), consultar (RF0024), alterar (RF0022) e
 * inativar (RF0023). Ver {@link ClienteService} para as regras de negócio.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    /** RF0021 — cadastrar cliente. */
    @PostMapping
    public ResponseEntity<ClienteResponse> cadastrar(@Valid @RequestBody CadastrarClienteRequest req) {
        Cliente cliente = service.cadastrar(req);
        return ResponseEntity.created(URI.create("/api/clientes/" + cliente.getId()))
                .body(ClienteResponse.from(cliente));
    }

    /** RF0024 — consultar clientes, com filtros combinados ou isolados. */
    @GetMapping
    public ResponseEntity<List<ClienteResponse>> consultar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String codigoCliente,
            @RequestParam(required = false) StatusCliente status) {
        List<ClienteResponse> resultado = service.consultar(nome, cpf, email, codigoCliente, status)
                .stream().map(ClienteResponse::from).toList();
        return ResponseEntity.ok(resultado);
    }

    /** RF0024 — consultar cliente por id. */
    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ClienteResponse.from(service.buscarPorId(id)));
    }

    /** RF0022 — alterar dados cadastrais. */
    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> alterar(@PathVariable Long id, @Valid @RequestBody AlterarClienteRequest req) {
        Cliente cliente = service.alterar(id, req);
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }

    /** RF0028 — alterar apenas a senha, isoladamente. */
    @PatchMapping("/{id}/senha")
    public ResponseEntity<Void> alterarSenha(@PathVariable Long id, @Valid @RequestBody AlterarSenhaRequest req) {
        service.alterarSenha(id, req);
        return ResponseEntity.noContent().build();
    }

    /**
     * RF0023 — inativar cliente. Implementado como PATCH (mudança de estado),
     * nunca como DELETE físico — não existe endpoint de exclusão neste módulo
     * (ver javadoc de {@link com.games.cliente.domain.enums.StatusCliente}).
     */
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<ClienteResponse> inativar(@PathVariable Long id) {
        Cliente cliente = service.inativar(id);
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }

    /** Reativa um cliente inativo — demonstra que a inativação preserva o registro. */
    @PatchMapping("/{id}/reativar")
    public ResponseEntity<ClienteResponse> reativar(@PathVariable Long id) {
        Cliente cliente = service.reativar(id);
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }
}
