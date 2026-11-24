package com.games.recomendacao.application.service;

import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.application.gateway.IaGenerativa;
import com.games.recomendacao.application.gateway.IaGenerativa.Turno;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.Recomendacao;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import com.games.vendas.adapter.out.persistence.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o dataset do ajuste fino (RNF0044, item 3) a partir de dois tipos de dado:
 * <ul>
 *   <li><b>Vendas</b>: para cada cliente com compras efetivadas, conversas-exemplo em que o assistente
 *   recomenda os jogos que a pontuação aponta (preferências + histórico + "quem comprou X também levou Y"),
 *   busca por gênero, orçamento e detalhes de um jogo;</li>
 *   <li><b>Feedback</b>: as respostas reais do chat avaliadas como <i>úteis</i> entram como exemplos; as
 *   <i>não úteis</i> ficam de fora e tiram pontos dos jogos que sugeriram.</li>
 * </ul>
 * Cada linha traz a mesma instrução de sistema usada no chat, com o perfil daquele cliente.
 */
@Service
public class DatasetTreinamentoService {

    public record DatasetTreinamento(String jsonl, int exemplosVendas, int exemplosFeedback, int feedbackDescartado,
                                     int clientes, long vendasConsideradas, long feedbacksConsiderados) {
        public int total() {
            return exemplosVendas + exemplosFeedback;
        }
    }

    private static final int PEDIDOS_POR_CLIENTE = 2;
    private static final int ESTILOS_POR_CLIENTE = 3;
    // o modelo base tende a responder qualquer pergunta: mais exemplos de recusa reforçam a regra
    private static final int FORA_DO_ASSUNTO_POR_CLIENTE = 3;

    private final PerfilRecomendacaoService perfis;
    private final RedacaoRespostas redacao;
    private final EstilosDeJogo estilos;
    private final IaGenerativa ia;
    private final MensagemAssistenteRepository mensagemRepository;
    private final PedidoRepository pedidoRepository;

    public DatasetTreinamentoService(PerfilRecomendacaoService perfis, RedacaoRespostas redacao, EstilosDeJogo estilos, IaGenerativa ia,
                                     MensagemAssistenteRepository mensagemRepository, PedidoRepository pedidoRepository) {
        this.perfis = perfis;
        this.redacao = redacao;
        this.estilos = estilos;
        this.ia = ia;
        this.mensagemRepository = mensagemRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public DatasetTreinamento montar() {
        List<JogoCatalogo> catalogo = perfis.catalogo();
        DadosVendas vendas = perfis.dadosVendas();
        Map<Long, PerfilCliente> perfilPorCliente = new HashMap<>();
        List<String> linhas = new ArrayList<>();

        // 1) exemplos gerados das vendas
        int exemplosVendas = 0;
        List<Long> clientes = perfis.clientesComCompras();
        for (int k = 0; k < clientes.size(); k++) {
            PerfilCliente perfil = perfilPorCliente.computeIfAbsent(clientes.get(k), perfis::perfil);
            List<Recomendacao> recomendacoes = perfis.recomendar(perfil, catalogo, vendas, 3);
            if (recomendacoes.isEmpty()) continue;
            String instrucao = perfis.instrucaoSistema(perfil, catalogo, vendas);

            // pedidos de recomendação: formas diferentes de um cliente para outro (6 no total)
            for (int i = 0; i < PEDIDOS_POR_CLIENTE; i++) {
                int forma = (k + i * 3) % RedacaoRespostas.PEDIDOS_RECOMENDACAO.size();
                linhas.add(ia.linhaDataset(prompt(instrucao, RedacaoRespostas.PEDIDOS_RECOMENDACAO.get(forma), perfil, catalogo, vendas), List.of(
                        new Turno(true, RedacaoRespostas.PEDIDOS_RECOMENDACAO.get(forma)),
                        new Turno(false, redacao.recomendacao(perfil, recomendacoes, forma)))));
                exemplosVendas++;
            }

            // jogos parecidos com o último que o cliente comprou (sugestão de conteúdo relevante)
            if (!perfil.compras().isEmpty()) {
                String base = perfil.compras().get(perfil.compras().size() - 1).titulo();
                String generoBase = perfil.compras().get(perfil.compras().size() - 1).genero();
                List<Recomendacao> parecidos = perfis.recomendar(perfil, catalogo.stream()
                        .filter(j -> j.genero().equals(generoBase) || vendas.coCompras().getOrDefault(base, Map.of()).containsKey(j.titulo()))
                        .toList(), vendas, 3);
                String perguntaParecidos = "O que mais combina com quem gosta de " + base + "?";
                linhas.add(ia.linhaDataset(prompt(instrucao, perguntaParecidos, perfil, catalogo, vendas), List.of(
                        new Turno(true, perguntaParecidos),
                        new Turno(false, redacao.parecidos(base, parecidos.isEmpty() ? recomendacoes : parecidos)))));
                exemplosVendas++;
            }

            // dúvida sobre um jogo do catálogo
            JogoCatalogo duvida = recomendacoes.get(Math.min(1, recomendacoes.size() - 1)).jogo();
            String perguntaDuvida = "O " + duvida.titulo() + " vale a pena?";
            linhas.add(ia.linhaDataset(prompt(instrucao, perguntaDuvida, perfil, catalogo, vendas), List.of(
                    new Turno(true, perguntaDuvida),
                    new Turno(false, redacao.sobreJogo(duvida, versoes(catalogo, duvida.titulo()), perfil)))));
            exemplosVendas++;

            // detalhes de um jogo que o cliente já tem e de um jogo de outro gênero: só os fatos do CATÁLOGO
            String comprado = perfil.compras().get(0).titulo();
            JogoCatalogo jaTem = catalogo.stream().filter(j -> j.titulo().equals(comprado)).findFirst().orElse(null);
            if (jaTem != null) {
                String perguntaJaTem = "Me fala sobre o " + comprado;
                linhas.add(ia.linhaDataset(prompt(instrucao, perguntaJaTem, perfil, catalogo, vendas), List.of(
                        new Turno(true, perguntaJaTem),
                        new Turno(false, redacao.sobreJogo(jaTem, versoes(catalogo, comprado), perfil)))));
                exemplosVendas++;
            }
            List<String> titulos = catalogo.stream().map(JogoCatalogo::titulo).distinct().toList();
            String outroTitulo = titulos.get((k * 11 + 5) % titulos.size());
            JogoCatalogo outro = versoes(catalogo, outroTitulo).get(0);
            String perguntaOutro = "Como é o " + outroTitulo + "?";
            linhas.add(ia.linhaDataset(prompt(instrucao, perguntaOutro, perfil, catalogo, vendas), List.of(
                    new Turno(true, perguntaOutro),
                    new Turno(false, redacao.sobreJogo(outro, versoes(catalogo, outroTitulo), perfil)))));
            exemplosVendas++;

            // assunto fora de jogos: recusa com educação e volta para a loja
            for (int j = 0; j < FORA_DO_ASSUNTO_POR_CLIENTE; j++) {
                String foraDoAssunto = RedacaoRespostas.FORA_DO_ASSUNTO.get((k * FORA_DO_ASSUNTO_POR_CLIENTE + j) % RedacaoRespostas.FORA_DO_ASSUNTO.size());
                linhas.add(ia.linhaDataset(prompt(instrucao, foraDoAssunto, perfil, catalogo, vendas), List.of(
                        new Turno(true, foraDoAssunto),
                        new Turno(false, redacao.foraDoAssunto(perfil)))));
                exemplosVendas++;
            }

            // pedidos por estilo ("quero criar coisas e ter liberdade"...): 3 estilos e frases diferentes por cliente
            for (int j = 0; j < ESTILOS_POR_CLIENTE; j++) {
                EstilosDeJogo.Estilo estilo = EstilosDeJogo.ESTILOS.get((k * ESTILOS_POR_CLIENTE + j) % EstilosDeJogo.ESTILOS.size());
                String frase = estilo.frases().get((k + j) % estilo.frases().size());
                // mesma instrução do chat: com o bloco dos jogos que combinam com o pedido
                linhas.add(ia.linhaDataset(prompt(instrucao, frase, perfil, catalogo, vendas), List.of(
                        new Turno(true, frase),
                        new Turno(false, redacao.estilo(perfil, estilo, estilos.jogosPara(estilo, perfil, catalogo, 3), k + j)))));
                exemplosVendas++;
            }

            // conversa de duas voltas: recomenda e depois detalha o primeiro jogo
            String maisSobre = "Me fala mais sobre o " + recomendacoes.get(0).jogo().titulo();
            linhas.add(ia.linhaDataset(prompt(instrucao, maisSobre, perfil, catalogo, vendas), List.of(
                    new Turno(true, "Me recomenda um jogo?"),
                    new Turno(false, redacao.recomendacao(perfil, recomendacoes, 2)),
                    new Turno(true, maisSobre),
                    new Turno(false, redacao.sobreJogo(recomendacoes.get(0).jogo(), versoes(catalogo, recomendacoes.get(0).jogo().titulo()), perfil)))));
            exemplosVendas++;

            String genero = perfil.generoPrincipal();
            if (genero != null) {
                List<JogoCatalogo> doGenero = catalogo.stream().filter(j -> j.genero().equals(genero)).toList();
                String perguntaBusca = "Quais jogos de " + genero + " vocês têm?";
                linhas.add(ia.linhaDataset(prompt(instrucao, perguntaBusca, perfil, catalogo, vendas), List.of(
                        new Turno(true, perguntaBusca),
                        new Turno(false, redacao.busca(genero, doGenero, perfil)))));
                exemplosVendas++;

                // pedido de um gênero: só os jogos dele que respeitam plataformas, orçamento e o que o cliente já tem
                String pedidoGenero = "Quero um jogo de " + genero.toLowerCase();
                linhas.add(ia.linhaDataset(prompt(instrucao, pedidoGenero, perfil, catalogo, vendas), List.of(
                        new Turno(true, pedidoGenero),
                        new Turno(false, redacao.genero(perfil, genero, perfis.doGenero(perfil, catalogo, vendas, genero))))));
                exemplosVendas++;

                // e um gênero fora do gosto dele, para o modelo não depender do gênero preferido
                List<String> generos = catalogo.stream().map(JogoCatalogo::genero).distinct().sorted().toList();
                String outroGenero = generos.get((k * 5 + 3) % generos.size());
                if (outroGenero.equals(genero)) outroGenero = generos.get((k * 5 + 4) % generos.size());
                String pedidoOutroGenero = "Me indica um jogo de " + outroGenero.toLowerCase();
                linhas.add(ia.linhaDataset(prompt(instrucao, pedidoOutroGenero, perfil, catalogo, vendas), List.of(
                        new Turno(true, pedidoOutroGenero),
                        new Turno(false, redacao.genero(perfil, outroGenero, perfis.doGenero(perfil, catalogo, vendas, outroGenero))))));
                exemplosVendas++;
            }

            // pedido de uma plataforma que não está no perfil: os jogos indicados nela
            List<String> plataformasDoCatalogo = catalogo.stream().map(JogoCatalogo::plataforma).distinct().sorted().toList();
            List<String> outras = plataformasDoCatalogo.stream().filter(p -> !perfil.plataformasPreferidas().contains(p)).toList();
            if (!outras.isEmpty()) {
                String outra = outras.get(k % outras.size());
                String pedidoPlataforma = "Tem algum jogo bom para " + outra + "?";
                linhas.add(ia.linhaDataset(prompt(instrucao, pedidoPlataforma, perfil, catalogo, vendas), List.of(
                        new Turno(true, pedidoPlataforma),
                        new Turno(false, redacao.outraPlataforma(outra, perfis.recomendarOuMaisVendidos(
                                perfil.comPlataformas(java.util.Set.of(outra)), catalogo, vendas, 3))))));
                exemplosVendas++;
            }

            // orçamento: o declarado no perfil ou, sem ele, um valor comum de pergunta
            BigDecimal limite = perfil.orcamento() != null ? perfil.orcamento() : new BigDecimal(k % 2 == 0 ? "150" : "200");
            List<Recomendacao> noOrcamento = perfis.dentroDoValor(perfil, catalogo, vendas, limite);
            String perguntaValor = "Tem algo até R$ " + limite.intValue() + "?";
            linhas.add(ia.linhaDataset(prompt(instrucao, perguntaValor, perfil, catalogo, vendas), List.of(
                    new Turno(true, perguntaValor),
                    new Turno(false, redacao.orcamento(limite, noOrcamento)))));
            exemplosVendas++;
        }

        // 2) feedback do chat: só as respostas avaliadas como úteis viram exemplo
        int exemplosFeedback = 0;
        for (MensagemAssistente mensagem : mensagemRepository.findByAvaliacaoOrderByIdAsc(AvaliacaoResposta.UTIL)) {
            PerfilCliente perfil = perfilPorCliente.computeIfAbsent(mensagem.getClienteId(), perfis::perfil);
            linhas.add(ia.linhaDataset(prompt(perfis.instrucaoSistema(perfil, catalogo, vendas), mensagem.getPergunta(), perfil, catalogo, vendas), List.of(
                    new Turno(true, mensagem.getPergunta()),
                    new Turno(false, mensagem.getResposta()))));
            exemplosFeedback++;
        }
        int descartado = (int) mensagemRepository.countByAvaliacao(AvaliacaoResposta.NAO_UTIL);

        return new DatasetTreinamento(String.join("\n", linhas) + "\n", exemplosVendas, exemplosFeedback, descartado,
                clientes.size(), pedidoRepository.countByStatusIn(PerfilRecomendacaoService.EFETIVADAS),
                mensagemRepository.countByAvaliacaoIsNotNull());
    }

    private static List<JogoCatalogo> versoes(List<JogoCatalogo> catalogo, String titulo) {
        return catalogo.stream().filter(j -> j.titulo().equals(titulo)).toList();
    }

    /** A mesma instrução que o chat montaria para essa pergunta (com os blocos de estilo, valor e gênero). */
    private String prompt(String instrucao, String pergunta, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        return perfis.comBlocosDoPedido(instrucao, pergunta, perfil, catalogo, vendas);
    }
}
