package com.games.vendas.config;

import com.games.vendas.adapter.out.persistence.repository.CupomRepository;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.adapter.out.persistence.repository.ParametroSistemaRepository;
import com.games.vendas.application.service.ParametroService;
import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.entity.ParametroSistema;
import com.games.vendas.domain.enums.TipoCupom;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * RNF0013 — carga dos registros de domínio necessários para o sistema
 * funcionar na implantação: parâmetros, catálogo inicial de jogos e cupons
 * promocionais. Só insere o que ainda não existe, então é seguro a cada subida.
 */
@Component
@Order(1)
public class CargaInicialDominio implements ApplicationRunner {

    /** RNF0044 — o que cada jogo do catálogo inicial oferece; o assistente usa para pedidos por estilo. */
    private static final Map<String, String> CARACTERISTICAS = Map.ofEntries(
            Map.entry("The Legend of Zelda: Tears of the Kingdom", "mundo aberto enorme, liberdade total, construir veículos e engenhocas, exploração, quebra-cabeças"),
            Map.entry("Super Mario Bros. Wonder", "plataforma 2D colorida, multijogador local com até 4 pessoas, divertido para a família"),
            Map.entry("God of War Ragnarök", "história emocionante, combate intenso, mitologia nórdica"),
            Map.entry("Elden Ring", "mundo aberto sombrio, muito desafiador, liberdade para explorar, combate difícil"),
            Map.entry("EA Sports FC 25", "futebol, competitivo online, partidas rápidas, multijogador"),
            Map.entry("Forza Horizon 5", "corrida em mundo aberto, centenas de carros, multijogador online, liberdade para dirigir"),
            Map.entry("Minecraft", "construir e criar o que quiser, liberdade total, sobrevivência, multijogador, para todas as idades"),
            Map.entry("Cyberpunk 2077", "mundo aberto futurista, história adulta, escolhas que mudam a história"),
            Map.entry("Baldur's Gate 3", "história rica, escolhas que mudam a história, combate tático por turnos, cooperativo online"),
            Map.entry("Final Fantasy XVI", "história épica, combate de ação, fantasia"),
            Map.entry("Starfield", "exploração espacial, construir naves e bases, liberdade, mundo aberto"),
            Map.entry("Hogwarts Legacy", "mundo de Harry Potter, magia, exploração, mundo aberto"),
            Map.entry("Marvel's Spider-Man 2", "super-herói, ação acrobática, mundo aberto em Nova York, história"),
            Map.entry("Red Dead Redemption 2", "faroeste, história marcante, mundo aberto realista, muita liberdade"),
            Map.entry("Gran Turismo 7", "simulação de corrida realista, carros licenciados, ideal com volante"),
            Map.entry("Mario Kart 8 Deluxe", "corrida divertida, multijogador local e online, festa com amigos e família"),
            Map.entry("NBA 2K25", "basquete, competitivo online, modo carreira"),
            Map.entry("Tony Hawk's Pro Skater 1+2", "skate, manobras, partidas rápidas, trilha sonora clássica"),
            Map.entry("Hollow Knight", "indie, muito desafiador, exploração, atmosfera sombria"),
            Map.entry("Astro Bot", "plataforma 3D criativa, divertido para todas as idades, colecionáveis"),
            Map.entry("Kirby e a Terra Esquecida", "plataforma 3D fácil, cooperativo local, ideal para crianças"),
            Map.entry("Terraria", "construir e criar, liberdade, exploração, sobrevivência, cooperativo"),
            Map.entry("Stardew Valley", "fazenda, relaxante, construir e decorar, cooperativo, ritmo tranquilo"),
            Map.entry("The Sims 4", "criar pessoas e casas, liberdade criativa, simulação de vida, relaxante"),
            Map.entry("Animal Crossing: New Horizons", "decorar e criar a sua ilha, relaxante, ritmo tranquilo, para todas as idades"),
            Map.entry("Microsoft Flight Simulator 2024", "simulação de voo realista, explorar o mundo inteiro, relaxante"),
            Map.entry("Cities: Skylines II", "construir e administrar cidades, criatividade, planejamento, sem pressa"),
            Map.entry("Sid Meier's Civilization VI", "estratégia por turnos, construir um império, partidas longas, planejamento"),
            Map.entry("No Man's Sky", "exploração espacial infinita, construir bases, liberdade, cooperativo online"),
            Map.entry("It Takes Two", "cooperativo para dois, criativo, história de casal, tela dividida"),
            Map.entry("Valheim", "sobrevivência viking, construir, exploração, cooperativo com amigos"),
            Map.entry("Resident Evil 4", "terror e ação, sustos, tensão"),
            Map.entry("Alan Wake 2", "terror psicológico, história de mistério, suspense"),
            Map.entry("Portal 2", "quebra-cabeças inteligentes, humor, cooperativo para dois"),
            Map.entry("Overcooked! All You Can Eat", "cozinha caótica, cooperativo local, festa com amigos, partidas rápidas"),
            Map.entry("Street Fighter 6", "luta, competitivo online, partidas rápidas"),
            Map.entry("Halo Infinite", "tiro em primeira pessoa, competitivo online, campanha de ficção científica"));

    private final ParametroSistemaRepository parametroRepository;
    private final JogoRepository jogoRepository;
    private final CupomRepository cupomRepository;

    public CargaInicialDominio(ParametroSistemaRepository parametroRepository, JogoRepository jogoRepository,
                               CupomRepository cupomRepository) {
        this.parametroRepository = parametroRepository;
        this.jogoRepository = jogoRepository;
        this.cupomRepository = cupomRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!parametroRepository.existsById(ParametroSistema.PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS)) {
            parametroRepository.save(new ParametroSistema(ParametroSistema.PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS,
                    String.valueOf(ParametroService.PRAZO_PADRAO_SEGUNDOS),
                    "Prazo, em segundos, de bloqueio dos itens no carrinho a partir do último item incluído (RN0044)"));
        }

        if (jogoRepository.count() == 0) {
            jogo("The Legend of Zelda: Tears of the Kingdom", "Nintendo Switch", "Aventura", "Nintendo", "Nintendo", "10", 2023, "45496598433", "349.90", 30, "0.100");
            jogo("Super Mario Bros. Wonder", "Nintendo Switch", "Plataforma", "Nintendo", "Nintendo", "L", 2023, "45496510602", "299.90", 30, "0.100");
            jogo("God of War Ragnarök", "PlayStation 5", "Ação", "Santa Monica Studio", "Sony Interactive Entertainment", "18", 2022, "711719547853", "299.90", 25, "0.120");
            jogo("Elden Ring", "PlayStation 5", "RPG", "FromSoftware", "Bandai Namco", "16", 2022, "722674121170", "229.90", 35, "0.120");
            jogo("EA Sports FC 25", "PlayStation 5", "Esporte", "EA Vancouver", "Electronic Arts", "L", 2024, "14633386185", "279.90", 40, "0.110");
            jogo("Forza Horizon 5", "Xbox Series X|S", "Corrida", "Playground Games", "Xbox Game Studios", "L", 2021, "889842851567", "249.90", 20, "0.110");
            jogo("Minecraft", "Nintendo Switch", "Sandbox", "Mojang Studios", "Microsoft", "10", 2011, "45496420963", "149.90", 50, "0.100");
            jogo("Cyberpunk 2077", "PC", "RPG", "CD Projekt Red", "CD Projekt", "18", 2020, "5902367640484", "199.90", 15, "0.150");
            // catálogo ampliado para o assistente de recomendação (RNF0044)
            jogo("Baldur's Gate 3", "PC", "RPG", "Larian Studios", "Larian Studios", "18", 2023, "5056635604460", "249.90", 20, "0.150");
            jogo("Final Fantasy XVI", "PlayStation 5", "RPG", "Square Enix", "Square Enix", "16", 2023, "662248925232", "249.90", 18, "0.120");
            jogo("Starfield", "Xbox Series X|S", "RPG", "Bethesda Game Studios", "Bethesda Softworks", "16", 2023, "093155180478", "299.90", 15, "0.110");
            jogo("Hogwarts Legacy", "PlayStation 5", "Aventura", "Avalanche Software", "Warner Bros. Games", "12", 2023, "883929730256", "199.90", 22, "0.120");
            jogo("Marvel's Spider-Man 2", "PlayStation 5", "Ação", "Insomniac Games", "Sony Interactive Entertainment", "12", 2023, "711719570714", "299.90", 25, "0.120");
            jogo("Red Dead Redemption 2", "Xbox Series X|S", "Ação", "Rockstar Games", "Rockstar Games", "18", 2018, "710425570322", "149.90", 15, "0.110");
            jogo("Gran Turismo 7", "PlayStation 5", "Corrida", "Polyphony Digital", "Sony Interactive Entertainment", "L", 2022, "711719541028", "229.90", 20, "0.120");
            jogo("Mario Kart 8 Deluxe", "Nintendo Switch", "Corrida", "Nintendo", "Nintendo", "L", 2017, "045496420277", "299.90", 30, "0.100");
            jogo("NBA 2K25", "PlayStation 5", "Esporte", "Visual Concepts", "2K", "L", 2024, "710425598661", "249.90", 25, "0.120");
            jogo("Tony Hawk's Pro Skater 1+2", "Xbox Series X|S", "Esporte", "Vicarious Visions", "Activision", "10", 2020, "047875884281", "129.90", 15, "0.110");
            jogo("Hollow Knight", "PC", "Plataforma", "Team Cherry", "Team Cherry", "10", 2017, "811949031005", "59.90", 40, "0.100");
            jogo("Astro Bot", "PlayStation 5", "Plataforma", "Team Asobi", "Sony Interactive Entertainment", "L", 2024, "711719589316", "249.90", 20, "0.120");
            jogo("Kirby e a Terra Esquecida", "Nintendo Switch", "Plataforma", "HAL Laboratory", "Nintendo", "L", 2023, "045496478643", "299.90", 20, "0.100");
            jogo("Terraria", "PC", "Sandbox", "Re-Logic", "Re-Logic", "10", 2011, "812303016011", "39.90", 50, "0.100");
            // mais estilos para o assistente responder pedidos por característica (criar, relaxar, terror...)
            jogo("Stardew Valley", "Nintendo Switch", "Simulação", "ConcernedApe", "ConcernedApe", "L", 2016, "045496596187", "89.90", 40, "0.100");
            jogo("The Sims 4", "PC", "Simulação", "Maxis", "Electronic Arts", "12", 2014, "014633730623", "99.90", 30, "0.150");
            jogo("Animal Crossing: New Horizons", "Nintendo Switch", "Simulação", "Nintendo", "Nintendo", "L", 2020, "045496596439", "299.90", 25, "0.100");
            jogo("Microsoft Flight Simulator 2024", "Xbox Series X|S", "Simulação", "Asobo Studio", "Xbox Game Studios", "L", 2024, "889842960863", "299.90", 10, "0.110");
            jogo("Cities: Skylines II", "PC", "Estratégia", "Colossal Order", "Paradox Interactive", "L", 2023, "4020628602347", "199.90", 15, "0.150");
            jogo("Sid Meier's Civilization VI", "PC", "Estratégia", "Firaxis Games", "2K", "10", 2016, "710425578724", "129.90", 20, "0.150");
            jogo("No Man's Sky", "PlayStation 5", "Aventura", "Hello Games", "Hello Games", "10", 2016, "711719503002", "199.90", 20, "0.120");
            jogo("It Takes Two", "PlayStation 5", "Aventura", "Hazelight Studios", "Electronic Arts", "12", 2021, "014633380552", "149.90", 25, "0.120");
            jogo("Valheim", "PC", "Sandbox", "Iron Gate", "Coffee Stain", "14", 2021, "7350002930183", "79.90", 30, "0.100");
            jogo("Resident Evil 4", "PlayStation 5", "Terror", "Capcom", "Capcom", "18", 2023, "013388560608", "199.90", 20, "0.120");
            jogo("Alan Wake 2", "Xbox Series X|S", "Terror", "Remedy Entertainment", "Epic Games", "18", 2023, "5056635604477", "249.90", 12, "0.110");
            jogo("Portal 2", "PC", "Puzzle", "Valve", "Valve", "10", 2011, "014633196412", "39.90", 40, "0.100");
            jogo("Overcooked! All You Can Eat", "Nintendo Switch", "Festa", "Ghost Town Games", "Team17", "L", 2020, "5056208808455", "149.90", 25, "0.100");
            jogo("Street Fighter 6", "PlayStation 5", "Luta", "Capcom", "Capcom", "12", 2023, "013388410200", "229.90", 20, "0.120");
            jogo("Halo Infinite", "Xbox Series X|S", "Tiro", "343 Industries", "Xbox Game Studios", "16", 2021, "889842744340", "199.90", 20, "0.110");
        }
        jogoRepository.findAll().forEach(j -> {
            if (j.getCaracteristicas() == null && CARACTERISTICAS.containsKey(j.getTitulo())) {
                j.definirCaracteristicas(CARACTERISTICAS.get(j.getTitulo()));
                jogoRepository.save(j);
            }
        });

        cupom("PROMO10", "10.00", LocalDate.now().plusYears(1));
        cupom("PROMO20", "20.00", LocalDate.now().plusYears(1));
        cupom("GAMER5", "5.00", LocalDate.now().plusYears(1));
        cupom("VENCIDO10", "10.00", LocalDate.of(2020, 1, 1));
    }

    private void jogo(String titulo, String plataforma, String genero, String desenvolvedora, String distribuidora,
                      String classificacao, int ano, String codigoBarras, String preco, int estoque, String peso) {
        jogoRepository.save(new Jogo(titulo, plataforma, genero, desenvolvedora, distribuidora, classificacao, ano,
                codigoBarras, new BigDecimal(preco), estoque, new BigDecimal(peso)));
    }

    private void cupom(String codigo, String valor, LocalDate validade) {
        if (!cupomRepository.existsByCodigoIgnoreCase(codigo)) {
            cupomRepository.save(new Cupom(codigo, TipoCupom.PROMOCIONAL, new BigDecimal(valor), null, validade, null));
        }
    }
}
