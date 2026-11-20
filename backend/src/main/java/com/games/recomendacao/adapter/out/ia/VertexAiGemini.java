package com.games.recomendacao.adapter.out.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.games.cliente.application.exception.IaIndisponivelException;
import com.games.recomendacao.application.gateway.IaGenerativa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador alternativo da IA generativa (nexus.ia.provedor=vertex): Google Vertex AI com os modelos
 * Gemini (RNF0044, item 1). É pago; o padrão do projeto é o {@code OllamaLocal}, gratuito.
 * Gera as respostas do chat ({@code generateContent}) e faz o ajuste fino supervisionado
 * ({@code tuningJobs}): o dataset JSONL vai para um bucket do Cloud Storage, o Vertex treina um
 * adaptador sobre o Gemini e devolve um endpoint, que passa a ser o modelo usado no chat.
 *
 * Configuração (variáveis de ambiente ou application.properties):
 * GOOGLE_APPLICATION_CREDENTIALS (arquivo JSON da conta de serviço) e, opcionais,
 * nexus.ia.projeto, nexus.ia.regiao, nexus.ia.modelo-base, nexus.ia.bucket e nexus.ia.epocas.
 */
@Component
@ConditionalOnProperty(name = "nexus.ia.provedor", havingValue = "vertex")
public class VertexAiGemini implements IaGenerativa {

    private final ObjectMapper mapper;
    private final RestClient http;
    private final CredencialGoogle credencial;
    private final String motivoNaoConfigurada;
    private final String projeto;
    private final String regiao;
    private final String modeloBase;
    private final String bucket;
    private final int epocas;

    public VertexAiGemini(ObjectMapper mapper,
                          @Value("${nexus.ia.credenciais:${GOOGLE_APPLICATION_CREDENTIALS:}}") String caminhoCredenciais,
                          @Value("${nexus.ia.projeto:}") String projeto,
                          @Value("${nexus.ia.regiao:us-central1}") String regiao,
                          @Value("${nexus.ia.modelo-base:gemini-2.5-flash}") String modeloBase,
                          @Value("${nexus.ia.bucket:}") String bucket,
                          @Value("${nexus.ia.epocas:3}") int epocas) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(120));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.regiao = regiao;
        this.modeloBase = modeloBase;
        this.epocas = epocas;

        CredencialGoogle carregada = null;
        String motivo = null;
        if (caminhoCredenciais.isBlank()) {
            motivo = "Defina a variável de ambiente GOOGLE_APPLICATION_CREDENTIALS com o caminho do arquivo JSON "
                    + "da conta de serviço do Google Cloud e reinicie a aplicação (passo a passo no README).";
        } else if (!Files.isRegularFile(Path.of(caminhoCredenciais))) {
            motivo = "Arquivo de credenciais do Google Cloud não encontrado: " + caminhoCredenciais;
        } else {
            try {
                carregada = CredencialGoogle.carregar(Path.of(caminhoCredenciais), mapper);
            } catch (Exception e) {
                motivo = "Credenciais do Google Cloud inválidas (" + caminhoCredenciais + "): " + e.getMessage();
            }
        }
        this.projeto = !projeto.isBlank() ? projeto : carregada != null ? carregada.projeto() : "";
        if (motivo == null && this.projeto.isBlank()) {
            motivo = "Projeto do Google Cloud não identificado: informe nexus.ia.projeto.";
        }
        this.credencial = motivo == null ? carregada : null;
        this.motivoNaoConfigurada = motivo;
        this.bucket = !bucket.isBlank() ? bucket : this.projeto + "-nexus-ia";
    }

    @Override
    public boolean configurada() {
        return credencial != null;
    }

    @Override
    public String motivoNaoConfigurada() {
        return motivoNaoConfigurada;
    }

    @Override
    public String descricao() {
        return "Google Vertex AI — Gemini" + (configurada() ? " (projeto " + projeto + ", região " + regiao + ")" : "");
    }

    @Override
    public String modeloBase() {
        return modeloBase;
    }

    // --------------------- conversa ---------------------

    @Override
    public String responder(String modelo, String instrucaoSistema, List<Turno> conversa) {
        exigirConfigurada();
        String alvo = modelo != null && modelo.startsWith("projects/")
                ? modelo // endpoint do modelo ajustado
                : recursoLocal() + "/publishers/google/models/" + (modelo == null ? modeloBase : modelo);
        Map<String, Object> corpo = corpoConversa(instrucaoSistema, conversa);
        Map<String, Object> geracao = new LinkedHashMap<>();
        geracao.put("temperature", 0.6);
        geracao.put("maxOutputTokens", 2048);
        if (modeloBase.contains("2.5-flash")) {
            geracao.put("thinkingConfig", Map.of("thinkingBudget", 0)); // resposta mais rápida no chat
        }
        corpo.put("generationConfig", geracao);

        String url = host() + "/v1/" + alvo + ":generateContent";
        JsonNode resposta;
        try {
            resposta = post(url, corpo);
        } catch (ErroProvedor e) {
            if (e.status != 400 || !geracao.containsKey("thinkingConfig")) throw e;
            geracao.remove("thinkingConfig"); // modelo que não aceita o ajuste de raciocínio
            resposta = post(url, corpo);
        }

        JsonNode candidato = resposta.path("candidates").path(0);
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false)) texto.append(parte.path("text").asText(""));
        }
        if (texto.isEmpty()) {
            String motivo = resposta.path("promptFeedback").path("blockReason").asText(candidato.path("finishReason").asText("sem conteúdo"));
            throw new IaIndisponivelException("A IA não devolveu uma resposta (" + motivo + "). Tente reformular a pergunta.");
        }
        return texto.toString().trim();
    }

    @Override
    public String linhaDataset(String instrucaoSistema, List<Turno> conversa) {
        try {
            return mapper.writeValueAsString(corpoConversa(instrucaoSistema, conversa));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mesmo formato no chat e no dataset: systemInstruction + contents (user/model). */
    private Map<String, Object> corpoConversa(String instrucaoSistema, List<Turno> conversa) {
        List<Map<String, Object>> conteudos = new ArrayList<>();
        for (Turno turno : conversa) {
            conteudos.add(Map.of("role", turno.doCliente() ? "user" : "model", "parts", List.of(Map.of("text", turno.texto()))));
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instrucaoSistema))));
        corpo.put("contents", conteudos);
        return corpo;
    }

    // --------------------- ajuste fino ---------------------

    @Override
    public String publicarDataset(String nomeArquivo, String jsonl) {
        exigirConfigurada();
        garantirBucket();
        String url = "https://storage.googleapis.com/upload/storage/v1/b/" + bucket + "/o?uploadType=media&name="
                + URLEncoder.encode(nomeArquivo, StandardCharsets.UTF_8);
        executar(() -> http.post().uri(URI.create(url))
                .header("Authorization", "Bearer " + credencial.token(http))
                .contentType(MediaType.parseMediaType("application/jsonl"))
                .body(jsonl.getBytes(StandardCharsets.UTF_8))
                .retrieve().body(JsonNode.class));
        return "gs://" + bucket + "/" + nomeArquivo;
    }

    @Override
    public String iniciarAjusteFino(String datasetUri, String nomeModelo) {
        exigirConfigurada();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("baseModel", modeloBase);
        corpo.put("supervisedTuningSpec", Map.of(
                "trainingDatasetUri", datasetUri,
                "hyperParameters", Map.of("epochCount", epocas)));
        corpo.put("tunedModelDisplayName", nomeModelo);
        return post(host() + "/v1/" + recursoLocal() + "/tuningJobs", corpo).path("name").asText();
    }

    @Override
    public SituacaoAjuste consultarAjuste(String jobId) {
        exigirConfigurada();
        JsonNode job = get(host() + "/v1/" + jobId);
        return switch (job.path("state").asText()) {
            case "JOB_STATE_SUCCEEDED" -> new SituacaoAjuste(SituacaoAjuste.Estado.CONCLUIDO,
                    job.path("tunedModel").path("endpoint").asText(), null, null);
            case "JOB_STATE_FAILED", "JOB_STATE_CANCELLED", "JOB_STATE_EXPIRED" -> new SituacaoAjuste(SituacaoAjuste.Estado.FALHOU,
                    null, job.path("error").path("message").asText(job.path("state").asText()), null);
            default -> new SituacaoAjuste(SituacaoAjuste.Estado.EM_ANDAMENTO, null, null, job.path("state").asText());
        };
    }

    private void garantirBucket() {
        try {
            get("https://storage.googleapis.com/storage/v1/b/" + bucket);
        } catch (ErroProvedor e) {
            if (e.status != 404) throw e;
            String local = switch (regiao) {
                case "us", "global" -> "US";
                case "eu" -> "EU";
                default -> regiao.toUpperCase();
            };
            post("https://storage.googleapis.com/storage/v1/b?project=" + projeto, Map.of("name", bucket, "location", local));
        }
    }

    // --------------------- HTTP ---------------------

    private String host() {
        if (regiao.equals("global")) return "https://aiplatform.googleapis.com";
        if (regiao.equals("us") || regiao.equals("eu")) return "https://aiplatform." + regiao + ".rep.googleapis.com";
        return "https://" + regiao + "-aiplatform.googleapis.com";
    }

    private String recursoLocal() {
        return "projects/" + projeto + "/locations/" + regiao;
    }

    private JsonNode post(String url, Object corpo) {
        return executar(() -> http.post().uri(URI.create(url))
                .header("Authorization", "Bearer " + credencial.token(http))
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve().body(JsonNode.class));
    }

    private JsonNode get(String url) {
        return executar(() -> http.get().uri(URI.create(url))
                .header("Authorization", "Bearer " + credencial.token(http))
                .retrieve().body(JsonNode.class));
    }

    private JsonNode executar(java.util.function.Supplier<JsonNode> chamada) {
        try {
            JsonNode resposta = chamada.get();
            return resposta == null ? mapper.createObjectNode() : resposta;
        } catch (RestClientResponseException e) {
            String detalhe = e.getResponseBodyAsString();
            try {
                detalhe = mapper.readTree(detalhe).path("error").path("message").asText(detalhe);
            } catch (Exception ignorada) {
                // corpo não é JSON: usa o texto como veio
            }
            throw new ErroProvedor(e.getStatusCode().value(), "Google Cloud respondeu " + e.getStatusCode().value() + ": " + detalhe);
        } catch (ResourceAccessException e) {
            throw new IaIndisponivelException("Sem conexão com o Google Cloud: " + e.getMessage());
        }
    }

    private void exigirConfigurada() {
        if (!configurada()) throw new IaIndisponivelException(motivoNaoConfigurada);
    }

    /** Erro HTTP do provedor, com o status para decidir novas tentativas. */
    private static class ErroProvedor extends IaIndisponivelException {
        private final int status;

        ErroProvedor(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }
}
