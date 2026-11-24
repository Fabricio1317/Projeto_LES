package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.games.recomendacao.application.service.PerfilRecomendacaoService.moeda;

/**
 * Perguntas sobre a loja que a resposta tem de tirar do catálogo, não da imaginação do modelo: "me mostra o seu
 * produto mais barato", "o que está fazendo sucesso?", "quais as novidades?", "quantos jogos vocês têm?", "jogos da
 * Capcom"... O backend calcula a resposta, manda os dados no prompt e confere o que a IA respondeu.
 */
@Component
public class PerguntasDoCatalogo {

    public enum Tipo { QUANTIDADE, PLATAFORMAS, GENEROS, MAIS_BARATOS, MAIS_CAROS, MAIS_VENDIDOS, LANCAMENTOS, CLASSICOS, EMPRESA }

    /**
     * @param plataformas as pedidas na mensagem ou, sem elas, as do perfil do cliente
     * @param empresa     a desenvolvedora pedida ("Capcom"), só no tipo EMPRESA
     */
    public record Pergunta(Tipo tipo, String genero, Set<String> plataformas, BigDecimal valor, String empresa) {}

    private static final int TAMANHO_DA_LISTA = 5;
    private static final int TAMANHO_DA_LISTA_DA_EMPRESA = 8;

    /** A ordem importa: "quantos jogos baratos" é QUANTIDADE, "o mais barato" é MAIS_BARATOS. */
    private static final Map<Tipo, Pattern> PADROES = new LinkedHashMap<>();

    static {
        PADROES.put(Tipo.QUANTIDADE, Pattern.compile("quantos (jogos|produtos|titulos)|tamanho do catalogo|quantidade de jogos"));
        PADROES.put(Tipo.PLATAFORMAS, Pattern.compile("(quais|que|para quais|pra quais) (plataformas|consoles|videogames)|vendem (jogos )?para quais"));
        PADROES.put(Tipo.GENEROS, Pattern.compile("(quais|que) (generos|categorias|tipos de jogos?)"));
        PADROES.put(Tipo.MAIS_BARATOS, Pattern.compile("mais barat|menor preco|mais em conta|preco mais baixo|mais economic|baratinh|baratos"));
        PADROES.put(Tipo.MAIS_CAROS, Pattern.compile("mais car[oa]s?\\b|maior preco|preco mais alto|mais salgad"));
        PADROES.put(Tipo.MAIS_VENDIDOS, Pattern.compile("mais vendid|mais popular|fazendo sucesso|mais compra|mais procurad|campe(ao|oes) de venda|top vendas"));
        PADROES.put(Tipo.LANCAMENTOS, Pattern.compile("lancament|mais nov[oa]s?\\b|mais recente|novidade|acabou de sair|saiu agora"));
        PADROES.put(Tipo.CLASSICOS, Pattern.compile("mais antig|classico|das antigas"));
    }

    // ------------------------------------------------------------------ detecção

    /** O tipo da pergunta, ou null quando a mensagem não é uma pergunta sobre o catálogo da loja. */
    public static Tipo tipo(String mensagem, List<JogoCatalogo> catalogo) {
        if (mensagem == null) return null;
        String texto = semAcento(mensagem);
        for (Map.Entry<Tipo, Pattern> padrao : PADROES.entrySet()) {
            if (padrao.getValue().matcher(texto).find()) return padrao.getKey();
        }
        return empresaPedida(mensagem, catalogo) != null ? Tipo.EMPRESA : null;
    }

    public Pergunta detectar(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo) {
        Tipo tipo = tipo(mensagem, catalogo);
        if (tipo == null) return null;
        Set<String> plataformas = PerfilRecomendacaoService.plataformasDoPedido(mensagem, catalogo);
        // o tamanho do catálogo, as plataformas e os gêneros são da loja toda; as listas seguem as plataformas do cliente
        boolean daLojaToda = tipo == Tipo.QUANTIDADE || tipo == Tipo.PLATAFORMAS || tipo == Tipo.GENEROS;
        if (plataformas.isEmpty() && !daLojaToda) plataformas = perfil.plataformasPreferidas();
        return new Pergunta(tipo, PerfilRecomendacaoService.generoDoPedido(mensagem, catalogo), plataformas,
                PerfilRecomendacaoService.valorDoPedido(mensagem), tipo == Tipo.EMPRESA ? empresaPedida(mensagem, catalogo) : null);
    }

    /** "jogos da Capcom", "o que tem da Nintendo?": a desenvolvedora citada depois de "da", "do" ou "de". */
    private static String empresaPedida(String mensagem, List<JogoCatalogo> catalogo) {
        String texto = " " + PerfilRecomendacaoService.normalizarTitulo(mensagem) + " ";
        for (String chave : chavesDeEmpresa(catalogo)) {
            if (Pattern.compile("\\b(da|do|de|dos|das) " + Pattern.quote(chave) + " ").matcher(texto).find()) return chave;
        }
        return null;
    }

    /** Nomes pelos quais o cliente cita uma desenvolvedora: o nome inteiro e a primeira palavra ("ubisoft"). */
    private static Set<String> chavesDeEmpresa(List<JogoCatalogo> catalogo) {
        Set<String> chaves = new LinkedHashSet<>();
        for (String dev : catalogo.stream().map(JogoCatalogo::desenvolvedora).filter(d -> d != null && !d.isBlank()).distinct().toList()) {
            String nome = PerfilRecomendacaoService.normalizarTitulo(dev);
            if (nome.length() >= 4) chaves.add(nome);
            String primeira = nome.split(" ")[0];
            if (primeira.length() >= 5) chaves.add(primeira);
        }
        return chaves;
    }

    private static boolean daEmpresa(JogoCatalogo jogo, String chave) {
        String dev = PerfilRecomendacaoService.normalizarTitulo(jogo.desenvolvedora());
        return dev.equals(chave) || dev.startsWith(chave + " ");
    }

    // ------------------------------------------------------------------ resposta

    /** Os jogos da resposta, um por título (a versão mais barata nas plataformas consideradas), já na ordem. */
    List<List<JogoCatalogo>> jogos(Pergunta p, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        Map<String, Integer> vendidos = vendas == null ? Map.of() : vendas.vendasPorJogo();
        Map<String, List<JogoCatalogo>> porTitulo = catalogo.stream()
                .filter(j -> p.genero() == null || j.genero().equals(p.genero()))
                .filter(j -> p.plataformas().isEmpty() || p.plataformas().contains(j.plataforma()))
                .filter(j -> p.valor() == null || j.preco().compareTo(p.valor()) <= 0)
                .filter(j -> PerfilRecomendacaoService.classificacaoPermitida(j.classificacao(), perfil.idade()))
                .filter(j -> p.tipo() != Tipo.EMPRESA || daEmpresa(j, p.empresa()))
                .filter(j -> p.tipo() != Tipo.MAIS_VENDIDOS || vendidos.getOrDefault(j.titulo(), 0) > 0)
                .collect(Collectors.groupingBy(JogoCatalogo::titulo, LinkedHashMap::new, Collectors.toList()));
        Comparator<List<JogoCatalogo>> ordem = switch (p.tipo()) {
            case MAIS_CAROS -> Comparator.comparing((List<JogoCatalogo> v) -> maisCara(v).preco()).reversed();
            case MAIS_VENDIDOS -> Comparator.comparing((List<JogoCatalogo> v) -> -vendidos.getOrDefault(v.get(0).titulo(), 0));
            case LANCAMENTOS -> Comparator.comparing((List<JogoCatalogo> v) -> -ano(v.get(0)));
            case CLASSICOS -> Comparator.comparing((List<JogoCatalogo> v) -> ano(v.get(0)));
            case EMPRESA -> Comparator.comparing((List<JogoCatalogo> v) -> v.get(0).titulo());
            default -> Comparator.comparing((List<JogoCatalogo> v) -> maisBarata(v).preco());
        };
        int limite = p.tipo() == Tipo.EMPRESA ? TAMANHO_DA_LISTA_DA_EMPRESA : TAMANHO_DA_LISTA;
        return porTitulo.values().stream()
                .sorted(ordem.thenComparing(v -> maisBarata(v).preco()).thenComparing(v -> v.get(0).titulo()))
                .limit(limite).toList();
    }

    /** A resposta certa para a pergunta, montada só com o catálogo. */
    public String responder(Pergunta p, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        String escopo = escopo(p);
        switch (p.tipo()) {
            case QUANTIDADE -> {
                List<JogoCatalogo> filtrados = filtrados(p, catalogo);
                long total = filtrados.stream().map(JogoCatalogo::titulo).distinct().count();
                String porPlataforma = p.plataformas().isEmpty()
                        ? ": " + contagem(filtrados, JogoCatalogo::plataforma, " para ") : "";
                return "Temos " + total + " jogos" + escopo + " no catálogo" + porPlataforma
                        + ". Quer que eu mostre os de algum gênero ou de alguma faixa de preço?";
            }
            case PLATAFORMAS -> {
                List<JogoCatalogo> filtrados = filtrados(p, catalogo);
                long plataformas = filtrados.stream().map(JogoCatalogo::plataforma).distinct().count();
                return "Vendemos jogos para " + plataformas + " plataformas: " + contagem(filtrados, JogoCatalogo::plataforma, " ")
                        + ". Quer ver os jogos de alguma delas?";
            }
            case GENEROS -> {
                List<JogoCatalogo> filtrados = filtrados(p, catalogo);
                long generos = filtrados.stream().map(JogoCatalogo::genero).distinct().count();
                return "Temos jogos de " + generos + " gêneros" + escopo + ": " + contagem(filtrados, JogoCatalogo::genero, " ")
                        + ". Quer ver os de algum gênero?";
            }
            default -> {
                List<List<JogoCatalogo>> jogos = jogos(p, perfil, catalogo, vendas);
                if (jogos.isEmpty()) {
                    return p.tipo() == Tipo.MAIS_VENDIDOS
                            ? "Ainda não temos vendas registradas" + escopo + ". Quer que eu recomende algo pelo seu gosto?"
                            : "No momento não encontrei jogos" + escopo + ". Quer que eu procure em outra plataforma, gênero ou faixa de preço?";
                }
                Map<String, Integer> vendidos = vendas == null ? Map.of() : vendas.vendasPorJogo();
                String lista = jogos.stream().map(v -> linha(v, p, perfil, vendidos)).collect(Collectors.joining("\n"));
                JogoCatalogo primeiro = p.tipo() == Tipo.MAIS_CAROS ? maisCara(jogos.get(0)) : maisBarata(jogos.get(0));
                return switch (p.tipo()) {
                    case MAIS_BARATOS -> "Os jogos mais baratos" + escopo + " são:\n" + lista + "\nO mais em conta é o **"
                            + primeiro.titulo() + "**, por " + moeda(primeiro.preco()) + ". Quer que eu conte mais sobre algum deles?";
                    case MAIS_CAROS -> "Os jogos mais caros" + escopo + " são:\n" + lista + "\nO de maior preço é o **"
                            + primeiro.titulo() + "**, por " + moeda(primeiro.preco()) + ". Quer que eu mostre opções mais em conta?";
                    case MAIS_VENDIDOS -> "Os mais vendidos da loja" + escopo + " são:\n" + lista
                            + "\nQuer que eu conte mais sobre algum deles?";
                    case LANCAMENTOS -> "Os lançamentos mais recentes" + escopo + " são:\n" + lista
                            + "\nQuer que eu conte mais sobre algum deles?";
                    case CLASSICOS -> "Os clássicos mais antigos" + escopo + " são:\n" + lista
                            + "\nQuer que eu conte mais sobre algum deles?";
                    default -> "Da " + nomeDaEmpresa(jogos) + escopo + ", temos:\n" + lista + "\nQuer que eu conte mais sobre algum deles?";
                };
            }
        }
    }

    /** Bloco do prompt com a resposta calculada, para o modelo usar os dados certos. Vazio se não for pergunta do catálogo. */
    public String bloco(String mensagem, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        Pergunta p = detectar(mensagem, perfil, catalogo);
        if (p == null) return "";
        return "\n\nDADOS DO CATÁLOGO PARA ESTA PERGUNTA (responda só com estes jogos, preços e números):\n"
                + responder(p, perfil, catalogo, vendas);
    }

    /** O que a resposta da IA errou: jogo fora da lista, o primeiro da lista ausente, preço ou número inventado. */
    public List<String> problemas(String resposta, Pergunta p, PerfilCliente perfil, List<JogoCatalogo> catalogo, DadosVendas vendas) {
        List<String> problemas = new ArrayList<>();
        String esperada = responder(p, perfil, catalogo, vendas);
        if (p.tipo() == Tipo.QUANTIDADE || p.tipo() == Tipo.PLATAFORMAS || p.tipo() == Tipo.GENEROS) {
            Set<String> certos = numeros(esperada);
            Set<String> ditos = numeros(resposta);
            // o primeiro número da resposta certa é o total ("Temos 82 jogos", "para 4 plataformas", "de 14 gêneros")
            String total = certos.stream().findFirst().orElse("");
            if (!ditos.contains(total)) problemas.add("não informou o total certo (" + total + ")");
            ditos.stream().filter(n -> !certos.contains(n)).forEach(n -> problemas.add("número que não está no catálogo (" + n + ")"));
            return problemas;
        }
        // "o mais barato para PC" respondido "para Nintendo Switch": a plataforma escrita tem de ser a da pergunta
        if (!p.plataformas().isEmpty()) {
            List<String> todas = catalogo.stream().map(JogoCatalogo::plataforma).distinct().toList();
            PerfilRecomendacaoService.plataformasCitadas(resposta, todas).stream().filter(pl -> !p.plataformas().contains(pl))
                    .forEach(pl -> problemas.add("falou de " + pl + ", mas a pergunta é sobre " + String.join(" ou ", p.plataformas())));
        }
        List<List<JogoCatalogo>> jogos = jogos(p, perfil, catalogo, vendas);
        List<String> esperados = jogos.stream().map(v -> v.get(0).titulo()).toList();
        List<String> citados = catalogo.stream().map(JogoCatalogo::titulo).distinct().filter(t -> PerfilRecomendacaoService.citaTitulo(resposta, t)).toList();
        if (esperados.isEmpty()) {
            if (!citados.isEmpty()) problemas.add("citou jogos, mas nenhum atende à pergunta");
            return problemas;
        }
        if (!citados.contains(esperados.get(0))) problemas.add("não citou " + esperados.get(0) + ", o primeiro da lista");
        // "jogos da Capcom": a lista da empresa tem de vir completa
        if (p.tipo() == Tipo.EMPRESA) {
            esperados.stream().skip(1).filter(t -> !citados.contains(t)).forEach(t -> problemas.add("deixou de fora " + t));
        }
        citados.stream().filter(t -> !esperados.contains(t)).forEach(t -> problemas.add(t + ": não está na lista pedida"));
        Set<String> precos = jogos.stream().flatMap(List::stream).map(j -> moeda(j.preco()).substring(3)).collect(Collectors.toSet());
        Matcher preco = Pattern.compile("R\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})").matcher(resposta);
        while (preco.find()) {
            if (!precos.contains(preco.group(1))) problemas.add("preço que não está no catálogo (R$ " + preco.group(1) + ")");
        }
        return problemas;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String linha(List<JogoCatalogo> versoes, Pergunta p, PerfilCliente perfil, Map<String, Integer> vendidos) {
        JogoCatalogo jogo = p.tipo() == Tipo.MAIS_CAROS ? maisCara(versoes) : maisBarata(versoes);
        String onde = p.plataformas().isEmpty()
                ? PerfilRecomendacaoService.plataformas(versoes) + ") — " + PerfilRecomendacaoService.precos(versoes)
                : jogo.plataforma() + ") — " + moeda(jogo.preco());
        String extra = switch (p.tipo()) {
            case MAIS_VENDIDOS -> ": " + vendidos.getOrDefault(jogo.titulo(), 0) + " vendidos";
            case LANCAMENTOS, CLASSICOS -> ": lançado em " + jogo.ano();
            case EMPRESA -> ": " + jogo.genero();
            default -> ": " + jogo.genero();
        };
        return "- **" + jogo.titulo() + "** (" + onde + extra
                + (perfil.titulosComprados().contains(jogo.titulo()) ? " — você já tem este" : "");
    }

    private static String escopo(Pergunta p) {
        return (p.genero() == null ? "" : " de " + p.genero())
                + (p.plataformas().isEmpty() ? "" : " para " + String.join(" ou ", p.plataformas()))
                + (p.valor() == null ? "" : " até " + moeda(p.valor()));
    }

    private static List<JogoCatalogo> filtrados(Pergunta p, List<JogoCatalogo> catalogo) {
        return catalogo.stream()
                .filter(j -> p.genero() == null || j.genero().equals(p.genero()))
                .filter(j -> p.plataformas().isEmpty() || p.plataformas().contains(j.plataforma()))
                .toList();
    }

    /** "PC (61 jogos), PlayStation 5 (48 jogos)": títulos por plataforma ou gênero. */
    private static String contagem(List<JogoCatalogo> jogos, java.util.function.Function<JogoCatalogo, String> chave, String juntar) {
        Map<String, Set<String>> titulos = new TreeMap<>();
        jogos.forEach(j -> titulos.computeIfAbsent(chave.apply(j), k -> new LinkedHashSet<>()).add(j.titulo()));
        return titulos.entrySet().stream()
                .map(e -> juntar.equals(" para ") ? e.getValue().size() + " para " + e.getKey() : e.getKey() + " (" + e.getValue().size() + ")")
                .collect(Collectors.joining(", "));
    }

    private static String nomeDaEmpresa(List<List<JogoCatalogo>> jogos) {
        return jogos.get(0).get(0).desenvolvedora();
    }

    private static Set<String> numeros(String texto) {
        // os números da resposta, sem os preços (que são conferidos à parte)
        Matcher m = Pattern.compile("(?<![\\d,.$])\\d+(?![\\d,])").matcher(texto.replaceAll("R\\$\\s*[\\d.,]+", ""));
        Set<String> numeros = new LinkedHashSet<>();
        while (m.find()) numeros.add(m.group());
        return numeros;
    }

    private static JogoCatalogo maisBarata(List<JogoCatalogo> versoes) {
        return versoes.stream().min(Comparator.comparing(JogoCatalogo::preco)).orElseThrow();
    }

    private static JogoCatalogo maisCara(List<JogoCatalogo> versoes) {
        return versoes.stream().max(Comparator.comparing(JogoCatalogo::preco)).orElseThrow();
    }

    private static int ano(JogoCatalogo jogo) {
        return jogo.ano() == null ? 0 : jogo.ano();
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
