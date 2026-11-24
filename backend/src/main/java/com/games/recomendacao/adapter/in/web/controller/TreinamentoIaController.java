package com.games.recomendacao.adapter.in.web.controller;

import com.games.recomendacao.adapter.in.web.dto.response.DatasetResponse;
import com.games.recomendacao.adapter.in.web.dto.response.MensagemAssistenteResponse;
import com.games.recomendacao.adapter.in.web.dto.response.TreinamentoResponse;
import com.games.recomendacao.application.service.AssistenteService;
import com.games.recomendacao.application.service.TreinamentoService;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.vendas.adapter.in.web.interceptor.SomenteAdministrador;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Treinamento do modelo generativo (RNF0044, item 3) para o administrador: situação da IA, dataset,
 * histórico de ajustes finos e feedback registrado. Restrito pelo {@code PerfilAdministradorInterceptor}.
 */
@RestController
@SomenteAdministrador
@RequestMapping("/api/ia")
public class TreinamentoIaController {

    private final TreinamentoService treinamentoService;
    private final AssistenteService assistenteService;

    public TreinamentoIaController(TreinamentoService treinamentoService, AssistenteService assistenteService) {
        this.treinamentoService = treinamentoService;
        this.assistenteService = assistenteService;
    }

    @GetMapping("/status")
    public ResponseEntity<TreinamentoService.StatusIa> status() {
        return ResponseEntity.ok(treinamentoService.status());
    }

    @GetMapping("/treinamentos")
    public ResponseEntity<List<TreinamentoResponse>> treinamentos() {
        return ResponseEntity.ok(treinamentoService.historico().stream().map(TreinamentoResponse::from).toList());
    }

    @PostMapping("/treinamentos")
    public ResponseEntity<TreinamentoResponse> treinar() {
        return ResponseEntity.status(HttpStatus.CREATED).body(TreinamentoResponse.from(treinamentoService.iniciar("MANUAL")));
    }

    @GetMapping("/dataset")
    public ResponseEntity<DatasetResponse> dataset() {
        return ResponseEntity.ok(DatasetResponse.from(treinamentoService.dataset()));
    }

    /** O arquivo JSONL completo, igual ao enviado ao provedor no ajuste fino. */
    @GetMapping("/dataset/arquivo")
    public ResponseEntity<byte[]> datasetArquivo() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("dataset-ajuste-fino.jsonl").build().toString())
                .contentType(new MediaType("application", "jsonl", StandardCharsets.UTF_8))
                .body(treinamentoService.dataset().jsonl().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/feedbacks")
    public ResponseEntity<List<MensagemAssistenteResponse>> feedbacks() {
        List<MensagemAssistente> avaliadas = assistenteService.todasAvaliacoes();
        Map<Long, String> nomes = assistenteService.nomesDosClientes(avaliadas.stream().map(MensagemAssistente::getClienteId).distinct().toList());
        return ResponseEntity.ok(avaliadas.stream().map(m -> MensagemAssistenteResponse.from(m, nomes.get(m.getClienteId()))).toList());
    }
}
