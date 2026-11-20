package com.games.recomendacao.config;

import com.games.cliente.adapter.in.web.dto.request.CadastrarClienteRequest;
import com.games.cliente.adapter.out.persistence.repository.ClienteRepository;
import com.games.cliente.application.service.ClienteService;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.Bandeira;
import com.games.cliente.domain.enums.GeneroCliente;
import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.adapter.out.persistence.repository.PreferenciaClienteRepository;
import com.games.recomendacao.application.service.PerfilRecomendacaoService;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.Recomendacao;
import com.games.recomendacao.application.service.RedacaoRespostas;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.entity.PreferenciaCliente;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import com.games.vendas.application.service.FreteService;
import com.games.vendas.domain.entity.EnderecoEntrega;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Massa de dados do assistente de recomendação (RNF0044): seis clientes com históricos de compra
 * distintos e preferências no perfil, e conversas do chat já avaliadas (útil / não útil), em
 * quantidade suficiente para o primeiro ajuste fino. Só é carregada com a base sem esses clientes.
 *
 * <ul>
 *   <li>Ana Souza — RPG e aventura, PC e PlayStation 5, orçamento de R$ 250;</li>
 *   <li>Bruno Lima — esporte e corrida, PlayStation 5 e Xbox;</li>
 *   <li>Carla Mendes — plataforma e sandbox no Switch, para a família;</li>
 *   <li>Diego Rocha — ação no PlayStation 5;</li>
 *   <li>Elisa Martins — indies de plataforma e sandbox no PC, orçamento de R$ 100;</li>
 *   <li>Felipe Alves — corrida e ação no Xbox.</li>
 * </ul>
 */
@Component
@Order(3)
public class CargaAssistente implements ApplicationRunner {

    private static final String MODELO_MASSA = "massa de dados (conversa simulada)";

    private record ClienteMassa(String nome, GeneroCliente genero, LocalDate nascimento, String cpfBase, String email,
                                List<String> generos, List<String> plataformas, String orcamento, String observacoes,
                                List<String> compras) {}

    private static final List<ClienteMassa> CLIENTES = List.of(
            new ClienteMassa("Ana Souza", GeneroCliente.FEMININO, LocalDate.of(1994, 3, 12), "123456789", "ana.souza@nexus.com.br",
                    List.of("RPG", "Aventura"), List.of("PC", "PlayStation 5"), "250.00", "Gosto de histórias longas e mundo aberto.",
                    List.of("Elden Ring", "Cyberpunk 2077", "Hogwarts Legacy")),
            new ClienteMassa("Bruno Lima", GeneroCliente.MASCULINO, LocalDate.of(1999, 8, 25), "987654321", "bruno.lima@nexus.com.br",
                    List.of("Esporte", "Corrida"), List.of("PlayStation 5", "Xbox Series X|S"), null, "Jogo online com os amigos.",
                    List.of("EA Sports FC 25", "Forza Horizon 5", "NBA 2K25")),
            new ClienteMassa("Carla Mendes", GeneroCliente.FEMININO, LocalDate.of(1988, 11, 3), "111444777", "carla.mendes@nexus.com.br",
                    List.of("Plataforma", "Sandbox"), List.of("Nintendo Switch"), "300.00", "Compro para jogar com meus filhos de 8 e 10 anos.",
                    List.of("Super Mario Bros. Wonder", "Minecraft")),
            new ClienteMassa("Diego Rocha", GeneroCliente.MASCULINO, LocalDate.of(1990, 6, 18), "222555888", "diego.rocha@nexus.com.br",
                    List.of("Ação", "Aventura"), List.of("PlayStation 5"), null, null,
                    List.of("God of War Ragnarök", "Marvel's Spider-Man 2")),
            new ClienteMassa("Elisa Martins", GeneroCliente.FEMININO, LocalDate.of(1996, 1, 27), "333666999", "elisa.martins@nexus.com.br",
                    List.of("Plataforma", "Sandbox"), List.of("PC"), "100.00", "Prefiro jogos independentes e baratos.",
                    List.of("Hollow Knight", "Terraria", "Cyberpunk 2077")),
            new ClienteMassa("Felipe Alves", GeneroCliente.MASCULINO, LocalDate.of(1985, 9, 9), "444777000", "felipe.alves@nexus.com.br",
                    List.of("Corrida"), List.of("Xbox Series X|S"), null, "Tenho volante e gosto de simulação.",
                    List.of("Forza Horizon 5", "Red Dead Redemption 2")));

    private final ClienteService clienteService;
    private final ClienteRepository clienteRepository;
    private final JogoRepository jogoRepository;
    private final PedidoRepository pedidoRepository;
    private final FreteService freteService;
    private final PreferenciaClienteRepository preferenciaRepository;
    private final MensagemAssistenteRepository mensagemRepository;
    private final PerfilRecomendacaoService perfis;
    private final RedacaoRespostas redacao;

    public CargaAssistente(ClienteService clienteService, ClienteRepository clienteRepository, JogoRepository jogoRepository,
                           PedidoRepository pedidoRepository, FreteService freteService,
                           PreferenciaClienteRepository preferenciaRepository, MensagemAssistenteRepository mensagemRepository,
                           PerfilRecomendacaoService perfis, RedacaoRespostas redacao) {
        this.clienteService = clienteService;
        this.clienteRepository = clienteRepository;
        this.jogoRepository = jogoRepository;
        this.pedidoRepository = pedidoRepository;
        this.freteService = freteService;
        this.preferenciaRepository = preferenciaRepository;
        this.mensagemRepository = mensagemRepository;
        this.perfis = perfis;
        this.redacao = redacao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clienteRepository.existsByEmail(CLIENTES.get(0).email())) return;
        Map<String, Jogo> jogos = jogoRepository.findAllByOrderByTituloAsc().stream()
                .collect(Collectors.toMap(Jogo::getTitulo, Function.identity(), (a, b) -> a));

        List<Long> ids = new ArrayList<>();
        for (ClienteMassa dados : CLIENTES) {
            Cliente cliente = clienteService.cadastrar(new CadastrarClienteRequest(dados.genero(), dados.nome(), dados.nascimento(),
                    cpf(dados.cpfBase()), "Celular", "11", "98765" + dados.cpfBase().substring(0, 4), dados.email(),
                    "Nexus@2026", "Nexus@2026", "Casa", "Rua", "Rua dos Jogadores", "100", "Centro", "01310100",
                    "São Paulo", "SP", "Brasil"));
            ids.add(cliente.getId());
            for (int i = 0; i < dados.compras().size(); i++) {
                comprar(cliente, jogos.get(dados.compras().get(i)), 8 - 2 * i);
            }
            PreferenciaCliente preferencia = new PreferenciaCliente(cliente.getId());
            preferencia.atualizar(dados.generos(), dados.plataformas(),
                    dados.orcamento() == null ? null : new BigDecimal(dados.orcamento()), dados.observacoes());
            preferenciaRepository.save(preferencia);
        }

        // conversas avaliadas: 4 úteis e 2 não úteis por cliente
        List<JogoCatalogo> catalogo = perfis.catalogo();
        DadosVendas vendas = perfis.dadosVendas();
        for (int i = 0; i < ids.size(); i++) {
            registrarConversas(perfis.perfil(ids.get(i)), catalogo, vendas, i);
        }
    }

    private void registrarConversas(PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas, int indice) {
        List<Recomendacao> recomendacoes = perfis.recomendar(perfil, catalogo, vendas, 3);
        if (recomendacoes.isEmpty()) return;
        String genero = perfil.generoPrincipal();
        String plataforma = perfil.plataformasPreferidas().iterator().next();
        int diasAtras = 60 - indice * 5;

        avaliada(perfil, diasAtras, RedacaoRespostas.PEDIDOS_RECOMENDACAO.get(indice % RedacaoRespostas.PEDIDOS_RECOMENDACAO.size()),
                redacao.recomendacao(perfil, recomendacoes, indice), AvaliacaoResposta.UTIL);
        avaliada(perfil, diasAtras - 7, "Quais jogos de " + genero + " vocês têm?",
                redacao.busca(genero, catalogo.stream().filter(j -> j.genero().equals(genero)).toList(), perfil), AvaliacaoResposta.UTIL);
        avaliada(perfil, diasAtras - 14, "Me fala mais sobre o " + recomendacoes.get(0).jogo().titulo(),
                redacao.sobreJogo(recomendacoes.get(0).jogo()), AvaliacaoResposta.UTIL);
        if (perfil.orcamento() != null) {
            avaliada(perfil, diasAtras - 21, "Tem alguma coisa até " + PerfilRecomendacaoService.moeda(perfil.orcamento()) + "?",
                    redacao.orcamento(perfil.orcamento(), perfis.recomendar(perfil, catalogo.stream()
                            .filter(j -> j.preco().compareTo(perfil.orcamento()) <= 0).toList(), vendas, 3)), AvaliacaoResposta.UTIL);
        } else {
            avaliada(perfil, diasAtras - 21, "Vocês têm jogos para " + plataforma + "?",
                    redacao.buscaPorPlataforma(plataforma, catalogo.stream().filter(j -> j.plataforma().equals(plataforma)).toList(), perfil),
                    AvaliacaoResposta.UTIL);
        }

        // respostas ruins, que o cliente marcou como não úteis: um jogo que ele já tem e um fora do seu gosto
        String jaComprado = perfil.compras().get(0).titulo();
        avaliada(perfil, diasAtras - 28, "Me recomenda um jogo?",
                "Você vai adorar **" + jaComprado + "**! É um dos jogos mais populares do nosso catálogo.", AvaliacaoResposta.NAO_UTIL);
        catalogo.stream()
                .filter(j -> !perfil.generosPreferidos().contains(j.genero()) && !perfil.plataformasPreferidas().contains(j.plataforma())
                        && !perfil.titulosComprados().contains(j.titulo()))
                .findFirst()
                .ifPresent(j -> avaliada(perfil, diasAtras - 35, "Quero uma sugestão de jogo.",
                        "Recomendo **" + j.titulo() + "** (" + j.plataforma() + "), que está fazendo sucesso.", AvaliacaoResposta.NAO_UTIL));
    }

    private void avaliada(PerfilCliente perfil, int diasAtras, String pergunta, String resposta, AvaliacaoResposta avaliacao) {
        MensagemAssistente mensagem = new MensagemAssistente(perfil.clienteId(), "massa-" + UUID.randomUUID(), pergunta, resposta, MODELO_MASSA);
        mensagem.avaliar(avaliacao);
        LocalDateTime quando = LocalDateTime.now().minusDays(diasAtras).withHour(19).withMinute(30);
        mensagem.definirDatas(quando, quando.plusMinutes(2));
        mensagemRepository.save(mensagem);
    }

    private void comprar(Cliente c, Jogo jogo, int mesesAtras) {
        EnderecoEntrega endereco = new EnderecoEntrega("Residencial", c.getEnderecoTipoResidencia(),
                c.getEnderecoTipoLogradouro(), c.getEnderecoLogradouro(), c.getEnderecoNumero(), c.getEnderecoBairro(),
                c.getEnderecoCep(), c.getEnderecoCidade(), c.getEnderecoEstado(), c.getPais());
        Pedido pedido = new Pedido("PED-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                c.getId(), endereco, freteService.calcular(jogo.getPesoKg(), c.getEnderecoEstado()));
        pedido.adicionarItem(jogo, 1);
        pedido.adicionarPagamentoCartao(null, "4111111111111111", c.getNome().toUpperCase(), Bandeira.VISA, pedido.getTotal());
        pedido.getPagamentos().forEach(p -> p.registrarRetornoOperadora(true));
        pedido.aprovar();
        pedido.despachar();
        pedido.confirmarEntrega();
        Pedido salvo = pedidoRepository.save(pedido);
        pedidoRepository.definirDataCompra(salvo.getId(), LocalDateTime.now().minusMonths(mesesAtras).withHour(15).withMinute(0));
    }

    /** CPF válido a partir dos 9 primeiros dígitos (calcula os dígitos verificadores). */
    private static String cpf(String base) {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) d[i] = base.charAt(i) - '0';
        for (int posicao = 9; posicao < 11; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) soma += d[i] * (posicao + 1 - i);
            int resto = soma % 11;
            d[posicao] = resto < 2 ? 0 : 11 - resto;
        }
        String n = java.util.Arrays.stream(d).mapToObj(String::valueOf).collect(Collectors.joining());
        return n.substring(0, 3) + "." + n.substring(3, 6) + "." + n.substring(6, 9) + "-" + n.substring(9);
    }
}
