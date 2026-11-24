package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.Recomendacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Conferência da resposta da IA (RNF0044): um modelo pequeno às vezes ignora a instrução e recomenda um jogo
 * que o cliente já tem, acima do orçamento ou de uma plataforma que ele não usa. Antes de mostrar a resposta,
 * cada jogo citado é conferido contra o perfil; se algum quebra essas regras, a resposta é trocada por uma
 * montada com os jogos que a pontuação aponta (o mesmo formato das respostas-modelo do ajuste fino).
 */
@Component
public class ConferenciaResposta {

    private static final Logger log = LoggerFactory.getLogger(ConferenciaResposta.class);

    /** Busca no catálogo ("quais jogos de corrida vocês têm?"): listar o que a loja tem não é recomendar. */
    private static final Pattern BUSCA = Pattern.compile("quais jogos|voces tem|vcs tem|tem no catalogo|o catalogo");
    /** Pedido de recomendação ("me recomenda um jogo", "o que eu jogo?"): a resposta precisa citar ao menos um jogo. */
    private static final Pattern RECOMENDACAO = Pattern.compile(
            "recomend|indic|sugest|sugir|o que (eu )?(devo |deveria |posso )?jogar|quero um jogo|quero algo|me ajuda a escolher");
    private static final Pattern PLATAFORMA_CITADA = Pattern.compile("\\b(pc|computador|switch|nintendo|playstation|ps5|xbox)\\b");

    public record Resultado(String resposta, boolean substituida, List<String> problemas) {}

    private final PerfilRecomendacaoService perfis;
    private final EstilosDeJogo estilos;
    private final RedacaoRespostas redacao;

    public ConferenciaResposta(PerfilRecomendacaoService perfis, EstilosDeJogo estilos, RedacaoRespostas redacao) {
        this.perfis = perfis;
        this.estilos = estilos;
        this.redacao = redacao;
    }

    /**
     * Sinais de que a mensagem é sobre jogos ou sobre a loja. Sem nenhum deles (e sem jogo, gênero, plataforma,
     * estilo ou valor citado), é assunto de fora: vale a recusa educada.
     */
    private static final Pattern ASSUNTO_DA_LOJA = Pattern.compile(
            "jog|game|gamer|console|recomend|indic|sugest|sugir|compr|preco|valor|reais|r\\$|barat|caro|promoc|cupom|"
                    + "catalogo|loja|nexus|estoque|pedido|frete|entrega|troca|pagamento|cartao|genero|estilo|lancamento|"
                    + "multijogador|online|cooperativ|fase|chefe|personagem|dlc|zerar|classificacao|idade|plataforma");
    /** Assuntos claramente de fora, mesmo com uma palavra que lembra um estilo ("a lição de matemática do meu filho"). */
    private static final Pattern ASSUNTO_DE_FORA = Pattern.compile(
            "licao|dever de casa|matematica|capital d|receita|previsao do tempo|clima|eleicao|votar|politic|poema|"
                    + "quem descobriu|maior pais|copa do mundo|quanto e \\d");
    /** Cumprimentos e agradecimentos não são "fora do assunto". */
    private static final Pattern CONVERSA = Pattern.compile("^\\s*((oi|ola|bom dia|boa tarde|boa noite|valeu|tchau|ate mais|beleza|ok)\\b|obrigad)");
    private static final Pattern PRECO = Pattern.compile("r\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern DESTAQUES = Pattern.compile("destaque(?:s)? (?:para|em)[: ]+([^.\\n]+)");
    private static final Pattern JA_TEM = Pattern.compile("ja tem|ja possui|ja comprou|voce tem este|ja e seu");

    public Resultado conferir(String resposta, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        // 1) assunto fora de jogos: sempre a recusa educada (o modelo às vezes responde ou mistura o assunto)
        if (foraDoAssunto(mensagem, catalogo)) {
            String recusa = redacao.foraDoAssunto(perfil);
            if (resposta.trim().equals(recusa)) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: assunto fora de jogos");
            return new Resultado(recusa, true, List.of("assunto fora de jogos"));
        }
        // 2) pergunta sobre um jogo: só os fatos do catálogo, e avisa se o cliente já tem o jogo
        List<JogoCatalogo> perguntado = jogoPerguntado(mensagem, catalogo);
        if (!perguntado.isEmpty()) {
            List<String> problemas = problemasSobreJogo(resposta, perguntado, perfil);
            if (problemas.isEmpty()) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: {}", problemas);
            JogoCatalogo jogo = perguntado.stream().filter(j -> PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil))
                    .findFirst().orElse(perguntado.get(0));
            return new Resultado(redacao.sobreJogo(jogo, perguntado, perfil), true, problemas);
        }
        // 3) recomendações: jogo já comprado, acima do orçamento ou de outra plataforma
        List<String> problemas = problemas(resposta, mensagem, perfil, catalogo);
        if (problemas.isEmpty()) return new Resultado(resposta, false, List.of());
        log.info("Resposta da IA trocada pela conferência: {}", problemas);
        return new Resultado(respostaConferida(mensagem, perfil, catalogo, vendas), true, problemas);
    }

    /** Mensagem sem nada de jogos ou da loja ("qual a capital da França?"). */
    boolean foraDoAssunto(String mensagem, List<JogoCatalogo> catalogo) {
        String texto = semAcento(mensagem);
        if (ASSUNTO_DE_FORA.matcher(texto).find() && !texto.contains("jog")) return true;
        if (texto.isBlank() || CONVERSA.matcher(texto).find() || ASSUNTO_DA_LOJA.matcher(texto).find()) return false;
        if (PLATAFORMA_CITADA.matcher(texto).find() || PerfilRecomendacaoService.valorDoPedido(mensagem) != null) return false;
        if (PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo) != null) return false;
        if (estilos != null && !estilos.detectar(mensagem).isEmpty()) return false;
        return catalogo.stream().map(JogoCatalogo::titulo).distinct().noneMatch(t -> citado(mensagem, t));
    }

    /** As versões do jogo, quando a mensagem pergunta de um jogo só ("O Hogwarts Legacy vale a pena?"). */
    List<JogoCatalogo> jogoPerguntado(String mensagem, List<JogoCatalogo> catalogo) {
        String texto = semAcento(mensagem);
        if (RECOMENDACAO.matcher(texto).find() || BUSCA.matcher(texto).find()) return List.of();
        List<String> citados = catalogo.stream().map(JogoCatalogo::titulo).distinct().filter(t -> citado(mensagem, t)).toList();
        if (citados.size() != 1) return List.of();
        return catalogo.stream().filter(j -> j.titulo().equals(citados.get(0))).toList();
    }

    /** O que a resposta diz do jogo tem de bater com o catálogo: plataformas, preço, destaques e se o cliente já tem. */
    List<String> problemasSobreJogo(String resposta, List<JogoCatalogo> versoes, PerfilCliente perfil) {
        String texto = semAcento(resposta);
        JogoCatalogo jogo = versoes.get(0);
        List<String> problemas = new ArrayList<>();
        if (perfil.titulosComprados().contains(jogo.titulo()) && !JA_TEM.matcher(texto).find()) {
            problemas.add(jogo.titulo() + ": não avisou que o cliente já tem o jogo");
        }
        List<String> plataformas = versoes.stream().map(JogoCatalogo::plataforma).toList();
        for (String citada : List.of("pc", "switch", "playstation", "xbox")) {
            boolean naResposta = Pattern.compile("\\b" + citada + "\\b").matcher(texto).find();
            boolean existe = plataformas.stream().anyMatch(p -> semAcento(p).contains(citada));
            if (naResposta && !existe) problemas.add(jogo.titulo() + ": não sai para " + citada);
        }
        // o jogo sai para a plataforma do cliente: a resposta tem de dizer isso
        List<String> doCliente = plataformas.stream().filter(p -> perfil.plataformasPreferidas().contains(p)).toList();
        if (!doCliente.isEmpty() && doCliente.stream().noneMatch(p -> texto.contains(semAcento(p).split(" ")[0]))) {
            problemas.add(jogo.titulo() + ": não citou " + String.join(" ou ", doCliente));
        }
        var precoCerto = versoes.stream().map(j -> PerfilRecomendacaoService.moeda(j.preco()).substring(3)).toList();
        var preco = PRECO.matcher(texto);
        while (preco.find()) {
            if (!precoCerto.contains(preco.group(1))) problemas.add(jogo.titulo() + ": preço errado (R$ " + preco.group(1) + ")");
        }
        var destaques = DESTAQUES.matcher(texto);
        if (destaques.find() && jogo.caracteristicas() != null) {
            String doCatalogo = semAcento(jogo.caracteristicas());
            for (String item : destaques.group(1).split(",|\\be\\b")) {
                String limpo = item.trim();
                if (limpo.length() > 2 && !doCatalogo.contains(limpo)) problemas.add(jogo.titulo() + ": destaque inventado (" + limpo + ")");
            }
        }
        return problemas.stream().distinct().toList();
    }

    /** Jogos citados na resposta que o cliente já tem, que passam do valor ou que não saem para a plataforma dele. */
    List<String> problemas(String resposta, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo) {
        String pedido = semAcento(mensagem);
        if (BUSCA.matcher(pedido).find()) return List.of();
        BigDecimal valorPedido = PerfilRecomendacaoService.valorDoPedido(mensagem);
        BigDecimal limite = valorPedido != null ? valorPedido : perfil.orcamento();
        boolean conferePlataforma = !PLATAFORMA_CITADA.matcher(pedido).find(); // pediu outra plataforma: vale a dele

        List<String> problemas = new ArrayList<>();
        boolean citouAlgum = false;
        Map<String, List<JogoCatalogo>> versoes = catalogo.stream()
                .collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<JogoCatalogo>> jogo : versoes.entrySet()) {
            String titulo = jogo.getKey();
            if (!citado(resposta, titulo)) continue;
            citouAlgum = true;
            if (citado(mensagem, titulo)) continue; // perguntou desse jogo: pode falar dele
            if (perfil.titulosComprados().contains(titulo)) {
                problemas.add(titulo + ": o cliente já tem");
                continue;
            }
            List<JogoCatalogo> dele = jogo.getValue().stream()
                    .filter(j -> !conferePlataforma || PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil)).toList();
            if (dele.isEmpty()) {
                problemas.add(titulo + ": não sai para " + String.join(" ou ", perfil.plataformasPreferidas()));
            } else if (limite != null && dele.stream().allMatch(j -> j.preco().compareTo(limite) > 0)) {
                problemas.add(titulo + ": passa de " + PerfilRecomendacaoService.moeda(limite));
            }
        }
        if (!citouAlgum && RECOMENDACAO.matcher(pedido).find()) problemas.add("pediu recomendação e a resposta não citou jogo");
        return problemas;
    }

    /** A resposta-modelo para o pedido: gênero, valor, estilo ou recomendação, com os jogos já filtrados pelo perfil. */
    private String respostaConferida(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        BigDecimal valor = PerfilRecomendacaoService.valorDoPedido(mensagem);
        // plataforma pedida na mensagem vale no lugar das do perfil, como nos blocos do prompt
        var pedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
        if (!pedidas.isEmpty()) perfil = perfil.comPlataformas(pedidas);
        PerfilCliente efetivo = valor != null ? perfil.comOrcamento(valor) : perfil;
        String genero = PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo);
        if (genero != null) {
            return redacao.genero(efetivo, genero, perfis.doGenero(efetivo, catalogo, vendas, genero));
        }
        if (valor != null) {
            return redacao.orcamento(valor, perfis.dentroDoValor(perfil, catalogo, vendas, valor));
        }
        List<EstilosDeJogo.Estilo> pedidos = estilos.detectar(mensagem);
        if (!pedidos.isEmpty()) {
            EstilosDeJogo.Estilo estilo = pedidos.get(0);
            return redacao.estilo(perfil, estilo, estilos.jogosPara(estilo, perfil, catalogo, 3), 0);
        }
        List<Recomendacao> recomendacoes = perfis.recomendar(perfil, catalogo, vendas, 3);
        if (recomendacoes.isEmpty()) {
            return "No momento não encontrei um jogo" + (perfil.orcamento() == null ? "" : " até " + PerfilRecomendacaoService.moeda(perfil.orcamento()))
                    + (perfil.plataformasPreferidas().isEmpty() ? "" : " para " + String.join(" ou ", perfil.plataformasPreferidas()))
                    + " que você ainda não tenha. Quer que eu procure com outro valor ou outra plataforma?";
        }
        return redacao.recomendacao(perfil, recomendacoes, 0);
    }

    private static boolean citado(String texto, String titulo) {
        // o título inteiro, sem ser pedaço de outro ("Civilization VI" dentro de "Civilization VII")
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(semAcento(titulo)) + "(?![\\p{L}\\p{N}])")
                .matcher(semAcento(texto)).find();
    }

    private static String semAcento(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
