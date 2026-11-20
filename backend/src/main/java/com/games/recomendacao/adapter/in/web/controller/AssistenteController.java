package com.games.recomendacao.adapter.in.web.controller;

import com.games.recomendacao.adapter.in.web.dto.request.AvaliacaoRequest;
import com.games.recomendacao.adapter.in.web.dto.request.MensagemAssistenteRequest;
import com.games.recomendacao.adapter.in.web.dto.request.PreferenciaRequest;
import com.games.recomendacao.adapter.in.web.dto.response.MensagemAssistenteResponse;
import com.games.recomendacao.adapter.in.web.dto.response.PreferenciaResponse;
import com.games.recomendacao.application.service.AssistenteService;
import com.games.recomendacao.application.service.PreferenciaService;
import com.games.recomendacao.application.service.PreferenciaService.OpcoesPreferencia;
import com.games.recomendacao.application.service.TreinamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Chatbot de recomendação do perfil cliente (RNF0044): preferências, conversa e avaliação das
 * respostas. Restrito ao perfil cliente pelo {@code PerfilClienteInterceptor}.
 */
@RestController
@RequestMapping("/api")
public class AssistenteController {

    private final AssistenteService assistenteService;
    private final PreferenciaService preferenciaService;
    private final TreinamentoService treinamentoService;

    public AssistenteController(AssistenteService assistenteService, PreferenciaService preferenciaService,
                                TreinamentoService treinamentoService) {
        this.assistenteService = assistenteService;
        this.preferenciaService = preferenciaService;
        this.treinamentoService = treinamentoService;
    }

    /** Situação da IA para o chat: se está pronta e qual modelo responde. */
    @GetMapping("/assistente/status")
    public ResponseEntity<Map<String, Object>> status() {
        TreinamentoService.StatusIa status = treinamentoService.status();
        return ResponseEntity.ok(Map.of(
                "configurada", status.configurada(),
                "provedor", status.provedor(),
                "modeloEmUso", status.modeloEmUso(),
                "modeloAjustado", status.modeloAjustado(),
                "motivoNaoConfigurada", status.motivoNaoConfigurada() == null ? "" : status.motivoNaoConfigurada()));
    }

    @GetMapping("/assistente/opcoes")
    public ResponseEntity<OpcoesPreferencia> opcoes() {
        return ResponseEntity.ok(preferenciaService.opcoes());
    }

    @GetMapping("/clientes/{clienteId}/preferencias")
    public ResponseEntity<PreferenciaResponse> preferencias(@PathVariable Long clienteId) {
        return ResponseEntity.ok(PreferenciaResponse.from(preferenciaService.buscar(clienteId)));
    }

    @PutMapping("/clientes/{clienteId}/preferencias")
    public ResponseEntity<PreferenciaResponse> salvarPreferencias(@PathVariable Long clienteId, @RequestBody PreferenciaRequest req) {
        return ResponseEntity.ok(PreferenciaResponse.from(preferenciaService.salvar(clienteId, req.generos(), req.plataformas(),
                req.orcamentoMaximo(), req.observacoes())));
    }

    @PostMapping("/clientes/{clienteId}/assistente/mensagens")
    public ResponseEntity<MensagemAssistenteResponse> conversar(@PathVariable Long clienteId, @RequestBody MensagemAssistenteRequest req) {
        return ResponseEntity.ok(MensagemAssistenteResponse.from(assistenteService.conversar(clienteId, req.conversaId(), req.mensagem())));
    }

    @GetMapping("/clientes/{clienteId}/assistente/mensagens")
    public ResponseEntity<List<MensagemAssistenteResponse>> conversa(@PathVariable Long clienteId, @RequestParam String conversaId) {
        return ResponseEntity.ok(assistenteService.conversa(clienteId, conversaId).stream().map(MensagemAssistenteResponse::from).toList());
    }

    /** Feedback já registrado pelo cliente (roteiro 7 da apresentação). */
    @GetMapping("/clientes/{clienteId}/assistente/avaliacoes")
    public ResponseEntity<List<MensagemAssistenteResponse>> avaliacoes(@PathVariable Long clienteId) {
        return ResponseEntity.ok(assistenteService.avaliacoesDoCliente(clienteId).stream().map(MensagemAssistenteResponse::from).toList());
    }

    @PutMapping("/assistente/mensagens/{mensagemId}/avaliacao")
    public ResponseEntity<MensagemAssistenteResponse> avaliar(@PathVariable Long mensagemId, @RequestBody AvaliacaoRequest req) {
        return ResponseEntity.ok(MensagemAssistenteResponse.from(assistenteService.avaliar(mensagemId, req.avaliacao())));
    }
}
