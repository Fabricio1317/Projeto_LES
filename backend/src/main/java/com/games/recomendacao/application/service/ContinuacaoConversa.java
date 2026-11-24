package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Respostas curtas do cliente às perguntas do assistente ("Quer que eu sugira outros parecidos?" → "sim").
 * Sozinho, "sim" não diz nada ao modelo nem à conferência; aqui ele vira o pedido completo, a partir da
 * pergunta que o assistente fez na resposta anterior e dos jogos que ela citou
 * ("Sim, me sugira outros jogos parecidos com Hollow Knight").
 */
@Component
public class ContinuacaoConversa {

    /** Resposta negativa já interpretada: o cliente não quer mais sugestões agora. */
    public static final String NEGATIVA = "Não, obrigado. Por enquanto é só isso.";

    private static final int MAXIMO_DE_PALAVRAS = 6;
    /** Palavras de uma resposta só de concordância ("sim", "quero sim", "pode mandar", "claro, por favor"). */
    private static final Set<String> AFIRMATIVA = Set.of("sim", "s", "quero", "queria", "claro", "que", "pode", "ser", "isso", "manda",
            "mande", "mandar", "mostra", "mostre", "mostrar", "bora", "vamos", "ok", "okay", "beleza", "blz", "com", "certeza", "aham",
            "uhum", "por", "favor", "gostaria", "seria", "otimo", "perfeito", "fechado", "positivo", "yes", "outros", "outras", "mais",
            "ai", "me", "sugere", "sugira", "entao", "legal", "show", "top", "bom", "boa", "opcoes", "opcao", "fala", "conta", "sobre");
    /** Palavras de uma resposta só de recusa ("não", "não, obrigado", "agora não", "por enquanto não precisa"). */
    private static final Set<String> NEGATIVA_CLIENTE = Set.of("nao", "nem", "agora", "precisa", "quero", "obrigado", "obrigada",
            "valeu", "brigado", "por", "enquanto", "so", "isso", "ta", "bom", "tudo", "nope", "negativo", "mesmo", "e", "assim");
    private static final Map<String, Integer> ORDINAIS = Map.of("primeiro", 0, "segundo", 1, "terceiro", 2, "ultimo", -1);
    private static final Pattern ORDINAL = Pattern.compile("\\b(primeiro|segundo|terceiro|ultimo)\\b");
    private static final Pattern ESSE = Pattern.compile("^(esse|este|essa|esta|ele)\\b");

    /**
     * O pedido que a mensagem do cliente representa, para o modelo e a conferência. Mensagens que não são
     * resposta curta a uma pergunta do assistente voltam sem mudança.
     */
    public String interpretar(String mensagem, String respostaAnterior, List<JogoCatalogo> catalogo) {
        return interpretar(mensagem, null, respostaAnterior, catalogo);
    }

    /**
     * @param perguntaAnterior o que o cliente perguntou antes ("Quais jogos de corrida vocês têm?"): "quero ver só os de
     *                         PC" continua aquele assunto ("Quais jogos de Corrida vocês têm para PC?")
     */
    public String interpretar(String mensagem, String perguntaAnterior, String respostaAnterior, List<JogoCatalogo> catalogo) {
        if (mensagem == null || respostaAnterior == null || respostaAnterior.isBlank()) return mensagem;
        String refinada = refinamento(mensagem, perguntaAnterior, respostaAnterior, catalogo);
        if (refinada != null) return refinada;
        String texto = semAcento(mensagem).replaceAll("[!?.,]", " ").trim();
        if (texto.isEmpty() || texto.split("\\s+").length > MAXIMO_DE_PALAVRAS) return mensagem;

        List<String> citados = citadosEmOrdem(respostaAnterior, catalogo);
        // "o segundo", "fala do último": o jogo daquela posição na lista anterior
        Matcher ordinal = ORDINAL.matcher(texto);
        if (ordinal.find() && !citados.isEmpty()) {
            int posicao = ORDINAIS.get(ordinal.group(1));
            int indice = posicao < 0 ? citados.size() - 1 : Math.min(posicao, citados.size() - 1);
            return "Me fala mais sobre o " + citados.get(indice);
        }
        if (ESSE.matcher(texto).find() && !citados.isEmpty()) return "Me fala mais sobre o " + citados.get(0);

        List<String> palavras = List.of(texto.split("\\s+"));
        boolean recusa = palavras.stream().anyMatch(p -> p.equals("nao") || p.equals("nem") || p.equals("nope") || p.equals("negativo"));
        if (recusa && NEGATIVA_CLIENTE.containsAll(palavras)) return NEGATIVA;
        // "Quero um jogo de corrida" não é um "sim": a mensagem precisa ser só concordância
        if (recusa || !AFIRMATIVA.containsAll(palavras)) return mensagem;

        // o "sim" responde à pergunta que fechou a resposta anterior
        String pergunta = semAcento(ultimaPergunta(respostaAnterior));
        String primeiro = citados.isEmpty() ? null : citados.get(0);
        if (pergunta.contains("parecid") && primeiro != null) {
            return "Sim, me sugira outros jogos parecidos com " + primeiro;
        }
        if ((pergunta.contains("mais sobre") || pergunta.contains("mais detalhes") || pergunta.contains("chamou sua atencao")
                || pergunta.contains("comparo")) && primeiro != null) {
            return "Me fala mais sobre o " + primeiro;
        }
        if (pergunta.contains("mais barat")) {
            BigDecimal menor = catalogo.stream().filter(j -> citados.contains(j.titulo())).map(JogoCatalogo::preco)
                    .min(Comparator.naturalOrder()).orElse(null);
            if (menor != null && menor.compareTo(BigDecimal.ONE) > 0) {
                return "Me recomenda um jogo mais barato, até R$ " + menor.subtract(BigDecimal.ONE).setScale(0, RoundingMode.DOWN);
            }
        }
        // "quer que eu indique qual combina mais?", "posso te recomendar um jogo...?" e as demais: uma recomendação
        return "Sim, me recomenda um jogo";
    }

    private static final int MAXIMO_DE_PALAVRAS_DO_REFINAMENTO = 9;
    private static final Pattern BUSCA = Pattern.compile("quais jogos|voces tem|vcs tem|tem no catalogo|o catalogo|temos estes");
    /** O gênero no começo da resposta anterior: "Temos estes jogos de Corrida", "De Corrida, estes combinam". */
    private static final Pattern GENERO_DA_RESPOSTA = Pattern.compile("^(?:temos estes jogos de|de) ([^,:\\n]+?)(?: no catalogo| para |,|:)");

    /**
     * "quero ver somente os de PC", "e até R$ 100?", "tem pro Switch?": a mensagem só muda a plataforma ou o valor
     * do pedido anterior, então o gênero (e se era busca no catálogo ou recomendação) vem dele.
     */
    private static String refinamento(String mensagem, String perguntaAnterior, String respostaAnterior, List<JogoCatalogo> catalogo) {
        String texto = semAcento(mensagem);
        if (texto.split("\\s+").length > MAXIMO_DE_PALAVRAS_DO_REFINAMENTO) return null;
        // "me recomenda um jogo até R$ 100" é um pedido novo, de qualquer gênero
        if (texto.matches(".*\\b(um jogo|algum jogo|qualquer|outro genero)\\b.*")) return null;
        if (PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo) != null) return null;
        if (catalogo.stream().map(JogoCatalogo::titulo).distinct().anyMatch(t -> PerfilRecomendacaoService.citaTituloAproximado(mensagem, t))) return null;
        var plataformas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
        BigDecimal valor = PerfilRecomendacaoService.valorDoPedido(mensagem);
        if (plataformas.isEmpty() && valor == null) return null;

        String genero = perguntaAnterior == null ? null : PerfilRecomendacaoService.generoDoPedido(perguntaAnterior, catalogo);
        if (genero == null) {
            Matcher cabecalho = GENERO_DA_RESPOSTA.matcher(semAcento(respostaAnterior.trim()));
            if (cabecalho.find()) genero = PerfilRecomendacaoService.generoDoPedido("de " + cabecalho.group(1), catalogo);
        }
        if (genero == null) return null;

        String filtros = (plataformas.isEmpty() ? "" : " para " + String.join(" ou ", plataformas))
                + (valor == null ? "" : " até R$ " + valor.stripTrailingZeros().toPlainString().replace('.', ','));
        boolean eraBusca = BUSCA.matcher(semAcento(perguntaAnterior == null ? "" : perguntaAnterior)).find()
                || semAcento(respostaAnterior.trim()).startsWith("temos estes");
        return eraBusca ? "Quais jogos de " + genero + " vocês têm" + filtros + "?" : "Me indica um jogo de " + genero + filtros;
    }

    /** A última frase com interrogação da resposta ("Quer que eu sugira outros parecidos?"). */
    private static String ultimaPergunta(String resposta) {
        int fim = resposta.lastIndexOf('?');
        if (fim < 0) return "";
        int inicio = Math.max(Math.max(resposta.lastIndexOf('.', fim - 1), resposta.lastIndexOf('\n', fim - 1)),
                Math.max(resposta.lastIndexOf('!', fim - 1), resposta.lastIndexOf(';', fim - 1)));
        return resposta.substring(inicio + 1, fim + 1);
    }

    /** Títulos do catálogo citados na resposta, na ordem em que aparecem. */
    static List<String> citadosEmOrdem(String resposta, List<JogoCatalogo> catalogo) {
        String texto = semAcento(resposta);
        return catalogo.stream().map(JogoCatalogo::titulo).distinct()
                .map(t -> Map.entry(t, posicao(texto, t)))
                .filter(e -> e.getValue() >= 0)
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static int posicao(String texto, String titulo) {
        return PerfilRecomendacaoService.posicaoDoTitulo(texto, titulo);
    }

    private static String semAcento(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
