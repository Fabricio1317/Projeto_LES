package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.ItemComprado;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.Recomendacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.games.recomendacao.application.service.PerfilRecomendacaoService.moeda;

/**
 * Redação das respostas-modelo dos exemplos de ajuste fino gerados a partir das vendas (e da massa
 * de dados de feedback): o tom, o tamanho e a estrutura que o modelo ajustado deve aprender.
 */
@Component
public class RedacaoRespostas {

    /** Pedidos de recomendação, em várias formas, para o modelo não depender de uma frase exata. */
    public static final List<String> PEDIDOS_RECOMENDACAO = List.of(
            "Me recomenda um jogo?",
            "O que eu deveria jogar agora?",
            "Quero um jogo novo, o que você indica?",
            "Tem alguma sugestão parecida com o que eu já comprei?",
            "Estou sem ideia do que jogar, me ajuda?",
            "Qual jogo você acha que eu vou gostar?");

    /** Perguntas fora do escopo do assistente, para o modelo aprender a recusar com educação. */
    public static final List<String> FORA_DO_ASSUNTO = List.of(
            "Qual a capital da França?",
            "Me ajuda com a minha lição de matemática?",
            "Quem ganhou a Copa do Mundo de 2022?",
            "Qual a previsão do tempo para amanhã?",
            "Me dá uma receita de bolo?",
            "Em quem eu devo votar na próxima eleição?",
            "Escreve um poema de amor para mim?",
            "Qual o maior país do mundo?",
            "Quanto é 15 vezes 12?",
            "Quem descobriu o Brasil?");

    private static final List<String> FECHAMENTOS = List.of(
            "Quer que eu conte mais sobre algum deles?",
            "Se quiser, comparo dois deles para você.",
            "Se preferir outra plataforma ou faixa de preço, é só me dizer.",
            "Posso procurar algo mais barato, se quiser.",
            "Quer saber mais detalhes de algum?",
            "Me conta se algum chamou sua atenção!");

    public String recomendacao(PerfilCliente perfil, List<Recomendacao> recomendacoes, int variante) {
        String nome = perfil.primeiroNome();
        String genero = perfil.generoPrincipal();
        List<ItemComprado> compras = perfil.compras();
        String abertura = switch (variante % 5) {
            case 0 -> nome + ", pelo seu histórico e pelas suas preferências, separei estes jogos para você:";
            case 1 -> compras.isEmpty()
                    ? "Boa pergunta! Pelo que você me contou do seu gosto, estes combinam com você:"
                    : "Boa! Olhando o que você já jogou (" + compras.subList(Math.max(0, compras.size() - 2), compras.size()).stream()
                    .map(ItemComprado::titulo).collect(Collectors.joining(" e ")) + "), acho que estes combinam com você:";
            case 2 -> "Aqui vão minhas sugestões, " + nome + ":";
            case 3 -> genero == null ? "Separei algumas opções do nosso catálogo para você:"
                    : "Pensando no seu gosto por " + genero + ", eu indicaria:";
            default -> "Separei algumas opções do nosso catálogo que têm a sua cara:";
        };
        // nenhuma sugestão dos gêneros preferidos (orçamento ou plataforma não permitem): avisa antes de listar
        if (!perfil.generosPreferidos().isEmpty()
                && recomendacoes.stream().noneMatch(r -> perfil.generosPreferidos().contains(r.jogo().genero()))) {
            abertura = "Não encontrei um jogo de " + String.join(" ou ", perfil.generosPreferidos())
                    + (perfil.orcamento() == null ? "" : " até " + moeda(perfil.orcamento()))
                    + (perfil.plataformasPreferidas().isEmpty() ? "" : " para " + String.join(" ou ", perfil.plataformasPreferidas()))
                    + " que você ainda não tenha, mas estes cabem no seu perfil:";
        }
        return abertura + "\n" + listar(recomendacoes) + "\n" + FECHAMENTOS.get(variante % FECHAMENTOS.size());
    }

    public String busca(String genero, List<JogoCatalogo> jogos, PerfilCliente perfil) {
        if (jogos.isEmpty()) {
            return "No momento não temos jogos de " + genero + " em estoque. Posso sugerir algo parecido?";
        }
        // uma linha por jogo, com as plataformas em que ele sai
        String lista = jogos.stream().collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(versoes -> "- **" + versoes.get(0).titulo() + "** (" + PerfilRecomendacaoService.plataformas(versoes) + ") — "
                        + PerfilRecomendacaoService.precos(versoes)
                        + (perfil.titulosComprados().contains(versoes.get(0).titulo()) ? " — você já tem este" : ""))
                .collect(Collectors.joining("\n"));
        return "Temos estes jogos de " + genero + " no catálogo:\n" + lista + "\nQuer que eu indique qual combina mais com você?";
    }

    public String buscaPorPlataforma(String plataforma, List<JogoCatalogo> jogos, PerfilCliente perfil) {
        if (jogos.isEmpty()) {
            return "No momento não temos jogos para " + plataforma + " em estoque. Quer ver outra plataforma?";
        }
        String lista = jogos.stream().map(j -> "- **" + j.titulo() + "** (" + j.genero() + ") — " + moeda(j.preco())
                        + (perfil.titulosComprados().contains(j.titulo()) ? " — você já tem este" : ""))
                .collect(Collectors.joining("\n"));
        return "Para " + plataforma + " temos:\n" + lista + "\nQuer uma indicação entre eles?";
    }

    public String orcamento(BigDecimal valor, List<Recomendacao> recomendacoes) {
        if (recomendacoes.isEmpty()) {
            return "Até " + moeda(valor) + " não encontrei um jogo do seu estilo agora. Quer que eu mostre as opções mais baratas do catálogo?";
        }
        return "Até " + moeda(valor) + ", as opções que mais combinam com você são:\n" + listar(recomendacoes)
                + "\nTodas cabem no seu orçamento.";
    }

    /** Pedido de um gênero ("quero um jogo de corrida"): só o que serve para o cliente, ou o aviso de que não há. */
    public String genero(PerfilCliente perfil, String genero, List<Recomendacao> recomendacoes) {
        if (recomendacoes.isEmpty()) {
            return "No momento não temos um jogo de " + genero
                    + (perfil.orcamento() == null ? "" : " até " + moeda(perfil.orcamento()))
                    + (perfil.plataformasPreferidas().isEmpty() ? "" : " para " + String.join(" ou ", perfil.plataformasPreferidas()))
                    + " que você ainda não tenha. Quer que eu procure com outro valor, outra plataforma ou outro gênero?";
        }
        return "De " + genero + ", estes combinam com você:\n" + listar(recomendacoes)
                + "\nQuer que eu conte mais sobre algum deles?";
    }

    /** Pedido de outra plataforma ("tem algo para o Switch?"): os jogos indicados nela. */
    public String outraPlataforma(String plataforma, List<Recomendacao> recomendacoes) {
        if (recomendacoes.isEmpty()) {
            return "Para " + plataforma + " não encontrei um jogo que combine com você agora. Quer que eu procure em outra plataforma?";
        }
        return "Para " + plataforma + ", estes combinam com você:\n" + listar(recomendacoes)
                + "\nQuer que eu conte mais sobre algum deles?";
    }

    public String parecidos(String base, List<Recomendacao> recomendacoes) {
        return "Quem gosta de **" + base + "** costuma curtir também:\n" + listar(recomendacoes)
                + "\nQuer que eu conte mais sobre algum deles?";
    }

    public String foraDoAssunto(PerfilCliente perfil) {
        return "Desculpe, " + perfil.primeiroNome() + ", aqui eu só consigo ajudar com jogos e com a loja Nexus. "
                + "Posso te recomendar um jogo, procurar algum no catálogo ou tirar uma dúvida sobre um jogo?";
    }

    /**
     * Detalhes de um jogo, só com os fatos da linha dele no CATÁLOGO (plataformas, gênero, classificação, preço e
     * características), para o modelo aprender a não inventar. Se o cliente já tem o jogo, diz isso.
     */
    public String sobreJogo(JogoCatalogo jogo, List<JogoCatalogo> versoes, PerfilCliente perfil) {
        String classificacao = jogo.classificacao().equalsIgnoreCase("L") ? "livre para todos os públicos"
                : "indicada para maiores de " + jogo.classificacao() + " anos";
        String plataformas = PerfilRecomendacaoService.plataformas(versoes);
        return "**" + jogo.titulo() + "** é um jogo de " + jogo.genero() + " para " + plataformas.replaceFirst(", ([^,]+)$", " e $1")
                + (jogo.caracteristicas() != null ? ", com destaque para: " + jogo.caracteristicas() : "")
                + ". A classificação é " + classificacao + ", e aqui na Nexus ele sai por " + PerfilRecomendacaoService.precos(versoes) + "."
                + (perfil.titulosComprados().contains(jogo.titulo())
                        ? " Você já tem este jogo; quer que eu sugira outros parecidos?"
                        : " Quer que eu sugira outros parecidos?");
    }

    /** Resposta a um pedido por estilo ("quero criar coisas e ter liberdade"), citando as características que batem. */
    public String estilo(PerfilCliente perfil, EstilosDeJogo.Estilo estilo, List<JogoCatalogo> jogos, int variante) {
        if (jogos.isEmpty()) {
            return "No momento não temos no catálogo um jogo para " + estilo.descricao() + ". Quer que eu sugira algo parecido?";
        }
        String abertura = switch (variante % 3) {
            case 0 -> "Para " + estilo.descricao() + ", estes são ótimos:";
            case 1 -> "Boa, " + perfil.primeiroNome() + "! Se a ideia é " + estilo.descricao() + ", eu indicaria:";
            default -> "Tenho algumas opções que combinam com isso:";
        };
        String lista = jogos.stream().map(j -> "- **" + j.titulo() + "** (" + j.plataforma() + ") — " + moeda(j.preco()) + ": "
                + String.join(", ", j.caracteristicasCom(estilo.palavras()))
                + (perfil.plataformasPreferidas().contains(j.plataforma()) ? "; e sai para a sua plataforma" : "") + ".")
                .collect(Collectors.joining("\n"));
        return abertura + "\n" + lista + "\n" + FECHAMENTOS.get(variante % FECHAMENTOS.size());
    }

    private static String listar(List<Recomendacao> recomendacoes) {
        return recomendacoes.stream().map(r -> "- **" + r.jogo().titulo() + "** (" + r.jogo().plataforma() + ", "
                        + r.jogo().genero() + ") — " + moeda(r.jogo().preco()) + ": " + String.join(" e ", r.motivos()) + ".")
                .collect(Collectors.joining("\n"));
    }
}
