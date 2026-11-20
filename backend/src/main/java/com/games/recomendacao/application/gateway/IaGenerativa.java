package com.games.recomendacao.application.gateway;

import java.util.List;

/**
 * Porta de saída para a IA generativa (RNF0044, item 1). O serviço do assistente e o treinamento
 * falam só com esta interface; o provedor fica no adaptador: {@code OllamaLocal} (padrão, gratuito,
 * roda na máquina) ou {@code VertexAiGemini} (Google Cloud, com nexus.ia.provedor=vertex). Trocar de
 * provedor não mexe na regra de negócio.
 */
public interface IaGenerativa {

    /** Uma fala da conversa: do cliente ou do assistente. */
    record Turno(boolean doCliente, String texto) {}

    /** Situação de um ajuste fino no provedor; detalhe é o andamento ou o resumo (ex.: "Treinando · 45%"). */
    record SituacaoAjuste(Estado estado, String modeloAjustado, String erro, String detalhe) {
        public enum Estado { EM_ANDAMENTO, CONCLUIDO, FALHOU }
    }

    /** Se as credenciais do provedor estão disponíveis. */
    boolean configurada();

    /** Por que a IA não está configurada (null quando está). */
    String motivoNaoConfigurada();

    /** Ex.: "Google Vertex AI — Gemini (projeto x, região y)". */
    String descricao();

    String modeloBase();

    /**
     * Gera a resposta do assistente.
     *
     * @param modelo modelo ajustado a usar, ou null para o modelo base
     */
    String responder(String modelo, String instrucaoSistema, List<Turno> conversa);

    /** Uma linha do dataset de ajuste fino, no formato que o provedor espera (JSONL). */
    String linhaDataset(String instrucaoSistema, List<Turno> conversa);

    /** Publica o dataset onde o provedor lê e devolve a referência (ex.: gs://...). */
    String publicarDataset(String nomeArquivo, String jsonl);

    /** Inicia o ajuste fino supervisionado a partir do modelo base e devolve o id do job. */
    String iniciarAjusteFino(String datasetUri, String nomeModelo);

    SituacaoAjuste consultarAjuste(String jobId);

    /** Um modelo ajustado que já existe no provedor (treinado antes de a aplicação reiniciar). */
    record ModeloRecuperado(String nome, int versao, String detalhe, int exemplos, java.time.LocalDateTime concluidoEm) {}

    /** O modelo ajustado mais recente já disponível no provedor, se houver. */
    default java.util.Optional<ModeloRecuperado> ultimoModeloAjustado() {
        return java.util.Optional.empty();
    }
}
