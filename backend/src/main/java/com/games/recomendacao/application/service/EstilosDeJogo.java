package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pedidos por estilo de jogo, ditos de jeitos diferentes ("quero criar coisas e ter liberdade",
 * "algo para relaxar", "para jogar com amigos no sofá"...). Cada estilo tem as palavras que procura
 * nas características dos jogos do catálogo. Gera exemplos do ajuste fino para o modelo aprender a
 * ligar o que o cliente descreve às características dos jogos.
 */
@Component
public class EstilosDeJogo {

    /**
     * @param descricao  como o estilo aparece na resposta ("criar coisas e ter liberdade")
     * @param frases     formas diferentes de o cliente pedir
     * @param palavras   trechos procurados nas características (minúsculas)
     * @param soInfantil só jogos de classificação L ou 10
     */
    public record Estilo(String descricao, List<String> frases, List<String> palavras, boolean soInfantil) {}

    public static final List<Estilo> ESTILOS = List.of(
            new Estilo("criar coisas e ter bastante liberdade", List.of(
                    "Quero um jogo em que eu possa criar as coisas e ter bastante liberdade.",
                    "Tem algum jogo de construir o que eu quiser?",
                    "Gosto de jogos sem regras fixas, em que eu faço do meu jeito. O que você indica?",
                    "Me indica um jogo bem criativo, de montar e construir."),
                    List.of("construir", "criar", "liberdade", "criativ", "decorar"), false),
            new Estilo("relaxar e jogar com calma", List.of(
                    "Quero algo para relaxar depois do trabalho.",
                    "Tem jogo tranquilo, sem pressão?",
                    "Procuro um jogo calmo para jogar antes de dormir."),
                    List.of("relaxante", "tranquilo", "sem pressa"), false),
            new Estilo("jogar junto com outras pessoas", List.of(
                    "Tem jogo para jogar com amigos no sofá?",
                    "Quero um jogo para jogar junto com a minha namorada.",
                    "Qual jogo é bom para jogar em grupo numa festa?"),
                    List.of("cooperativo", "multijogador local", "festa", "para dois"), false),
            new Estilo("uma história marcante", List.of(
                    "Quero um jogo com uma história emocionante.",
                    "Gosto de jogos com boa narrativa, qual você indica?",
                    "Procuro um jogo em que as minhas escolhas mudem a história."),
                    List.of("história", "escolhas"), false),
            new Estilo("um desafio de verdade", List.of(
                    "Gosto de jogo difícil, que seja um desafio de verdade.",
                    "Quero algo bem desafiador.",
                    "Me indica um jogo para testar a minha habilidade."),
                    List.of("desafiador", "difícil"), false),
            new Estilo("sentir medo e suspense", List.of(
                    "Quero sentir medo, tem jogo de terror?",
                    "Me indica algo com suspense e sustos."),
                    List.of("terror", "sustos", "suspense"), false),
            new Estilo("jogar com crianças", List.of(
                    "Quero um jogo para uma criança de 7 anos.",
                    "Tem algo bem infantil e fácil?",
                    "Qual jogo é bom para jogar com meus filhos pequenos?"),
                    List.of("crianças", "todas as idades", "família", "fácil"), true),
            new Estilo("competir online", List.of(
                    "Quero competir online com outras pessoas.",
                    "Tem jogo competitivo para jogar online?"),
                    List.of("competitivo online"), false),
            new Estilo("explorar mundos grandes", List.of(
                    "Gosto de explorar mundos gigantes.",
                    "Quero um jogo de mundo aberto para explorar à vontade."),
                    List.of("mundo aberto", "exploração", "explorar"), false),
            new Estilo("pensar e planejar", List.of(
                    "Gosto de resolver enigmas e quebra-cabeças.",
                    "Quero um jogo que me faça pensar.",
                    "Gosto de estratégia e de planejar com calma."),
                    List.of("quebra-cabeça", "planejamento", "estratégia"), false));

    /**
     * Trechos (minúsculos, sem acento) que indicam cada estilo na mensagem do cliente. Servem para o
     * backend achar os jogos do catálogo com as características pedidas e entregá-los ao modelo no prompt:
     * um modelo pequeno não cruza bem a frase do cliente com 37 linhas de catálogo sozinho.
     */
    private static final Map<String, List<String>> GATILHOS = Map.of(
            "criar coisas e ter bastante liberdade", List.of("criar", "constru", "liberdade", "criativ", "do meu jeito",
                    "sem regras", "montar", "sandbox", "decorar"),
            "relaxar e jogar com calma", List.of("relax", "tranquil", "calm", "sem pressao", "descans", "estress", "sossego"),
            "jogar junto com outras pessoas", List.of("amigo", "sofa", "namorad", "junto", "grupo", "festa", "cooperativ",
                    "em dupla", "a dois", "familia"),
            "uma história marcante", List.of("historia", "narrativa", "enredo", "emocion", "escolhas"),
            "um desafio de verdade", List.of("dificil", "desafi", "habilidade", "hardcore"),
            "sentir medo e suspense", List.of("medo", "terror", "susto", "suspense", "assust", "horror"),
            "jogar com crianças", List.of("crianca", "filho", "infantil", "pequen"),
            "competir online", List.of("compet", "pvp", "ranquead"),
            "explorar mundos grandes", List.of("explor", "mundo aberto", "mundos"),
            "pensar e planejar", List.of("enigma", "quebra-cabeca", "pensar", "estrateg", "planej", "puzzle", "raciocin"));

    /** Estilos que a mensagem do cliente pede ("quero construir do meu jeito" → criar coisas e ter liberdade). */
    public List<Estilo> detectar(String mensagem) {
        if (mensagem == null) return List.of();
        String texto = Normalizer.normalize(mensagem, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return ESTILOS.stream().filter(e -> GATILHOS.get(e.descricao()).stream().anyMatch(texto::contains)).toList();
    }

    /**
     * Bloco do prompt com os jogos do catálogo que têm as características pedidas na mensagem, já filtrados
     * pelo perfil (comprados, idade). Vazio quando a mensagem não descreve um estilo. É usado igual no chat
     * e nos exemplos do ajuste fino, para o modelo aprender a recomendar a partir dele.
     */
    public String blocoDoPedido(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo) {
        List<Estilo> pedidos = detectar(mensagem);
        if (pedidos.isEmpty()) return "";
        StringBuilder texto = new StringBuilder("\n\nJOGOS QUE COMBINAM COM O PEDIDO (características do catálogo):");
        for (Estilo estilo : pedidos) {
            List<JogoCatalogo> jogos = jogosPara(estilo, perfil, catalogo, 3);
            texto.append("\nPara ").append(estilo.descricao()).append(": ").append(jogos.isEmpty()
                    ? "nenhum jogo do catálogo disponível para este cliente"
                    : jogos.stream().map(j -> j.titulo() + " (" + j.plataforma() + ", " + PerfilRecomendacaoService.moeda(j.preco())
                            + "; " + String.join(", ", j.caracteristicasCom(estilo.palavras())) + ")")
                    .collect(Collectors.joining("; ")));
        }
        return texto.toString();
    }

    /** Jogos do catálogo para o estilo, do que mais combina para o que menos, respeitando o perfil. */
    public List<JogoCatalogo> jogosPara(Estilo estilo, PerfilCliente perfil, List<JogoCatalogo> catalogo, int quantidade) {
        Set<String> vistos = new HashSet<>();
        return catalogo.stream()
                .filter(j -> !j.caracteristicasCom(estilo.palavras()).isEmpty())
                .filter(j -> !perfil.titulosComprados().contains(j.titulo()))
                .filter(j -> PerfilRecomendacaoService.classificacaoPermitida(j.classificacao(), perfil.idade()))
                .filter(j -> PerfilRecomendacaoService.daPlataformaDoCliente(j, perfil))
                .filter(j -> PerfilRecomendacaoService.cabeNoOrcamento(j, perfil))
                .filter(j -> !estilo.soInfantil() || j.classificacao().equalsIgnoreCase("L") || j.classificacao().equals("10"))
                .sorted(Comparator.comparingDouble((JogoCatalogo j) -> -pontos(j, estilo, perfil)).thenComparing(JogoCatalogo::titulo))
                .filter(j -> vistos.add(j.titulo())) // o mesmo jogo em duas plataformas do cliente aparece uma vez
                .limit(quantidade)
                .toList();
    }

    private static double pontos(JogoCatalogo jogo, Estilo estilo, PerfilCliente perfil) {
        double pontos = jogo.caracteristicasCom(estilo.palavras()).size() * 2.0;
        if (perfil.plataformasPreferidas().contains(jogo.plataforma())) pontos += 1;
        if (perfil.generosPreferidos().contains(jogo.genero())) pontos += 0.5;
        return pontos;
    }
}
