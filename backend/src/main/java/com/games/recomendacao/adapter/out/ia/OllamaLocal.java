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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptador da IA generativa local e gratuita (RNF0044, item 1): o Ollama roda o modelo
 * Qwen2.5 1.5B Instruct na própria máquina (na placa de vídeo, quando há) e responde o chat; o
 * ajuste fino é um treino LoRA em Python ({@code ia-local/treinar_lora.py}) sobre o mesmo modelo,
 * com o dataset de vendas e feedback. O modelo treinado é importado no Ollama com o nome da versão
 * (ex.: nexus-recomendador-v2) e passa a responder no chat.
 *
 * Pré-requisitos (ver README): Ollama instalado com {@code ollama pull qwen2.5:1.5b}, e a pasta
 * {@code ia-local} com o ambiente Python (.venv) e o modelo base baixado em {@code modelos/}.
 */
@Component
@ConditionalOnProperty(name = "nexus.ia.provedor", havingValue = "local", matchIfMissing = true)
public class OllamaLocal implements IaGenerativa {

    private static final String PASTA_MODELO_HF = "modelos/Qwen2.5-1.5B-Instruct";

    private final ObjectMapper mapper;
    private final RestClient http;
    private final String ollamaUrl;
    private final String modeloBase;
    private final Path pasta;
    private final String ollama;
    private final int epocas;
    private final Map<String, Process> processos = new ConcurrentHashMap<>();

    public OllamaLocal(ObjectMapper mapper,
                       @Value("${nexus.ia.local.ollama-url:http://localhost:11434}") String ollamaUrl,
                       @Value("${nexus.ia.local.modelo-base:qwen2.5:1.5b}") String modeloBase,
                       @Value("${nexus.ia.local.pasta:}") String pasta,
                       @Value("${nexus.ia.local.ollama:}") String ollama,
                       @Value("${nexus.ia.epocas:2}") int epocas) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(180));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.ollamaUrl = ollamaUrl;
        this.modeloBase = modeloBase;
        this.pasta = pasta.isBlank() ? localizarPasta() : Path.of(pasta).toAbsolutePath();
        this.ollama = !ollama.isBlank() ? ollama : localizarOllama();
        this.epocas = epocas;
    }

    @Override
    public boolean configurada() {
        return motivoNaoConfigurada() == null;
    }

    @Override
    public String motivoNaoConfigurada() {
        try {
            JsonNode modelos = http.get().uri(ollamaUrl + "/api/tags").retrieve().body(JsonNode.class);
            for (JsonNode modelo : modelos.path("models")) {
                if (modelo.path("name").asText().equals(modeloBase) || modelo.path("model").asText().equals(modeloBase)) return null;
            }
            return "O modelo " + modeloBase + " não está no Ollama. Rode no terminal: ollama pull " + modeloBase;
        } catch (RestClientException e) {
            return "O Ollama não está rodando em " + ollamaUrl + ". Abra o aplicativo Ollama (ou rode: ollama serve).";
        }
    }

    @Override
    public String descricao() {
        return "Ollama local — Qwen2.5 1.5B Instruct, ajuste fino LoRA na própria máquina";
    }

    @Override
    public String modeloBase() {
        return modeloBase;
    }

    // --------------------- conversa ---------------------

    @Override
    public String responder(String modelo, String instrucaoSistema, List<Turno> conversa) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", modelo == null ? modeloBase : modelo);
        corpo.put("messages", mensagens(instrucaoSistema, conversa));
        corpo.put("stream", false);
        corpo.put("options", Map.of("temperature", 0.3, "num_ctx", 12288));
        try {
            JsonNode resposta = http.post().uri(ollamaUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON).body(corpo)
                    .retrieve().body(JsonNode.class);
            String texto = resposta.path("message").path("content").asText("").trim();
            if (texto.isEmpty()) throw new IaIndisponivelException("A IA não devolveu uma resposta. Tente reformular a pergunta.");
            return texto;
        } catch (RestClientException e) {
            String motivo = motivoNaoConfigurada();
            throw new IaIndisponivelException(motivo != null ? motivo : "Falha ao conversar com o Ollama: " + e.getMessage());
        }
    }

    @Override
    public String linhaDataset(String instrucaoSistema, List<Turno> conversa) {
        try {
            return mapper.writeValueAsString(Map.of("messages", mensagens(instrucaoSistema, conversa)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Formato de chat (system, user, assistant): o mesmo no Ollama e no dataset do treino. */
    private static List<Map<String, String>> mensagens(String instrucaoSistema, List<Turno> conversa) {
        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", instrucaoSistema));
        for (Turno turno : conversa) {
            mensagens.add(Map.of("role", turno.doCliente() ? "user" : "assistant", "content", turno.texto()));
        }
        return mensagens;
    }

    // --------------------- ajuste fino local ---------------------

    @Override
    public String publicarDataset(String nomeArquivo, String jsonl) {
        Path arquivo = pasta.resolve("treinamentos").resolve(Path.of(nomeArquivo).getFileName().toString());
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, jsonl, StandardCharsets.UTF_8);
            return arquivo.toString();
        } catch (IOException e) {
            throw new IaIndisponivelException("Não foi possível gravar o dataset em " + arquivo + ": " + e.getMessage());
        }
    }

    @Override
    public String iniciarAjusteFino(String datasetUri, String nomeModelo) {
        Path python = pasta.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (!Files.exists(python)) python = pasta.resolve(".venv").resolve("bin").resolve("python");
        Path script = pasta.resolve("treinar_lora.py");
        Path modeloHf = pasta.resolve(PASTA_MODELO_HF);
        Path conversor = pasta.resolve("llama.cpp").resolve("convert_hf_to_gguf.py");
        if (!Files.exists(python) || !Files.exists(script) || !Files.isDirectory(modeloHf) || !Files.exists(conversor)) {
            throw new IaIndisponivelException("Ambiente de treino incompleto em " + pasta
                    + ": é preciso ia-local/.venv (Python com torch, transformers e peft), o modelo base em ia-local/"
                    + PASTA_MODELO_HF + " e o conversor em ia-local/llama.cpp (ver README).");
        }
        Path saida = pasta.resolve("treinamentos").resolve(nomeModelo);
        try {
            Files.createDirectories(saida);
            Files.deleteIfExists(saida.resolve("status.json"));
            ProcessBuilder construtor = new ProcessBuilder(python.toString(), script.toString(),
                    "--dataset", datasetUri, "--modelo-base", modeloHf.toString(), "--saida", saida.toString(),
                    "--nome", nomeModelo, "--epocas", String.valueOf(epocas), "--ollama", ollama,
                    "--conversor", conversor.toString())
                    .directory(pasta.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(saida.resolve("log.txt").toFile());
            construtor.environment().put("PYTHONIOENCODING", "utf-8");
            construtor.environment().put("HF_HUB_OFFLINE", "1");
            processos.put(saida.toString(), construtor.start());
            return saida.toString();
        } catch (IOException e) {
            throw new IaIndisponivelException("Não foi possível iniciar o treino local: " + e.getMessage());
        }
    }

    @Override
    public SituacaoAjuste consultarAjuste(String jobId) {
        Path status = Path.of(jobId).resolve("status.json");
        JsonNode json = null;
        try {
            if (Files.exists(status)) json = mapper.readTree(Files.readString(status, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // arquivo sendo regravado: tenta de novo na próxima consulta
        }
        String estado = json == null ? "EM_ANDAMENTO" : json.path("estado").asText("EM_ANDAMENTO");
        Process processo = processos.get(jobId);
        if ("CONCLUIDO".equals(estado)) {
            processos.remove(jobId);
            return new SituacaoAjuste(SituacaoAjuste.Estado.CONCLUIDO, json.path("modelo").asText(), null,
                    "Perda final " + json.path("perda").asText() + " · " + json.path("exemplos").asText() + " exemplos · "
                            + (json.path("duracaoSegundos").asLong() / 60) + " min");
        }
        if ("FALHOU".equals(estado)) {
            processos.remove(jobId);
            return new SituacaoAjuste(SituacaoAjuste.Estado.FALHOU, null, json.path("erro").asText("Falha no treino"), null);
        }
        if (processo != null && !processo.isAlive()) {
            processos.remove(jobId);
            return new SituacaoAjuste(SituacaoAjuste.Estado.FALHOU, null,
                    "O processo de treino terminou sem concluir (código " + processo.exitValue() + "). Veja " + Path.of(jobId).resolve("log.txt"), null);
        }
        if (processo == null && json == null) {
            return new SituacaoAjuste(SituacaoAjuste.Estado.FALHOU, null, "Treino interrompido (a aplicação foi reiniciada).", null);
        }
        String detalhe = json == null ? "Iniciando" : json.path("etapa").asText("Treinando")
                + (json.has("progresso") ? " · " + Math.round(json.path("progresso").asDouble() * 100) + "%" : "")
                + (json.has("perda") ? " · perda " + json.path("perda").asText() : "")
                + (json.has("restanteSegundos") ? " · faltam ~" + Math.max(1, json.path("restanteSegundos").asLong() / 60) + " min" : "");
        return new SituacaoAjuste(SituacaoAjuste.Estado.EM_ANDAMENTO, null, null, detalhe);
    }

    /**
     * O banco é recriado a cada subida, mas os modelos treinados ficam no Ollama: procura a versão
     * mais recente concluída em ia-local/treinamentos e que ainda esteja no Ollama.
     */
    @Override
    public Optional<ModeloRecuperado> ultimoModeloAjustado() {
        Set<String> noOllama = new HashSet<>();
        try {
            for (JsonNode modelo : http.get().uri(ollamaUrl + "/api/tags").retrieve().body(JsonNode.class).path("models")) {
                noOllama.add(modelo.path("name").asText().replace(":latest", ""));
            }
        } catch (RestClientException e) {
            return Optional.empty();
        }
        ModeloRecuperado melhor = null;
        try (var pastas = Files.list(pasta.resolve("treinamentos"))) {
            for (Path dir : pastas.filter(Files::isDirectory).toList()) {
                Matcher nome = Pattern.compile("nexus-recomendador-v(\\d+)").matcher(dir.getFileName().toString());
                Path status = dir.resolve("status.json");
                if (!nome.matches() || !Files.exists(status) || !noOllama.contains(dir.getFileName().toString())) continue;
                JsonNode json = mapper.readTree(Files.readString(status, StandardCharsets.UTF_8));
                if (!"CONCLUIDO".equals(json.path("estado").asText())) continue;
                int versao = Integer.parseInt(nome.group(1));
                if (melhor == null || versao > melhor.versao()) {
                    melhor = new ModeloRecuperado(dir.getFileName().toString(), versao,
                            "Perda final " + json.path("perda").asText() + " · " + json.path("exemplos").asInt() + " exemplos",
                            json.path("exemplos").asInt(), LocalDateTime.parse(json.path("atualizadoEm").asText()));
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.ofNullable(melhor);
        }
        return Optional.ofNullable(melhor);
    }

    // --------------------- caminhos ---------------------

    /** A pasta ia-local fica na raiz do projeto; a aplicação pode rodar da raiz ou de backend/. */
    private static Path localizarPasta() {
        Path atual = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && atual != null; i++, atual = atual.getParent()) {
            if (Files.exists(atual.resolve("ia-local").resolve("treinar_lora.py"))) return atual.resolve("ia-local");
        }
        return Path.of("ia-local").toAbsolutePath();
    }

    /** O instalador do Ollama no Windows põe o executável em %LOCALAPPDATA%\Programs\Ollama. */
    private static String localizarOllama() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            Path exe = Path.of(local, "Programs", "Ollama", "ollama.exe");
            if (Files.exists(exe)) return exe.toString();
        }
        return "ollama";
    }
}
