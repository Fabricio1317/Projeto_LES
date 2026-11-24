package com.games.recomendacao.application.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pedidos fora do comum que o modelo pequeno não sabe responder e que merecem uma resposta simpática em vez de
 * uma recusa seca: jogar numa situação impossível ("quando estou tomando banho") ou uma dúvida da vida real
 * misturada com jogos ("quando eu jogo Nidhogg 2, qual o melhor carro para eu comprar?").
 */
public final class PedidosIncomuns {

    private PedidosIncomuns() {
    }

    public enum Tipo { SITUACAO, VIDA_REAL }

    /**
     * @param descricao "tomando banho"
     * @param motivo    "videogame e água não combinam"
     * @param depois    "depois do banho"
     * @param tema      o assunto da vida real ("carros"), no tipo VIDA_REAL
     * @param genero    o gênero de jogo ligado ao assunto ("Corrida"), se houver
     */
    public record Pedido(Tipo tipo, String descricao, String motivo, String depois, String tema, String genero) {}

    private record Situacao(Pattern padrao, String descricao, String motivo, String depois) {}

    /**
     * @param noJogo dá para dizer pelo catálogo se o jogo tem ou não esse objeto (veículos e equipamentos de esporte);
     *               "casa" ou "arma" dependem demais do jogo para afirmar
     */
    private record Assunto(Pattern objeto, String tema, String genero, boolean noJogo) {
        Assunto(Pattern objeto, String tema, String genero) {
            this(objeto, tema, genero, false);
        }
    }

    /**
     * Um objeto citado na mensagem que dá para conferir no jogo ("carro" numa pergunta sobre o Nidhogg 2).
     *
     * @param singular "carro"
     * @param plural   "carros"
     */
    public record Objeto(String singular, String plural, String tema, String genero, boolean vidaReal) {}

    private static final List<Situacao> SITUACOES = List.of(
            new Situacao(Pattern.compile("\\b(tomando banho|no banho|no chuveiro|na banheira|durante o banho|embaixo d.?agua|na piscina)\\b"),
                    "no banho", "videogame e água não combinam", "depois do banho, para relaxar"),
            new Situacao(Pattern.compile("\\b(dirigindo|no volante|no transito|enquanto dirijo|pilotando)\\b"),
                    "dirigindo", "a atenção tem que ficar na estrada", "quando chegar em casa"),
            new Situacao(Pattern.compile("\\b(dormindo|enquanto durmo|durante o sono)\\b"),
                    "dormindo", "aí não tem como jogar", "antes de dormir"),
            new Situacao(Pattern.compile("\\b(na aula|em aula|durante a aula|no trabalho|no expediente|na reuniao|numa reuniao)\\b"),
                    "na aula ou no trabalho", "melhor deixar para o seu tempo livre", "no seu tempo livre"),
            new Situacao(Pattern.compile("\\b(na academia|malhando|treinando na academia|correndo na rua)\\b"),
                    "treinando", "o foco ali é o treino", "para descansar depois do treino"));

    /** Coisas da vida real que o cliente pode querer comprar ou escolher, e o gênero de jogo que combina com elas. */
    private static final List<Assunto> ASSUNTOS = List.of(
            new Assunto(Pattern.compile("\\b(carros?|motos?|motocicletas?|bicicletas?|bikes?)\\b"), "carros e velocidade", "Corrida", true),
            new Assunto(Pattern.compile("\\b(time de futebol|chuteiras?|bolas? de futebol|tenis de corrida|raquetes?)\\b"), "esportes", "Esporte", true),
            new Assunto(Pattern.compile("\\b(armas?|espadas?)\\b"), "ação", "Ação"),
            new Assunto(Pattern.compile("\\b(casas?|apartamentos?|moveis|terrenos?)\\b"), "construir e decorar", "Simulação"),
            new Assunto(Pattern.compile("\\b(celular(es)?|roupas?|geladeiras?|televis(ao|oes)|tvs?|remedios?|acoes da bolsa|investimentos?)\\b"), null, null));

    /** Sinais de que a dúvida é da vida real ("para eu comprar", "de verdade"), não de dentro de um jogo. */
    private static final Pattern VIDA_REAL = Pattern.compile(
            "\\b(compr(ar|o|aria|ei)|devo comprar|vale a pena comprar|de verdade|na vida real|pra vida|investir)\\b");
    /** "qual o melhor carro no Forza?": aí a dúvida é de dentro do jogo. */
    private static final Pattern DENTRO_DO_JOGO = Pattern.compile("\\b(no jogo|dentro do jogo|no game|na partida|jogo de|jogos de)\\b");

    public static Pedido detectar(String mensagem) {
        if (mensagem == null) return null;
        String texto = semAcento(mensagem);
        for (Situacao s : SITUACOES) {
            if (s.padrao().matcher(texto).find()) return new Pedido(Tipo.SITUACAO, s.descricao(), s.motivo(), s.depois(), null, null);
        }
        if (!VIDA_REAL.matcher(texto).find() || DENTRO_DO_JOGO.matcher(texto).find()) return null;
        for (Assunto a : ASSUNTOS) {
            Matcher objeto = a.objeto().matcher(texto);
            if (objeto.find()) return new Pedido(Tipo.VIDA_REAL, objeto.group(), null, null, a.tema(), a.genero());
        }
        return null;
    }

    /** O objeto (veículo ou equipamento de esporte) citado na mensagem, para conferir no jogo citado junto. */
    public static Objeto objeto(String mensagem) {
        if (mensagem == null) return null;
        String texto = semAcento(mensagem);
        for (Assunto a : ASSUNTOS) {
            if (!a.noJogo()) continue;
            Matcher objeto = a.objeto().matcher(texto);
            if (objeto.find()) {
                // "bolas de futebol" → singular "bola de futebol", plural "bolas de futebol"
                String[] partes = objeto.group().split(" ", 2);
                String resto = partes.length > 1 ? " " + partes[1] : "";
                String primeira = partes[0];
                String singular = primeira.endsWith("s") && !primeira.equals("tenis") ? primeira.substring(0, primeira.length() - 1) : primeira;
                String plural = singular.endsWith("s") ? singular : singular.endsWith("r") ? singular + "es" : singular + "s";
                return new Objeto(singular + resto, plural + resto, a.tema(), a.genero(), VIDA_REAL.matcher(texto).find());
            }
        }
        return null;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
