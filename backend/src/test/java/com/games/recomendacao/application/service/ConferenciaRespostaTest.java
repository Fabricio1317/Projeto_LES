package com.games.recomendacao.application.service;

import com.games.recomendacao.application.service.PerfilRecomendacaoService.ItemComprado;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** RNF0044 — a conferência barra jogos que o cliente já tem, acima do orçamento ou de outra plataforma. */
class ConferenciaRespostaTest {

    private final RedacaoRespostas redacao = new RedacaoRespostas();
    private final ConferenciaResposta conferencia = new ConferenciaResposta(null, null, redacao);

    @Test
    void assuntoForaDeJogosViraARecusaEducada() {
        var resultado = conferencia.conferir("Posso ajudar com a sua dúvida sobre a capital da França.", "Qual a capital da França?",
                ANA, CATALOGO, null);
        assertThat(resultado.substituida()).isTrue();
        assertThat(resultado.resposta()).isEqualTo(redacao.foraDoAssunto(ANA));
        // a recusa certa passa sem troca; cumprimentos e mensagens sobre a loja não são "fora do assunto"
        assertThat(conferencia.conferir(redacao.foraDoAssunto(ANA), "Quanto é 15 vezes 12?", ANA, CATALOGO, null).substituida()).isFalse();
        assertThat(conferencia.foraDoAssunto("Obrigado!", CATALOGO)).isFalse();
        assertThat(conferencia.foraDoAssunto("Quanto custa o frete?", CATALOGO)).isFalse();
        assertThat(conferencia.foraDoAssunto("O Hades vale a pena?", CATALOGO)).isFalse();
        assertThat(conferencia.foraDoAssunto("Me ajuda com a lição de matemática do meu filho?", CATALOGO)).isTrue();
        assertThat(conferencia.foraDoAssunto("Quero um jogo de matemática para o meu filho", CATALOGO)).isFalse();
    }

    @Test
    void perguntaSobreUmJogoSoComOsFatosDoCatalogo() {
        // Ana já tem o Hogwarts Legacy: a resposta inventou destaques e não avisou
        var resultado = conferencia.conferir("**Hogwarts Legacy** é um jogo de Aventura, com destaque para: história emocionante e liberdade criativa.",
                "O Hogwarts Legacy vale a pena?", ANA, CATALOGO, null);
        assertThat(resultado.substituida()).isTrue();
        assertThat(resultado.problemas()).anyMatch(p -> p.contains("já tem")).anyMatch(p -> p.contains("destaque inventado"));
        assertThat(resultado.resposta()).contains("Hogwarts Legacy").contains("Você já tem este jogo").contains("R$ 199,90");

        // resposta fiel ao catálogo passa sem troca
        String certa = redacao.sobreJogo(CATALOGO.get(2), CATALOGO.subList(2, 4), ANA);
        assertThat(conferencia.conferir(certa, "Como é o Hades?", ANA, CATALOGO, null).substituida()).isFalse();
        // o Hades sai para o PC e o PS5 da Ana: a resposta precisa citar a plataforma dela
        assertThat(conferencia.problemasSobreJogo("O **Hades** é um jogo de Ação para Nintendo Switch, por R$ 79,90.", CATALOGO.subList(2, 4), ANA))
                .contains("Hades: não citou PC ou PlayStation 5");
        // preço e plataforma errados são trocados
        assertThat(conferencia.problemasSobreJogo("O **Hades** sai para Xbox por R$ 59,90.", CATALOGO.subList(2, 4), ANA))
                .contains("Hades: não sai para xbox", "Hades: preço errado (R$ 59,90)");
    }

    private static final List<JogoCatalogo> CATALOGO = List.of(
            jogo("Hogwarts Legacy", "PlayStation 5", "Aventura", "199.90", "mundo de Harry Potter, magia, exploração, mundo aberto"),
            jogo("Hogwarts Legacy", "PC", "Aventura", "199.90", "mundo de Harry Potter, magia, exploração, mundo aberto"),
            jogo("Hades", "PC", "Ação", "79.90", "ação rápida, mitologia grega, muito desafiador, história"),
            jogo("Hades", "PlayStation 5", "Ação", "79.90", "ação rápida, mitologia grega, muito desafiador, história"),
            jogo("Mario Kart 8 Deluxe", "Nintendo Switch", "Corrida", "299.90"),
            jogo("Sid Meier's Civilization VI", "PC", "Estratégia", "129.90"),
            jogo("Sid Meier's Civilization VII", "PC", "Estratégia", "299.90"));

    /** Ana: PC e PlayStation 5, orçamento de R$ 100, já comprou Hogwarts Legacy. */
    private static final PerfilCliente ANA = new PerfilCliente(1L, "Ana Souza", 32,
            List.of(new ItemComprado("Hogwarts Legacy", "Aventura", "PlayStation 5", LocalDate.of(2026, 5, 1))),
            Map.of("Aventura", 1L), Map.of("PlayStation 5", 1L), Set.of("Aventura", "RPG"), new LinkedHashSet<>(List.of("PC", "PlayStation 5")),
            new BigDecimal("100"), null, Set.of(), Set.of());

    @Test
    void aceitaRecomendacaoDentroDoPerfil() {
        assertThat(conferencia.problemas("Recomendo **Hades** (PC) — R$ 79,90.", "Me recomenda um jogo?", ANA, CATALOGO)).isEmpty();
    }

    @Test
    void barraJogoQueOClienteJaTem() {
        assertThat(conferencia.problemas("Você vai gostar de **Hogwarts Legacy** (PlayStation 5).", "Me recomenda um jogo?", ANA, CATALOGO))
                .containsExactly("Hogwarts Legacy: o cliente já tem");
    }

    @Test
    void barraJogoDeOutraPlataformaEAcimaDoOrcamento() {
        assertThat(conferencia.problemas("Que tal **Mario Kart 8 Deluxe**?", "Me recomenda um jogo?", ANA, CATALOGO))
                .containsExactly("Mario Kart 8 Deluxe: não sai para PC ou PlayStation 5");
        assertThat(conferencia.problemas("Que tal **Sid Meier's Civilization VII**?", "Me recomenda um jogo?", ANA, CATALOGO))
                .containsExactly("Sid Meier's Civilization VII: passa de R$ 100,00");
    }

    @Test
    void valorPedidoNaMensagemSubstituiOOrcamentoDoPerfil() {
        assertThat(conferencia.problemas("Até R$ 300, **Sid Meier's Civilization VII** (PC).", "Tem algo até R$ 300?", ANA, CATALOGO)).isEmpty();
    }

    @Test
    void podeFalarDoJogoQueOClientePerguntouEListarOCatalogo() {
        assertThat(conferencia.problemas("**Hogwarts Legacy** é um jogo de aventura...", "O Hogwarts Legacy vale a pena?", ANA, CATALOGO)).isEmpty();
        assertThat(conferencia.problemas("Temos **Mario Kart 8 Deluxe** (Switch).", "Quais jogos de corrida vocês têm?", ANA, CATALOGO)).isEmpty();
    }

    @Test
    void pedidoDeRecomendacaoSemNenhumJogoNaResposta() {
        assertThat(conferencia.problemas("Até aqui eu só consigo ajudar com jogos.", "Me recomenda um jogo?", ANA, CATALOGO))
                .containsExactly("pediu recomendação e a resposta não citou jogo");
        assertThat(conferencia.problemas("Desculpe, só ajudo com jogos.", "Qual a capital da França?", ANA, CATALOGO)).isEmpty();
    }

    @Test
    void naoConfundeTituloComPedacoDeOutro() {
        // "Civilization VI" não é citado só porque aparece dentro de "Civilization VII"
        assertThat(conferencia.problemas("Recomendo **Sid Meier's Civilization VII**.", "Tem algo até R$ 300?", ANA, CATALOGO)).isEmpty();
    }

    private static JogoCatalogo jogo(String titulo, String plataforma, String genero, String preco) {
        return jogo(titulo, plataforma, genero, preco, null);
    }

    private static JogoCatalogo jogo(String titulo, String plataforma, String genero, String preco, String caracteristicas) {
        return new JogoCatalogo(null, titulo, plataforma, genero, "12", 2023, "Estúdio", new BigDecimal(preco), 10, caracteristicas);
    }
}
