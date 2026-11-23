package com.games.recomendacao.application.service;

import com.games.cliente.adapter.out.persistence.repository.ClienteRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.domain.entity.Cliente;
import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.adapter.out.persistence.repository.PreferenciaClienteRepository;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.entity.PreferenciaCliente;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.entity.PedidoItem;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dados que personalizam o assistente (RNF0044, item 1): o catálogo disponível, o perfil do cliente
 * (histórico de compras, preferências declaradas e feedback dado no chat) e a pontuação de cada jogo
 * para ele, que combina preferências, histórico e "quem comprou X também comprou Y" das vendas.
 * A pontuação gera as respostas-modelo do dataset de ajuste fino; a instrução de sistema é a mesma
 * no treino e no chat, para o modelo ajustado reconhecer o formato.
 */
@Service
public class PerfilRecomendacaoService {

    /** Compras efetivadas, as que contam como histórico (as mesmas do ranking, RN0027). */
    public static final List<StatusPedido> EFETIVADAS = List.of(StatusPedido.APROVADA, StatusPedido.EM_TRANSPORTE, StatusPedido.ENTREGUE);

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Pattern VALOR_PEDIDO = Pattern.compile(
            "(?:at[eé]|menos de|no m[aá]ximo|abaixo de)\\s*(?:r\\$)?\\s*(\\d+(?:[.,]\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

    public record JogoCatalogo(Long id, String titulo, String plataforma, String genero, String classificacao, Integer ano,
                               String desenvolvedora, BigDecimal preco, int estoque, String caracteristicas) {

        /** Características que contêm alguma das palavras (minúsculas). */
        public List<String> caracteristicasCom(List<String> palavras) {
            if (caracteristicas == null) return List.of();
            return java.util.Arrays.stream(caracteristicas.split(",")).map(String::trim)
                    .filter(c -> palavras.stream().anyMatch(p -> c.toLowerCase().contains(p))).toList();
        }
    }

    public record ItemComprado(String titulo, String genero, String plataforma, LocalDate data) {}

    public record PerfilCliente(Long clienteId, String nome, Integer idade, List<ItemComprado> compras,
                                Map<String, Long> generosComprados, Map<String, Long> plataformasCompradas,
                                Set<String> generosPreferidos, Set<String> plataformasPreferidas, BigDecimal orcamento,
                                String observacoes, Set<String> jogosNaoUteis, Set<String> jogosUteis) {

        public String primeiroNome() {
            return nome.split(" ")[0];
        }

        public Set<String> titulosComprados() {
            return compras.stream().map(ItemComprado::titulo).collect(Collectors.toSet());
        }

        /** Gênero que mais representa o cliente: o preferido declarado ou, sem ele, o mais comprado. */
        public String generoPrincipal() {
            if (!generosPreferidos.isEmpty()) return generosPreferidos.iterator().next();
            return generosComprados.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }

        /** O mesmo perfil com outras plataformas (o cliente pediu uma plataforma na mensagem). */
        public PerfilCliente comPlataformas(Set<String> plataformas) {
            return new PerfilCliente(clienteId, nome, idade, compras, generosComprados, plataformasCompradas, generosPreferidos,
                    plataformas, orcamento, observacoes, jogosNaoUteis, jogosUteis);
        }

        /** O mesmo perfil com outro limite de preço (o valor que o cliente pediu na mensagem vale mais que o do perfil). */
        public PerfilCliente comOrcamento(BigDecimal limite) {
            return new PerfilCliente(clienteId, nome, idade, compras, generosComprados, plataformasCompradas, generosPreferidos,
                    plataformasPreferidas, limite, observacoes, jogosNaoUteis, jogosUteis);
        }
    }

    public record Recomendacao(JogoCatalogo jogo, double pontuacao, List<String> motivos) {}

    /** Vendas agregadas: força de cada par "comprou X e também Y" (ponderada por cliente) e o total por jogo. */
    public record DadosVendas(Map<String, Map<String, Double>> coCompras, Map<String, Integer> vendasPorJogo) {}

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final JogoRepository jogoRepository;
    private final PreferenciaClienteRepository preferenciaRepository;
    private final MensagemAssistenteRepository mensagemRepository;
    private final EstilosDeJogo estilos;

    public PerfilRecomendacaoService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
                                     JogoRepository jogoRepository, PreferenciaClienteRepository preferenciaRepository,
                                     MensagemAssistenteRepository mensagemRepository, EstilosDeJogo estilos) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.jogoRepository = jogoRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.mensagemRepository = mensagemRepository;
        this.estilos = estilos;
    }

    @Transactional(readOnly = true)
    public List<JogoCatalogo> catalogo() {
        return jogoRepository.findByAtivoTrueOrderByTituloAsc().stream()
                .filter(j -> j.getEstoque() > 0)
                .map(PerfilRecomendacaoService::paraCatalogo)
                .toList();
    }

    @Transactional(readOnly = true)
    public PerfilCliente perfil(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
        List<ItemComprado> compras = new ArrayList<>();
        for (Pedido pedido : pedidoRepository.findByClienteIdAndStatusIn(clienteId, EFETIVADAS)) {
            for (PedidoItem item : pedido.getItens()) {
                compras.add(new ItemComprado(item.getTitulo(), item.getJogo().getGenero(), item.getJogo().getPlataforma(),
                        pedido.getDataCompra().toLocalDate()));
            }
        }
        compras.sort(Comparator.comparing(ItemComprado::data));

        PreferenciaCliente preferencia = preferenciaRepository.findById(clienteId).orElse(null);
        List<String> titulos = jogoRepository.findAllByOrderByTituloAsc().stream().map(Jogo::getTitulo).toList();

        return new PerfilCliente(clienteId, cliente.getNome(),
                cliente.getDataNascimento() == null ? null : Period.between(cliente.getDataNascimento(), LocalDate.now()).getYears(),
                compras, contar(compras, ItemComprado::genero), contar(compras, ItemComprado::plataforma),
                preferencia == null ? Set.of() : new LinkedHashSet<>(preferencia.getGeneros()),
                preferencia == null ? Set.of() : new LinkedHashSet<>(preferencia.getPlataformas()),
                preferencia == null ? null : preferencia.getOrcamentoMaximo(),
                preferencia == null ? null : preferencia.getObservacoes(),
                jogosCitados(mensagemRepository.findByClienteIdAndAvaliacao(clienteId, AvaliacaoResposta.NAO_UTIL), titulos),
                jogosCitados(mensagemRepository.findByClienteIdAndAvaliacao(clienteId, AvaliacaoResposta.UTIL), titulos));
    }

    /** Agrega as compras efetivadas de todos os clientes. */
    @Transactional(readOnly = true)
    public DadosVendas dadosVendas() {
        Map<Long, Set<String>> titulosPorCliente = new HashMap<>();
        Map<String, Integer> vendasPorJogo = new HashMap<>();
        for (Pedido pedido : pedidoRepository.findByStatusIn(EFETIVADAS)) {
            for (PedidoItem item : pedido.getItens()) {
                titulosPorCliente.computeIfAbsent(pedido.getClienteId(), c -> new LinkedHashSet<>()).add(item.getTitulo());
                vendasPorJogo.merge(item.getTitulo(), item.getQuantidade(), Integer::sum);
            }
        }
        // cada cliente pesa 1 / (jogos que ele comprou - 1): quem compra de tudo quase não conta
        Map<String, Map<String, Double>> coCompras = new HashMap<>();
        for (Set<String> titulos : titulosPorCliente.values()) {
            if (titulos.size() < 2) continue;
            double peso = 1.0 / (titulos.size() - 1);
            for (String a : titulos) {
                for (String b : titulos) {
                    if (!a.equals(b)) coCompras.computeIfAbsent(a, t -> new HashMap<>()).merge(b, peso, Double::sum);
                }
            }
        }
        return new DadosVendas(coCompras, vendasPorJogo);
    }

    /** Ids dos clientes que têm compras efetivadas (base dos exemplos de venda no ajuste fino). */
    @Transactional(readOnly = true)
    public List<Long> clientesComCompras() {
        return pedidoRepository.findByStatusIn(EFETIVADAS).stream().map(Pedido::getClienteId).distinct().sorted().toList();
    }

    /** Pontua cada jogo do catálogo para o cliente e devolve os melhores, com os motivos. */
    public List<Recomendacao> recomendar(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas, int quantidade) {
        Set<String> comprados = perfil.titulosComprados();
        long totalComprado = perfil.compras().size();
        List<Recomendacao> resultado = new ArrayList<>();

        for (JogoCatalogo jogo : catalogo) {
            if (comprados.contains(jogo.titulo()) || !classificacaoPermitida(jogo.classificacao(), perfil.idade())
                    || !daPlataformaDoCliente(jogo, perfil) || !cabeNoOrcamento(jogo, perfil)) continue;
            double pontos = 0;
            List<String> motivos = new ArrayList<>();

            if (perfil.generosPreferidos().contains(jogo.genero())) {
                pontos += 3;
                motivos.add("é de " + jogo.genero() + ", um dos seus gêneros preferidos");
            } else if (perfil.generosComprados().containsKey(jogo.genero())) {
                pontos += 2.0 * perfil.generosComprados().get(jogo.genero()) / totalComprado;
                motivos.add("você já curtiu outros jogos de " + jogo.genero());
            }
            if (perfil.plataformasPreferidas().contains(jogo.plataforma())) {
                pontos += 2;
                motivos.add("sai para " + jogo.plataforma() + ", a plataforma que você prefere");
            } else if (perfil.plataformasCompradas().containsKey(jogo.plataforma())) {
                pontos += 1;
                motivos.add("roda no " + jogo.plataforma() + " que você já usa");
            }

            String maisParecido = null;
            double maiorCoCompra = 0.25; // abaixo disso o vínculo é fraco demais para virar motivo
            for (String comprado : comprados) {
                double peso = vendas.coCompras().getOrDefault(comprado, Map.of()).getOrDefault(jogo.titulo(), 0.0);
                pontos += Math.min(1.5, peso * 1.5);
                if (peso > maiorCoCompra) {
                    maiorCoCompra = peso;
                    maisParecido = comprado;
                }
            }
            if (maisParecido != null) motivos.add("quem comprou " + maisParecido + " também levou este jogo");

            if (perfil.jogosNaoUteis().contains(jogo.titulo())) pontos -= 4; // feedback negativo no chat
            if (perfil.jogosUteis().contains(jogo.titulo())) pontos += 0.5;
            if (motivos.isEmpty() && totalComprado == 0) {
                int vendidos = vendas.vendasPorJogo().getOrDefault(jogo.titulo(), 0);
                if (vendidos > 0) {
                    pontos += Math.min(2, vendidos * 0.2);
                    motivos.add("é um dos mais vendidos da loja");
                }
            }
            if (pontos > 0 && !motivos.isEmpty()) resultado.add(new Recomendacao(jogo, pontos, motivos));
        }

        resultado.sort(Comparator.comparingDouble(Recomendacao::pontuacao).reversed()
                .thenComparing(r -> -vendas.vendasPorJogo().getOrDefault(r.jogo().titulo(), 0))
                .thenComparing(r -> r.jogo().titulo()));
        // o mesmo jogo em duas plataformas do cliente: fica só a versão mais bem pontuada
        Set<String> vistos = new HashSet<>();
        return resultado.stream().filter(r -> vistos.add(r.jogo().titulo())).limit(quantidade).toList();
    }

    /**
     * Quem marcou plataformas no perfil só recebe jogos delas (um jogo de outra plataforma ele não tem como
     * jogar). Sem plataforma marcada, vale qualquer uma; o histórico de compras pesa na pontuação.
     */
    public static boolean daPlataformaDoCliente(JogoCatalogo jogo, PerfilCliente perfil) {
        return perfil.plataformasPreferidas().isEmpty() || perfil.plataformasPreferidas().contains(jogo.plataforma());
    }

    /** O orçamento por jogo do perfil é um limite: acima dele o jogo não é recomendado. */
    public static boolean cabeNoOrcamento(JogoCatalogo jogo, PerfilCliente perfil) {
        return perfil.orcamento() == null || jogo.preco().compareTo(perfil.orcamento()) <= 0;
    }

    /**
     * Instrução de sistema do assistente: papel, regras, catálogo, perfil do cliente e as sugestões que
     * a pontuação das vendas aponta para ele. É a mesma no chat e nos exemplos do ajuste fino.
     */
    public String instrucaoSistema(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        StringBuilder texto = new StringBuilder("""
                Você é o Nexus Assistente, atendente virtual da Nexus Games, uma loja online de jogos eletrônicos.
                Seu papel: recomendar jogos de forma personalizada, ajudar o cliente a encontrar jogos no catálogo, \
                responder dúvidas sobre jogos e sugerir conteúdos relevantes (jogos da mesma franquia ou estilo, \
                plataformas, dicas para começar a jogar).
                Regras:
                - Responda sempre em português do Brasil, de forma simpática e objetiva (até cerca de 120 palavras; \
                use tópicos ao listar jogos).
                - Recomende e cite preços apenas de jogos do CATÁLOGO abaixo, com título, plataforma e preço. \
                Não invente jogos, preços, estoque ou promoções da loja.
                - Use o PERFIL DO CLIENTE: priorize os gêneros preferidos e o histórico de compras. \
                Não recomende um jogo que o cliente já comprou, a menos que ele pergunte sobre esse jogo.
                - Se o cliente informou plataformas, recomende só jogos que saem para elas e cite só as plataformas \
                que estão nas preferências dele; \
                jogo de outra plataforma, só se ele pedir. O CATÁLOGO mostra só as plataformas do cliente; se ele pedir \
                outra, use o bloco JOGOS PARA A PLATAFORMA PEDIDA.
                - Respeite a classificação indicativa compatível com a idade do cliente.
                - O orçamento do perfil é o preço máximo por jogo: não recomende jogo acima dele. \
                Se houver o bloco JOGOS DENTRO DO VALOR PEDIDO, recomende só os jogos desse bloco.
                - Se o cliente pedir um gênero e houver o bloco JOGOS DO GÊNERO PEDIDO, recomende só os jogos desse bloco; \
                se ele disser que não há nenhum, diga isso ao cliente e não cite outro jogo desse gênero.
                - Evite os jogos que o cliente já marcou como recomendação não útil.
                - Ao recomendar, prefira as SUGESTÕES PARA ESTE CLIENTE, que vêm do histórico de vendas da loja.
                - Quando o cliente pedir uma recomendação, recomende na mesma resposta pelo menos um jogo das \
                SUGESTÕES PARA ESTE CLIENTE, citando o título exatamente como está no CATÁLOGO. Não responda \
                só com uma pergunta: o perfil já diz do que ele gosta.
                - Quando o cliente descrever o tipo de jogo que quer (criar coisas, relaxar, jogar com amigos, sentir medo...), \
                indique os jogos cujas características do CATÁLOGO combinam com o pedido. Se houver o bloco \
                JOGOS QUE COMBINAM COM O PEDIDO, recomende esses jogos no lugar das SUGESTÕES; se ele disser que \
                não há jogo disponível, diga isso e ofereça outra opção.
                - Explique em uma frase por que cada jogo combina com o cliente.
                - Em dúvidas gerais sobre jogos, responda com o que sabe; se não tiver certeza, diga isso.
                - Assuntos que não sejam jogos ou a loja (geografia, escola, receitas, política, clima...): NÃO responda \
                o conteúdo da pergunta; explique com educação que você só ajuda com jogos e ofereça uma recomendação.

                CATÁLOGO (jogos com estoque: título | plataformas | gênero | classificação | preço | destaques):
                """);
        // uma linha por jogo; quem marcou plataformas vê só os jogos delas (o prompt fica bem menor e o
        // treino cabe no tempo da CPU). Outra plataforma pedida na mensagem vem no bloco do pedido.
        List<String> todas = catalogo.stream().map(JogoCatalogo::plataforma).distinct().sorted().toList();
        if (perfil.plataformasPreferidas().isEmpty()) texto.append("(\"todas\" = ").append(String.join(", ", todas)).append(")\n");
        Map<String, List<JogoCatalogo>> versoes = catalogo.stream()
                .filter(j -> daPlataformaDoCliente(j, perfil))
                .collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()));
        for (List<JogoCatalogo> doJogo : versoes.values()) {
            JogoCatalogo jogo = doJogo.get(0);
            String plataformas = plataformas(doJogo);
            texto.append("- ").append(jogo.titulo()).append(" | ").append(plataformas.equals(String.join(", ", todas)) ? "todas" : plataformas)
                    .append(" | ").append(jogo.genero())
                    .append(" | ").append(jogo.classificacao()).append(" | ").append(precos(doJogo));
            if (jogo.caracteristicas() != null) texto.append(" | ").append(destaques(jogo.caracteristicas()));
            texto.append('\n');
        }

        texto.append("\nPERFIL DO CLIENTE:\nNome: ").append(perfil.nome());
        if (perfil.idade() != null) texto.append(" (").append(perfil.idade()).append(" anos)");
        texto.append("\nCompras: ");
        // as 10 compras mais recentes bastam; as contagens abaixo resumem o histórico inteiro
        List<ItemComprado> recentes = perfil.compras().subList(Math.max(0, perfil.compras().size() - 10), perfil.compras().size());
        texto.append(perfil.compras().isEmpty() ? "nenhuma compra ainda" : recentes.stream()
                .map(c -> c.titulo() + " (" + c.genero() + ", " + c.plataforma() + ", " + c.data().format(MES_ANO) + ")")
                .collect(Collectors.joining("; ")));
        if (perfil.compras().size() > recentes.size()) {
            texto.append(" (e mais ").append(perfil.compras().size() - recentes.size()).append(" compras anteriores)");
        }
        if (!perfil.compras().isEmpty()) {
            texto.append("\nGêneros mais comprados: ").append(contagem(perfil.generosComprados()));
            texto.append("\nPlataformas mais compradas: ").append(contagem(perfil.plataformasCompradas()));
        }
        texto.append("\nPreferências declaradas: ");
        List<String> preferencias = new ArrayList<>();
        if (!perfil.generosPreferidos().isEmpty()) preferencias.add("gêneros " + String.join(", ", perfil.generosPreferidos()));
        if (!perfil.plataformasPreferidas().isEmpty()) preferencias.add("plataformas " + String.join(", ", perfil.plataformasPreferidas()));
        if (perfil.orcamento() != null) preferencias.add("orçamento de até " + moeda(perfil.orcamento()) + " por jogo");
        if (perfil.observacoes() != null) preferencias.add("observações: \"" + perfil.observacoes() + "\"");
        texto.append(preferencias.isEmpty() ? "nenhuma informada" : String.join("; ", preferencias));
        List<String> naoUteis = perfil.jogosNaoUteis().stream().filter(t -> !perfil.titulosComprados().contains(t)).toList();
        if (!naoUteis.isEmpty()) {
            texto.append("\nRecomendações que o cliente marcou como não úteis: ").append(String.join(", ", naoUteis));
        }

        List<Recomendacao> sugestoes = recomendar(perfil, catalogo, vendas, 5);
        texto.append("\n\nSUGESTÕES PARA ESTE CLIENTE (da mais para a menos indicada):");
        if (sugestoes.isEmpty() && perfil.orcamento() != null) {
            texto.append("\n- nenhum jogo").append(restricoes(perfil))
                    .append(" combina com o perfil: diga isso e pergunte se ele aceita um valor maior");
        } else if (sugestoes.isEmpty()) {
            texto.append("\n- nenhuma ainda: pergunte do que ele gosta");
        }
        for (Recomendacao r : sugestoes) {
            texto.append("\n- ").append(r.jogo().titulo()).append(" (").append(r.jogo().plataforma()).append(", ")
                    .append(r.jogo().genero()).append(", ").append(moeda(r.jogo().preco())).append("): ")
                    .append(String.join("; ", r.motivos()));
        }
        // nenhuma sugestão dos gêneros preferidos: o modelo precisa saber o porquê para avisar o cliente
        if (!sugestoes.isEmpty() && !perfil.generosPreferidos().isEmpty()
                && sugestoes.stream().noneMatch(r -> perfil.generosPreferidos().contains(r.jogo().genero()))) {
            texto.append("\nObservação: não há jogo de ").append(String.join(" ou ", perfil.generosPreferidos()))
                    .append(restricoes(perfil)).append(" que ele ainda não tenha; avise isso antes de sugerir as opções acima.");
        }
        return texto.toString();
    }

    /** " até R$ 100,00 para Xbox Series X|S": os limites do perfil, para explicar por que não há jogo. */
    private static String restricoes(PerfilCliente perfil) {
        return (perfil.orcamento() == null ? "" : " até " + moeda(perfil.orcamento()))
                + (perfil.plataformasPreferidas().isEmpty() ? "" : " para " + String.join(" ou ", perfil.plataformasPreferidas()));
    }

    /**
     * Instrução de sistema para uma mensagem do cliente: a base mais os blocos do pedido (estilo, valor e
     * gênero), com os jogos já filtrados pelo perfil. É a mesma no chat e nos exemplos do ajuste fino.
     */
    public String comBlocosDoPedido(String instrucao, String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo,
                                    DadosVendas vendas) {
        // "tem algo para o Switch?": os blocos usam a plataforma pedida no lugar das do perfil
        Set<String> pedidas = plataformasDoPedido(mensagem, catalogo);
        PerfilCliente efetivo = pedidas.isEmpty() ? perfil : perfil.comPlataformas(pedidas);
        String blocos = estilos.blocoDoPedido(mensagem, efetivo, catalogo) + blocoOrcamento(mensagem, efetivo, catalogo, vendas)
                + blocoGenero(mensagem, efetivo, catalogo, vendas);
        if (!pedidas.isEmpty() && !perfil.plataformasPreferidas().containsAll(pedidas)) {
            blocos += blocoPlataforma(efetivo, catalogo, vendas);
        }
        return instrucao + blocos;
    }

    /** Plataformas citadas na mensagem ("pro Switch", "no PC", "de PS5"). */
    public static Set<String> plataformasDoPedido(String mensagem, List<JogoCatalogo> catalogo) {
        String texto = semAcento(mensagem == null ? "" : mensagem);
        Set<String> pedidas = new LinkedHashSet<>();
        for (String plataforma : catalogo.stream().map(JogoCatalogo::plataforma).distinct().sorted().toList()) {
            String regex = switch (plataforma) {
                case "PC" -> "\\b(pc|computador)\\b";
                case "Nintendo Switch" -> "\\b(switch|nintendo)\\b";
                case "PlayStation 5" -> "\\b(playstation|ps5|ps 5)\\b";
                case "Xbox Series X|S" -> "\\bxbox\\b";
                default -> "\\b" + Pattern.quote(semAcento(plataforma)) + "\\b";
            };
            if (Pattern.compile(regex).matcher(texto).find()) pedidas.add(plataforma);
        }
        return pedidas;
    }

    /** Bloco com os jogos indicados na plataforma que o cliente pediu e que não está no perfil dele. */
    private String blocoPlataforma(PerfilCliente efetivo, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        String plataformas = String.join(" ou ", efetivo.plataformasPreferidas());
        List<Recomendacao> jogos = recomendarOuMaisVendidos(efetivo, catalogo, vendas, 5);
        StringBuilder texto = new StringBuilder("\n\nJOGOS PARA A PLATAFORMA PEDIDA (").append(plataformas).append("):");
        if (jogos.isEmpty()) texto.append("\n- nenhum jogo").append(restricoes(efetivo)).append(" que o cliente ainda não tenha");
        for (Recomendacao r : jogos) {
            texto.append("\n- ").append(r.jogo().titulo()).append(" (").append(r.jogo().plataforma()).append(", ")
                    .append(r.jogo().genero()).append(", ").append(moeda(r.jogo().preco())).append("): ")
                    .append(String.join("; ", r.motivos()));
        }
        return texto.toString();
    }

    /** Recomendações da plataforma; se nada pontua (gênero fora do gosto), os mais vendidos dela que respeitam o perfil. */
    public List<Recomendacao> recomendarOuMaisVendidos(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas, int quantidade) {
        List<Recomendacao> indicados = recomendar(perfil, catalogo, vendas, quantidade);
        if (!indicados.isEmpty()) return indicados;
        Set<String> vistos = new HashSet<>();
        return catalogo.stream()
                .filter(j -> !perfil.titulosComprados().contains(j.titulo()))
                .filter(j -> classificacaoPermitida(j.classificacao(), perfil.idade()))
                .filter(j -> daPlataformaDoCliente(j, perfil) && cabeNoOrcamento(j, perfil))
                .sorted(Comparator.comparing((JogoCatalogo j) -> -vendas.vendasPorJogo().getOrDefault(j.titulo(), 0))
                        .thenComparing(JogoCatalogo::titulo))
                .filter(j -> vistos.add(j.titulo()))
                .limit(quantidade)
                .map(j -> new Recomendacao(j, 0, List.of("sai para " + j.plataforma())))
                .toList();
    }

    /** As 3 primeiras características: o resto não muda a escolha e deixa o prompt (e o treino) bem mais longo. */
    private static String destaques(String caracteristicas) {
        String[] partes = caracteristicas.split(",\\s*");
        return String.join(", ", java.util.Arrays.copyOf(partes, Math.min(3, partes.length)));
    }

    /** Gênero do catálogo citado na mensagem ("quero um jogo de corrida", "tem RPG?"), se houver. */
    public static String generoDoPedido(String mensagem, List<JogoCatalogo> catalogo) {
        if (mensagem == null) return null;
        String texto = semAcento(mensagem);
        for (String genero : catalogo.stream().map(JogoCatalogo::genero).distinct().sorted().toList()) {
            String nome = Pattern.quote(semAcento(genero));
            // "plataforma" e "festa" também são palavras comuns ("minhas plataformas", "festa com amigos"):
            // só contam como gênero em "jogo de plataforma", "jogos de festa"
            String regex = genero.equals("Plataforma") || genero.equals("Festa")
                    ? "\\b(?:de|do|da)\\s+" + nome + "s?\\b" : "\\b" + nome + "s?\\b";
            if (Pattern.compile(regex).matcher(texto).find()) return genero;
        }
        return null;
    }

    /**
     * Bloco do prompt com os jogos do gênero pedido que servem para o cliente (plataformas, orçamento, idade e o
     * que ele ainda não tem). Vazio quando a mensagem não cita gênero.
     */
    public String blocoGenero(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        String genero = generoDoPedido(mensagem, catalogo);
        if (genero == null) return "";
        List<Recomendacao> jogos = doGenero(perfil, catalogo, vendas, genero);
        StringBuilder texto = new StringBuilder("\n\nJOGOS DO GÊNERO PEDIDO (").append(genero).append(", dentro do perfil do cliente):");
        if (jogos.isEmpty()) {
            texto.append("\n- nenhum jogo de ").append(genero).append(restricoes(perfil))
                    .append(" que o cliente ainda não tenha: diga isso e pergunte se ele aceita outro valor, outra plataforma ou outro gênero");
        }
        for (Recomendacao r : jogos) {
            texto.append("\n- ").append(r.jogo().titulo()).append(" (").append(r.jogo().plataforma()).append(", ")
                    .append(moeda(r.jogo().preco())).append("): ").append(String.join("; ", r.motivos()));
        }
        return texto.toString();
    }

    /** Até 3 jogos do gênero que servem para o cliente, do mais para o menos indicado. */
    public List<Recomendacao> doGenero(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas, String genero) {
        List<JogoCatalogo> doGenero = catalogo.stream().filter(j -> j.genero().equals(genero)).toList();
        List<Recomendacao> indicados = recomendar(perfil, doGenero, vendas, 3);
        if (!indicados.isEmpty()) return indicados;
        // gênero fora do gosto dele: vale qualquer jogo do gênero que respeite o perfil
        Set<String> vistos = new HashSet<>();
        return doGenero.stream()
                .filter(j -> !perfil.titulosComprados().contains(j.titulo()))
                .filter(j -> classificacaoPermitida(j.classificacao(), perfil.idade()))
                .filter(j -> daPlataformaDoCliente(j, perfil) && cabeNoOrcamento(j, perfil))
                .sorted(Comparator.comparing(JogoCatalogo::preco).thenComparing(JogoCatalogo::titulo))
                .filter(j -> vistos.add(j.titulo()))
                .limit(3)
                .map(j -> new Recomendacao(j, 0, List.of("é de " + genero + ", o gênero que você pediu")))
                .toList();
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Valor pedido na mensagem ("tem algo até R$ 150?", "menos de 100 reais"), se houver. */
    public static BigDecimal valorDoPedido(String mensagem) {
        if (mensagem == null) return null;
        Matcher valor = VALOR_PEDIDO.matcher(mensagem);
        return valor.find() ? new BigDecimal(valor.group(1).replace(',', '.')) : null;
    }

    /**
     * Bloco do prompt com as recomendações que cabem no valor pedido na mensagem. Vazio quando a mensagem
     * não fala de valor. Usado igual no chat e nos exemplos do ajuste fino.
     */
    public String blocoOrcamento(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        BigDecimal limite = valorDoPedido(mensagem);
        if (limite == null) return "";
        List<Recomendacao> cabem = dentroDoValor(perfil, catalogo, vendas, limite);
        StringBuilder texto = new StringBuilder("\n\nJOGOS DENTRO DO VALOR PEDIDO (até ").append(moeda(limite)).append("):");
        if (cabem.isEmpty()) texto.append("\n- nenhum jogo do catálogo até esse valor");
        for (Recomendacao r : cabem) {
            texto.append("\n- ").append(r.jogo().titulo()).append(" (").append(r.jogo().plataforma()).append(", ")
                    .append(moeda(r.jogo().preco())).append("): ").append(String.join("; ", r.motivos()));
        }
        return texto.toString();
    }

    /**
     * Até 3 jogos que cabem no valor: primeiro os que combinam com o perfil; se nenhum combinar, os mais
     * em conta do catálogo (o cliente pediu um valor, então é melhor mostrar o que cabe nele do que nada).
     */
    public List<Recomendacao> dentroDoValor(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas, BigDecimal limite) {
        List<JogoCatalogo> cabem = catalogo.stream().filter(j -> j.preco().compareTo(limite) <= 0).toList();
        // o valor pedido na mensagem substitui o orçamento do perfil (o cliente pode pedir mais ou menos)
        List<Recomendacao> doPerfil = recomendar(perfil.comOrcamento(limite), cabem, vendas, 3);
        if (!doPerfil.isEmpty()) return doPerfil;
        Set<String> vistos = new HashSet<>();
        return cabem.stream()
                .filter(j -> !perfil.titulosComprados().contains(j.titulo()))
                .filter(j -> classificacaoPermitida(j.classificacao(), perfil.idade()))
                .filter(j -> daPlataformaDoCliente(j, perfil))
                .sorted(Comparator.comparing(JogoCatalogo::preco).thenComparing(JogoCatalogo::titulo))
                .filter(j -> vistos.add(j.titulo()))
                .limit(3)
                .map(j -> new Recomendacao(j, 0, List.of("é de " + j.genero() + " e cabe no valor que você pediu")))
                .toList();
    }

    /** "PC, Nintendo Switch": as plataformas em que o jogo está à venda. */
    public static String plataformas(List<JogoCatalogo> versoes) {
        return versoes.stream().map(JogoCatalogo::plataforma).distinct().sorted().collect(Collectors.joining(", "));
    }

    /** Um preço só quando todas as versões custam o mesmo; senão, o preço de cada plataforma. */
    public static String precos(List<JogoCatalogo> versoes) {
        if (versoes.stream().map(JogoCatalogo::preco).distinct().count() == 1) return moeda(versoes.get(0).preco());
        return versoes.stream().map(j -> j.plataforma() + " " + moeda(j.preco())).collect(Collectors.joining(", "));
    }

    public static String moeda(BigDecimal valor) {
        return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", valor);
    }

    public static JogoCatalogo paraCatalogo(Jogo jogo) {
        return new JogoCatalogo(jogo.getId(), jogo.getTitulo(), jogo.getPlataforma(), jogo.getGenero(),
                jogo.getClassificacaoIndicativa(), jogo.getAno(), jogo.getDesenvolvedora(), jogo.getPreco(), jogo.getEstoque(),
                jogo.getCaracteristicas());
    }

    /** Classificação indicativa compatível com a idade (L e 10 a 18 anos). */
    public static boolean classificacaoPermitida(String classificacao, Integer idade) {
        if (idade == null || classificacao == null || classificacao.equalsIgnoreCase("L")) return true;
        try {
            return idade >= Integer.parseInt(classificacao);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static Map<String, Long> contar(List<ItemComprado> compras, java.util.function.Function<ItemComprado, String> chave) {
        return compras.stream().collect(Collectors.groupingBy(chave, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static String contagem(Map<String, Long> mapa) {
        return mapa.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining(", "));
    }

    /** Títulos do catálogo citados nas respostas (para saber o que o cliente achou útil ou não). */
    private static Set<String> jogosCitados(List<MensagemAssistente> mensagens, List<String> titulos) {
        Set<String> citados = new LinkedHashSet<>();
        for (MensagemAssistente mensagem : mensagens) {
            String resposta = mensagem.getResposta().toLowerCase();
            titulos.stream().filter(t -> resposta.contains(t.toLowerCase())).forEach(citados::add);
        }
        return citados;
    }
}
