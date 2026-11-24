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
import java.util.Set;
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
            "recomend|indic|sugest|sugir|o que (eu )?(devo |deveria |posso )?jogar|quero um jogo|quero algo|me ajuda a escolher|"
                    + "melhor(es)? jogos?|jogos? bo(m|ns)\\b|jogo (pra|para) mim|que jogo (eu )?(compro|devo|deveria|escolho)");
    private static final Pattern PLATAFORMA_CITADA = Pattern.compile("\\b(pc|computador|switch|nintendo|playstation|ps5|xbox)\\b");

    public record Resultado(String resposta, boolean substituida, List<String> problemas) {}

    private final PerfilRecomendacaoService perfis;
    private final EstilosDeJogo estilos;
    private final RedacaoRespostas redacao;
    private final PerguntasDoCatalogo perguntas = new PerguntasDoCatalogo();

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
    private static final Pattern CONVERSA = Pattern.compile(
            "^\\s*((oi|ola|bom dia|boa tarde|boa noite|valeu|tchau|ate mais|beleza|ok|sim|claro|com certeza|pode ser|por favor)\\b|obrigad)");
    /** Resposta curta a uma pergunta do assistente ("sim", "quero", "pode mandar", "outros"): é continuação da conversa. */
    private static final int PALAVRAS_DE_RESPOSTA_CURTA = 4;
    private static final Pattern PRECO = Pattern.compile("r\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern DESTAQUES = Pattern.compile("destaque(?:s)? (?:para|em)[: ]+([^.\\n]+)");
    private static final Pattern JA_TEM = Pattern.compile("ja tem|ja possui|ja comprou|voce tem este|ja e seu");
    /** "**Hollow Knight** (Nintendo Switch" ou "**Hollow Knight** (PC)": o título e a plataforma da versão citada. */
    private static final Pattern VERSAO_CITADA = Pattern.compile("\\*\\*([^*]+)\\*\\*\\s*\\(([^,)]+)");

    public Resultado conferir(String resposta, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        return conferir(resposta, mensagem, perfil, catalogo, vendas, List.of());
    }

    /**
     * @param respostasAnteriores respostas já dadas nesta conversa a outras perguntas: um modelo pequeno às vezes só
     *                            copia a resposta anterior, sem olhar o que o cliente perguntou agora
     */
    public Resultado conferir(String resposta, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas,
                              List<String> respostasAnteriores) {
        // 0) o cliente respondeu "não" a uma pergunta do assistente: encerra sem empurrar outro jogo
        if (ContinuacaoConversa.NEGATIVA.equals(mensagem)) {
            if (catalogo.stream().noneMatch(j -> citado(resposta, j.titulo())) && !resposta.isBlank()) {
                return new Resultado(resposta, false, List.of());
            }
            log.info("Resposta da IA trocada pela conferência: o cliente recusou e a resposta sugeriu jogos");
            return new Resultado(redacao.encerramento(perfil), true, List.of("o cliente recusou e a resposta sugeriu jogos"));
        }
        // 0a) um jogo e um objeto na mesma mensagem ("quando eu jogo Nidhogg 2, qual o melhor carro?"): o catálogo diz
        //     se o jogo tem aquilo, e a resposta se adapta ao jogo de verdade
        PedidosIncomuns.Objeto objeto = PedidosIncomuns.objeto(mensagem);
        // "quero um jogo de corrida com carros, tipo Forza" é pedido de recomendação, não dúvida sobre o jogo
        if (objeto != null && !objeto.vidaReal() && RECOMENDACAO.matcher(semAcento(mensagem)).find()) objeto = null;
        String jogoDaMensagem = objeto == null ? null : catalogo.stream().map(JogoCatalogo::titulo).distinct()
                .filter(t -> citadoNaMensagem(mensagem, t)).findFirst().orElse(null);
        if (jogoDaMensagem != null && perfis != null) {
            String certa = respostaObjetoNoJogo(objeto, jogoDaMensagem, perfil, catalogo, vendas);
            if (resposta.trim().equals(certa.trim())) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: {} no {}", objeto.singular(), jogoDaMensagem);
            return new Resultado(certa, true, List.of(objeto.singular() + " no " + jogoDaMensagem));
        }
        // 0b) pedido fora do comum ("jogar no banho", "o melhor carro para eu comprar"): resposta simpática com sugestões
        PedidosIncomuns.Pedido incomum = PedidosIncomuns.detectar(mensagem);
        if (incomum != null && perfis != null) {
            String certa = respostaIncomum(incomum, mensagem, perfil, catalogo, vendas);
            if (resposta.trim().equals(certa.trim())) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: pedido fora do comum ({})", incomum.tipo());
            return new Resultado(certa, true, List.of("pedido fora do comum: " + incomum.tipo()));
        }
        // 1) assunto fora de jogos: sempre a recusa educada (o modelo às vezes responde ou mistura o assunto)
        if (foraDoAssunto(mensagem, catalogo, !respostasAnteriores.isEmpty())) {
            String recusa = redacao.foraDoAssunto(perfil);
            if (resposta.trim().equals(recusa)) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: assunto fora de jogos");
            return new Resultado(recusa, true, List.of("assunto fora de jogos"));
        }
        // 2) pergunta sobre um jogo: só os fatos do catálogo, e avisa se o cliente já tem o jogo
        List<JogoCatalogo> perguntado = jogoPerguntado(mensagem, catalogo);
        if (!perguntado.isEmpty()) {
            List<String> problemas = new ArrayList<>(problemasSobreJogo(resposta, perguntado, perfil));
            problemas.addAll(plataformaPreferidaFalsa(resposta, mensagem, perfil));
            problemas.addAll(generoErrado(resposta, catalogo));
            if (problemas.isEmpty()) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: {}", problemas);
            JogoCatalogo jogo = perguntado.stream().filter(j -> PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil))
                    .findFirst().orElse(perguntado.get(0));
            return new Resultado(redacao.sobreJogo(jogo, perguntado, perfil), true, problemas);
        }
        // 2b) pergunta sobre a loja ("o mais barato", "os mais vendidos", "quantos jogos"): só os dados do catálogo
        PerguntasDoCatalogo.Pergunta daLoja = perguntas.detectar(mensagem, perfil, catalogo);
        if (daLoja != null) {
            List<String> problemas = new ArrayList<>(perguntas.problemas(resposta, daLoja, perfil, catalogo, vendas));
            problemas.addAll(plataformaPreferidaFalsa(resposta, mensagem, perfil));
            problemas.addAll(generoErrado(resposta, catalogo));
            if (repeteFrases(resposta)) problemas.add("repetiu frases na resposta");
            if (problemas.isEmpty()) return new Resultado(resposta, false, List.of());
            log.info("Resposta da IA trocada pela conferência: {}", problemas);
            return new Resultado(perguntas.responder(daLoja, perfil, catalogo, vendas), true, problemas);
        }
        // 3) recomendações e buscas: jogo já comprado, acima do orçamento, de outra plataforma, fora do gênero ou
        //    estilo pedido, "você já tem" inventado ou cópia da resposta anterior
        List<String> problemas = new ArrayList<>(problemas(resposta, mensagem, perfil, catalogo));
        problemas.addAll(problemasDoPedido(resposta, mensagem, perfil, catalogo, vendas, respostasAnteriores));
        if (problemas.isEmpty()) return new Resultado(resposta, false, List.of());
        log.info("Resposta da IA trocada pela conferência: {}", problemas);
        String conferida = BUSCA.matcher(semAcento(mensagem)).find()
                ? respostaDaBusca(mensagem, perfil, catalogo, vendas)
                : respostaConferida(mensagem, perfil, catalogo, vendas);
        return new Resultado(conferida, true, problemas.stream().distinct().toList());
    }

    /**
     * A resposta tem de servir para o que o cliente pediu agora: os jogos citados são do gênero ou do estilo pedido,
     * o "você já tem" só vale para o que ele comprou e a resposta não é a mesma dada a outra pergunta.
     */
    List<String> problemasDoPedido(String resposta, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo,
                                   DadosVendas vendas, List<String> respostasAnteriores) {
        List<String> problemas = new ArrayList<>();
        Map<String, List<JogoCatalogo>> citados = catalogo.stream().filter(j -> citado(resposta, j.titulo()))
                .collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()));
        String texto = semAcento(resposta);
        String pedido = semAcento(mensagem);
        var plataformasPedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);

        // "**Hollow Knight** (PC)" para quem marcou só PS5 e Switch: a versão citada tem de ser de uma plataforma dele
        // (a busca no catálogo lista todas as plataformas de cada jogo, então fica de fora)
        if (plataformasPedidas.isEmpty() && !perfil.plataformasPreferidas().isEmpty() && !BUSCA.matcher(pedido).find()) {
            Set<String> doCatalogo = catalogo.stream().map(JogoCatalogo::plataforma).collect(Collectors.toSet());
            var versao = VERSAO_CITADA.matcher(resposta);
            while (versao.find()) {
                String escrito = PerfilRecomendacaoService.normalizarTitulo(versao.group(1));
                String titulo = citados.keySet().stream().filter(t -> PerfilRecomendacaoService.normalizarTitulo(t).equals(escrito))
                        .findFirst().orElse(null);
                String plataforma = versao.group(2).trim();
                if (titulo != null && doCatalogo.contains(plataforma) && !perfil.plataformasPreferidas().contains(plataforma)
                        && !citadoNaMensagem(mensagem,titulo)) {
                    problemas.add(titulo + ": citou a versão de " + plataforma + ", fora das plataformas do cliente");
                }
            }
        }

        // "quero ver os de PC": a versão citada ("**Celeste** (Nintendo Switch)") tem de ser da plataforma pedida
        if (!plataformasPedidas.isEmpty()) {
            var versao = VERSAO_CITADA.matcher(resposta);
            while (versao.find()) {
                for (String plataforma : versao.group(2).split(",\\s*")) {
                    String pl = plataforma.trim();
                    if (List.of("PC", "PlayStation 5", "Xbox Series X|S", "Nintendo Switch").contains(pl) && !plataformasPedidas.contains(pl)
                            && !citadoNaMensagem(mensagem, versao.group(1))) {
                        problemas.add(versao.group(1).trim() + ": citou a versão de " + pl + ", mas o pedido é para "
                                + String.join(" ou ", plataformasPedidas));
                    }
                }
            }
        }
        // "quero ver os de PC": todo jogo sugerido tem de sair para a plataforma pedida
        if (!plataformasPedidas.isEmpty()) {
            citados.forEach((titulo, versoes) -> {
                if (!citadoNaMensagem(mensagem, titulo) && versoes.stream().noneMatch(j -> plataformasPedidas.contains(j.plataforma()))) {
                    problemas.add(titulo + ": não sai para " + String.join(" ou ", plataformasPedidas));
                }
            });
        }

        // "você já tem este jogo" dito de jogos que o cliente nunca comprou
        if (JA_TEM.matcher(texto).find() && !citados.isEmpty()
                && citados.keySet().stream().noneMatch(perfil.titulosComprados()::contains)) {
            problemas.add("disse que o cliente já tem " + String.join(", ", citados.keySet()) + ", mas ele não comprou");
        }

        // "é de Luta, um dos seus gêneros preferidos" para quem marcou Esporte e Corrida
        problemas.addAll(generoPreferidoFalso(resposta, citados, perfil));
        problemas.addAll(plataformaPreferidaFalsa(resposta, mensagem, perfil));
        problemas.addAll(generoErrado(resposta, catalogo));
        if (repeteFrases(resposta)) problemas.add("repetiu frases na resposta");

        // o jogo perguntado na mensagem pode aparecer; os outros têm de ser do gênero ou do estilo pedido
        Map<String, List<JogoCatalogo>> sugeridos = new LinkedHashMap<>(citados);
        sugeridos.keySet().removeIf(titulo -> citadoNaMensagem(mensagem,titulo));
        String genero = PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo);
        if (genero != null) {
            sugeridos.forEach((titulo, versoes) -> {
                if (versoes.stream().noneMatch(j -> j.genero().equals(genero))) problemas.add(titulo + ": não é de " + genero);
            });
            // "quais jogos de corrida vocês têm?": a lista tem de trazer todos os do gênero nas plataformas do cliente
            // (ou na plataforma e até o valor que ele pediu)
            if (BUSCA.matcher(pedido).find()) {
                List<String> faltam = doGeneroBuscado(genero, mensagem, perfil, catalogo).stream()
                        .map(JogoCatalogo::titulo).distinct().filter(t -> !citados.containsKey(t)).toList();
                if (!faltam.isEmpty()) problemas.add("a busca deixou de fora " + String.join(", ", faltam));
            }
        } else if (estilos != null) {
            List<EstilosDeJogo.Estilo> pedidos = estilos.detectar(mensagem);
            if (!pedidos.isEmpty()) {
                // os mesmos jogos do bloco JOGOS QUE COMBINAM COM O PEDIDO do prompt (uma palavra solta, como
                // "criar pistas" num jogo de corrida, não basta para o jogo servir a quem quer criar e ter liberdade)
                var pedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
                PerfilCliente efetivo = pedidas.isEmpty() ? perfil : perfil.comPlataformas(pedidas);
                Set<String> combinam = pedidos.stream().flatMap(e -> estilos.jogosPara(e, efetivo, catalogo, 3).stream())
                        .map(JogoCatalogo::titulo).collect(Collectors.toSet());
                sugeridos.keySet().stream().filter(titulo -> !combinam.contains(titulo))
                        .forEach(titulo -> problemas.add(titulo + ": não combina com " + pedidos.get(0).descricao()));
            }
        }

        // "outros parecidos com X": a resposta tem de trazer outro jogo, não só o próprio X
        List<String> base = citados.keySet().stream().filter(t -> citadoNaMensagem(mensagem,t)).toList();
        if (pedido.contains("parecid") && !base.isEmpty() && sugeridos.isEmpty()) {
            problemas.add("pediu jogos parecidos com " + base.get(0) + " e a resposta não sugeriu outro");
        }

        // recomendação sem gênero, valor, estilo ou plataforma na mensagem: vale o perfil de agora (o cliente pode
        // ter mudado gêneros, plataformas e orçamento na tela), ou seja, as SUGESTÕES PARA ESTE CLIENTE do prompt
        boolean pedidoGeral = RECOMENDACAO.matcher(pedido).find() && genero == null && plataformasPedidas.isEmpty()
                && PerfilRecomendacaoService.valorDoPedido(mensagem) == null && !pedido.contains("parecid") && base.isEmpty()
                && (estilos == null || estilos.detectar(mensagem).isEmpty());
        if (pedidoGeral && perfis != null && vendas != null && !sugeridos.isEmpty()) {
            List<Recomendacao> sugestoes = perfis.recomendar(perfil, catalogo, vendas, 5);
            Set<String> doPerfil = sugestoes.stream().map(r -> r.jogo().titulo()).collect(Collectors.toSet());
            sugeridos.keySet().stream().filter(t -> !doPerfil.contains(t) && !perfil.titulosComprados().contains(t))
                    .forEach(t -> problemas.add(t + ": não está nas sugestões do perfil"));
            // um jogo de cada gênero marcado que tem sugestão (mesmo que sejam 3 ou 4 gêneros)
            Set<String> generos = perfil.generosPreferidos();
            List<String> semJogo = generos.stream()
                    .filter(g -> sugestoes.stream().anyMatch(r -> r.jogo().genero().equals(g)))
                    .filter(g -> sugeridos.values().stream().flatMap(List::stream).noneMatch(j -> j.genero().equals(g)))
                    .toList();
            if (!semJogo.isEmpty()) {
                problemas.add("não citou jogo de " + String.join(", ", semJogo) + ", gênero que o cliente marcou");
            }
        }

        // cópia de uma resposta anterior da conversa (dada a outra pergunta)
        String normalizada = texto.replaceAll("\\s+", " ").trim();
        if (respostasAnteriores.stream().anyMatch(a -> semAcento(a).replaceAll("\\s+", " ").trim().equals(normalizada))) {
            problemas.add("repetiu uma resposta anterior da conversa");
        }
        return problemas;
    }

    /**
     * Os jogos do gênero que a busca tem de listar: na plataforma pedida na mensagem (ou nas do cliente) e até o valor
     * pedido, se houver.
     */
    private static List<JogoCatalogo> doGeneroBuscado(String genero, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo) {
        var pedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
        BigDecimal valor = PerfilRecomendacaoService.valorDoPedido(mensagem);
        return catalogo.stream().filter(j -> j.genero().equals(genero))
                .filter(j -> pedidas.isEmpty() ? PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil) : pedidas.contains(j.plataforma()))
                .filter(j -> valor == null || j.preco().compareTo(valor) <= 0)
                .toList();
    }

    /** Busca no catálogo ("quais jogos de corrida vocês têm?"): a lista do gênero ou da plataforma pedida. */
    private String respostaDaBusca(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        String genero = PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo);
        if (genero != null) {
            var pedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
            BigDecimal valor = PerfilRecomendacaoService.valorDoPedido(mensagem);
            if (pedidas.isEmpty() && valor == null) {
                return redacao.busca(genero, catalogo.stream().filter(j -> j.genero().equals(genero)).toList(), perfil);
            }
            String filtros = (pedidas.isEmpty() ? "" : " para " + String.join(" ou ", pedidas))
                    + (valor == null ? "" : " até " + PerfilRecomendacaoService.moeda(valor));
            return redacao.busca(genero, doGeneroBuscado(genero, mensagem, perfil, catalogo), perfil, filtros);
        }
        var pedidas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
        if (pedidas.size() == 1) {
            String plataforma = pedidas.iterator().next();
            return redacao.buscaPorPlataforma(plataforma, catalogo.stream().filter(j -> j.plataforma().equals(plataforma)).toList(), perfil);
        }
        return respostaConferida(mensagem, perfil, catalogo, vendas);
    }

    /**
     * Se o jogo tem o objeto: as características do catálogo citam o objeto ("centenas de carros") ou o jogo é do gênero
     * dele (carro num jogo de corrida). Senão, o jogo "não tem" e vêm os jogos do gênero do objeto.
     */
    private String respostaObjetoNoJogo(PedidosIncomuns.Objeto objeto, String titulo, PerfilCliente perfil, List<JogoCatalogo> catalogo,
                                        DadosVendas vendas) {
        List<JogoCatalogo> versoes = catalogo.stream().filter(j -> j.titulo().equals(titulo)).toList();
        JogoCatalogo jogo = versoes.stream().filter(j -> PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil)).findFirst()
                .orElse(versoes.get(0));
        String raiz = semAcento(objeto.singular().split(" ")[0]);
        boolean nasCaracteristicas = jogo.caracteristicas() != null && semAcento(jogo.caracteristicas()).contains(raiz);
        boolean temNoJogo = nasCaracteristicas || ("Corrida".equals(objeto.genero()) && objeto.genero().equals(jogo.genero()));
        DadosVendas dados = vendas == null ? new DadosVendas(Map.of(), Map.of()) : vendas;
        List<Recomendacao> doGenero = temNoJogo ? List.of() : perfis.doGenero(perfil, catalogo, dados, objeto.genero());
        return redacao.objetoNoJogo(perfil, jogo, objeto, temNoJogo, doGenero);
    }

    /** A resposta para uma situação impossível (sugere para depois) ou uma dúvida da vida real (liga a um gênero de jogo). */
    private String respostaIncomum(PedidosIncomuns.Pedido pedido, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo,
                                   DadosVendas vendas) {
        DadosVendas dados = vendas == null ? new DadosVendas(Map.of(), Map.of()) : vendas;
        if (pedido.tipo() == PedidosIncomuns.Tipo.SITUACAO) {
            return redacao.situacao(perfil, pedido, perfis.recomendar(perfil, catalogo, dados, 3));
        }
        String jogoCitado = catalogo.stream().map(JogoCatalogo::titulo).distinct().filter(t -> citadoNaMensagem(mensagem, t))
                .findFirst().orElse(null);
        List<Recomendacao> doGenero = pedido.genero() == null ? List.of() : perfis.doGenero(perfil, catalogo, dados, pedido.genero());
        return redacao.vidaReal(perfil, pedido, doGenero, jogoCitado);
    }

    /** Mensagem sem nada de jogos ou da loja ("qual a capital da França?"). */
    boolean foraDoAssunto(String mensagem, List<JogoCatalogo> catalogo) {
        return foraDoAssunto(mensagem, catalogo, false);
    }

    /** @param emConversa o assistente já respondeu nesta conversa: uma resposta curta é continuação dela */
    boolean foraDoAssunto(String mensagem, List<JogoCatalogo> catalogo, boolean emConversa) {
        String texto = semAcento(mensagem);
        if (ASSUNTO_DE_FORA.matcher(texto).find() && !texto.contains("jog")) return true;
        if (texto.isBlank() || CONVERSA.matcher(texto).find() || ASSUNTO_DA_LOJA.matcher(texto).find()) return false;
        if (PerguntasDoCatalogo.tipo(mensagem, catalogo) != null) return false; // "o que está fazendo sucesso?"
        if (emConversa && texto.trim().split("\\s+").length <= PALAVRAS_DE_RESPOSTA_CURTA) return false;
        if (PLATAFORMA_CITADA.matcher(texto).find() || PerfilRecomendacaoService.valorDoPedido(mensagem) != null) return false;
        if (PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo) != null) return false;
        if (estilos != null && !estilos.detectar(mensagem).isEmpty()) return false;
        return catalogo.stream().map(JogoCatalogo::titulo).distinct().noneMatch(t -> citadoNaMensagem(mensagem,t));
    }

    /** As versões do jogo, quando a mensagem pergunta de um jogo só ("O Hogwarts Legacy vale a pena?"). */
    List<JogoCatalogo> jogoPerguntado(String mensagem, List<JogoCatalogo> catalogo) {
        String texto = semAcento(mensagem);
        if (RECOMENDACAO.matcher(texto).find() || BUSCA.matcher(texto).find()) return List.of();
        List<String> citados = catalogo.stream().map(JogoCatalogo::titulo).distinct().filter(t -> citadoNaMensagem(mensagem,t)).toList();
        if (citados.size() != 1) return List.of();
        return catalogo.stream().filter(j -> j.titulo().equals(citados.get(0))).toList();
    }

    /** O que a resposta diz do jogo tem de bater com o catálogo: plataformas, preço, destaques e se o cliente já tem. */
    List<String> problemasSobreJogo(String resposta, List<JogoCatalogo> versoes, PerfilCliente perfil) {
        String texto = semAcento(resposta);
        JogoCatalogo jogo = versoes.get(0);
        List<String> problemas = new ArrayList<>();
        // "procure o Minecraft" respondido com outro jogo (o preço e a plataforma podem até bater por acaso)
        if (!citado(resposta, jogo.titulo())) {
            problemas.add(jogo.titulo() + ": a resposta não falou do jogo perguntado");
        }
        // quem pergunta de um jogo quer saber quanto custa; e a resposta não pode ficar repetindo a mesma frase
        if (!PRECO.matcher(texto).find()) problemas.add(jogo.titulo() + ": não informou o preço");
        if (repeteFrases(resposta)) problemas.add(jogo.titulo() + ": repetiu frases na resposta");
        problemas.addAll(generoPreferidoFalso(resposta, Map.of(jogo.titulo(), versoes), perfil));
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
        // fala em destaques e não traz nenhum dos destaques do catálogo ("**Destaque**:" vazio)
        if (texto.contains("destaque") && jogo.caracteristicas() != null
                && java.util.Arrays.stream(semAcento(jogo.caracteristicas()).split(",")).map(String::trim).noneMatch(texto::contains)) {
            problemas.add(jogo.titulo() + ": não trouxe os destaques do catálogo");
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
            if (citadoNaMensagem(mensagem,titulo)) continue; // perguntou desse jogo: pode falar dele
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
        // "outros jogos parecidos com X": do gênero de X ou que quem comprou X também levou
        String parecidoCom = semAcento(mensagem).contains("parecid")
                ? catalogo.stream().map(JogoCatalogo::titulo).distinct().filter(t -> citadoNaMensagem(mensagem,t)).findFirst().orElse(null) : null;
        if (parecidoCom != null && perfis != null && vendas != null) {
            Set<String> generosBase = catalogo.stream().filter(j -> j.titulo().equals(parecidoCom)).map(JogoCatalogo::genero)
                    .collect(Collectors.toSet());
            var juntos = vendas.coCompras().getOrDefault(parecidoCom, Map.of());
            List<JogoCatalogo> candidatos = catalogo.stream().filter(j -> !j.titulo().equals(parecidoCom))
                    .filter(j -> generosBase.contains(j.genero()) || juntos.containsKey(j.titulo())).toList();
            List<Recomendacao> parecidos = perfis.recomendarOuMaisVendidos(efetivo, candidatos, vendas, 3);
            if (!parecidos.isEmpty()) return redacao.parecidos(parecidoCom, parecidos);
        }
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
        // o título inteiro (ou o nome antes de ":"), sem ser pedaço de outro ("Civilization VI" dentro de "Civilization VII")
        return PerfilRecomendacaoService.citaTitulo(texto, titulo);
    }

    /** Na mensagem do cliente vale também o título com um erro de digitação ("conta mais sobre the wicther 3"). */
    private static boolean citadoNaMensagem(String mensagem, String titulo) {
        return PerfilRecomendacaoService.citaTituloAproximado(mensagem, titulo);
    }

    /** "é de Sandbox, um dos seus gêneros preferidos" dito de um jogo de gênero que o cliente não marcou. */
    private static List<String> generoPreferidoFalso(String resposta, Map<String, List<JogoCatalogo>> citados, PerfilCliente perfil) {
        List<String> problemas = new ArrayList<>();
        for (String linha : resposta.split("\n")) {
            String semAcentoLinha = semAcento(linha);
            if (!semAcentoLinha.contains("generos preferidos") && !semAcentoLinha.contains("genero preferido")) continue;
            List<String> naLinha = citados.keySet().stream().filter(t -> citado(linha, t)).toList();
            if (naLinha.size() == 1 && citados.get(naLinha.get(0)).stream().noneMatch(j -> perfil.generosPreferidos().contains(j.genero()))) {
                problemas.add(naLinha.get(0) + ": disse que é de um gênero preferido do cliente, mas não é");
            }
        }
        return problemas;
    }

    private static final List<String> PLATAFORMAS = List.of("Nintendo Switch", "PC", "PlayStation 5", "Xbox Series X|S");
    private static final Pattern PREFERIDA_ANTES = Pattern.compile("(as |a )?plataformas? (que voce prefere|preferidas?)|sua plataforma preferida");
    private static final Pattern PREFERIDA_DEPOIS = Pattern.compile("(a )?sua plataforma (e|eh|sao) ");

    /**
     * "sai para PlayStation 5, a plataforma que você prefere" ou "a sua plataforma é Xbox" para uma plataforma que o
     * cliente não marcou no perfil (nem pediu na mensagem).
     */
    static List<String> plataformaPreferidaFalsa(String resposta, String mensagem, PerfilCliente perfil) {
        Set<String> valem = new java.util.HashSet<>(perfil.plataformasPreferidas());
        valem.addAll(PerfilRecomendacaoService.plataformasCitadas(mensagem, PLATAFORMAS));
        Set<String> falsas = new java.util.LinkedHashSet<>();
        for (String linha : semAcento(resposta).split("\n")) {
            var antes = PREFERIDA_ANTES.matcher(linha);
            while (antes.find()) {
                // o trecho desde o último ":", "—" ou "(" até a frase: "é de Sandbox ... e sai para playstation 5, "
                String trecho = linha.substring(0, antes.start());
                int corte = Math.max(Math.max(trecho.lastIndexOf(':'), trecho.lastIndexOf('—')), Math.max(trecho.lastIndexOf('('), trecho.lastIndexOf('.')));
                falsas.addAll(PerfilRecomendacaoService.plataformasCitadas(trecho.substring(corte + 1), PLATAFORMAS));
            }
            var depois = PREFERIDA_DEPOIS.matcher(linha);
            while (depois.find()) {
                String resto = linha.substring(depois.end());
                int fim = resto.length();
                for (char c : new char[]{'.', ';', ',', ':'}) if (resto.indexOf(c) >= 0) fim = Math.min(fim, resto.indexOf(c));
                falsas.addAll(PerfilRecomendacaoService.plataformasCitadas(resto.substring(0, fim), PLATAFORMAS));
            }
        }
        falsas.removeAll(valem);
        return falsas.stream().map(p -> "disse que " + p + " é a plataforma preferida do cliente, mas ele não marcou").toList();
    }

    /** "Plataforma" e "Festa" também são palavras comuns ("a plataforma que você prefere", "festa com amigos"). */
    private static final Set<String> GENEROS_AMBIGUOS = Set.of("Plataforma", "Festa");

    /**
     * "**Hogwarts Legacy** (Switch) — R$ 179,90: RPG" para um jogo de Aventura: em cada linha que cita um jogo só, um
     * gênero do catálogo diferente do dele (fora o título e as características do próprio jogo) é gênero errado.
     */
    static List<String> generoErrado(String resposta, List<JogoCatalogo> catalogo) {
        Map<String, List<JogoCatalogo>> porTitulo = catalogo.stream()
                .collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()));
        Set<String> generos = catalogo.stream().map(JogoCatalogo::genero).filter(g -> !GENEROS_AMBIGUOS.contains(g))
                .collect(Collectors.toSet());
        List<String> problemas = new ArrayList<>();
        for (String linha : resposta.split("\n")) {
            List<String> naLinha = porTitulo.keySet().stream().filter(t -> citado(linha, t)).toList();
            if (naLinha.size() != 1) continue;
            List<JogoCatalogo> versoes = porTitulo.get(naLinha.get(0));
            String resto = semAcento(linha).replace(semAcento(naLinha.get(0)), " ");
            if (versoes.get(0).caracteristicas() != null) {
                for (String item : versoes.get(0).caracteristicas().split(",")) resto = resto.replace(semAcento(item.trim()), " ");
            }
            for (String genero : generos) {
                if (versoes.stream().anyMatch(j -> j.genero().equals(genero))) continue;
                if (Pattern.compile("\\b" + Pattern.quote(semAcento(genero)) + "\\b").matcher(resto).find()) {
                    problemas.add(naLinha.get(0) + ": não é de " + genero + ", é de " + versoes.get(0).genero());
                }
            }
        }
        return problemas;
    }

    /** A mesma frase (de umas 3 palavras ou mais) dita duas vezes na resposta. */
    private static boolean repeteFrases(String resposta) {
        List<String> frases = java.util.Arrays.stream(semAcento(resposta).split("[.!?\\n]+"))
                .map(f -> f.replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim())
                .filter(f -> f.length() >= 15).toList();
        return frases.size() != frases.stream().distinct().count();
    }

    private static String semAcento(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
