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
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    }

    public record Recomendacao(JogoCatalogo jogo, double pontuacao, List<String> motivos) {}

    /** Vendas agregadas: força de cada par "comprou X e também Y" (ponderada por cliente) e o total por jogo. */
    public record DadosVendas(Map<String, Map<String, Double>> coCompras, Map<String, Integer> vendasPorJogo) {}

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final JogoRepository jogoRepository;
    private final PreferenciaClienteRepository preferenciaRepository;
    private final MensagemAssistenteRepository mensagemRepository;

    public PerfilRecomendacaoService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
                                     JogoRepository jogoRepository, PreferenciaClienteRepository preferenciaRepository,
                                     MensagemAssistenteRepository mensagemRepository) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.jogoRepository = jogoRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.mensagemRepository = mensagemRepository;
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
            if (comprados.contains(jogo.titulo()) || !classificacaoPermitida(jogo.classificacao(), perfil.idade())) continue;
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
            if (perfil.orcamento() != null && jogo.preco().compareTo(perfil.orcamento()) > 0) pontos -= 3;
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
        return resultado.subList(0, Math.min(quantidade, resultado.size()));
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
                - Use o PERFIL DO CLIENTE: priorize os gêneros e plataformas preferidos e o histórico de compras. \
                Não recomende um jogo que o cliente já comprou, a menos que ele pergunte sobre esse jogo.
                - Respeite o orçamento informado e a classificação indicativa compatível com a idade do cliente. \
                Se houver o bloco JOGOS DENTRO DO VALOR PEDIDO, recomende só os jogos desse bloco.
                - Evite os jogos que o cliente já marcou como recomendação não útil.
                - Ao recomendar, prefira as SUGESTÕES PARA ESTE CLIENTE, que vêm do histórico de vendas da loja.
                - Quando o cliente descrever o tipo de jogo que quer (criar coisas, relaxar, jogar com amigos, sentir medo...), \
                indique os jogos cujas características do CATÁLOGO combinam com o pedido. Se houver o bloco \
                JOGOS QUE COMBINAM COM O PEDIDO, recomende esses jogos no lugar das SUGESTÕES; se ele disser que \
                não há jogo disponível, diga isso e ofereça outra opção.
                - Explique em uma frase por que cada jogo combina com o cliente.
                - Em dúvidas gerais sobre jogos, responda com o que sabe; se não tiver certeza, diga isso.
                - Assuntos que não sejam jogos ou a loja (geografia, escola, receitas, política, clima...): NÃO responda \
                o conteúdo da pergunta; explique com educação que você só ajuda com jogos e ofereça uma recomendação.

                CATÁLOGO (jogos com estoque: título | plataforma | gênero | classificação | preço | características):
                """);
        for (JogoCatalogo jogo : catalogo) {
            texto.append("- ").append(jogo.titulo()).append(" | ").append(jogo.plataforma()).append(" | ").append(jogo.genero())
                    .append(" | ").append(jogo.classificacao()).append(" | ").append(moeda(jogo.preco()));
            if (jogo.caracteristicas() != null) texto.append(" | ").append(jogo.caracteristicas());
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
        if (sugestoes.isEmpty()) texto.append("\n- nenhuma ainda: pergunte do que ele gosta");
        for (Recomendacao r : sugestoes) {
            texto.append("\n- ").append(r.jogo().titulo()).append(" (").append(r.jogo().plataforma()).append(", ")
                    .append(r.jogo().genero()).append(", ").append(moeda(r.jogo().preco())).append("): ")
                    .append(String.join("; ", r.motivos()));
        }
        return texto.toString();
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
        List<Recomendacao> doPerfil = recomendar(perfil, cabem, vendas, 3);
        if (!doPerfil.isEmpty()) return doPerfil;
        return cabem.stream()
                .filter(j -> !perfil.titulosComprados().contains(j.titulo()))
                .filter(j -> classificacaoPermitida(j.classificacao(), perfil.idade()))
                .sorted(Comparator.comparing(JogoCatalogo::preco).thenComparing(JogoCatalogo::titulo))
                .limit(3)
                .map(j -> new Recomendacao(j, 0, List.of("é de " + j.genero() + " e cabe no valor que você pediu")))
                .toList();
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
