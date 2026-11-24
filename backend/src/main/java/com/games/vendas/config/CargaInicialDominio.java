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
import java.util.List;
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
            Map.entry("Halo Infinite", "tiro em primeira pessoa, competitivo online, campanha de ficção científica"),
            Map.entry("Celeste", "indie, plataforma 2D desafiadora, história emocionante, trilha sonora marcante"),
            Map.entry("Forza Motorsport", "simulação de corrida em pistas reais, carros licenciados, ideal com volante, multijogador online"),
            // um jogo de cada gênero em cada plataforma (ao menos 3 por combinação)
            Map.entry("Hades", "ação rápida, mitologia grega, muito desafiador, história"),
            Map.entry("Bayonetta 3", "ação estilosa, combate frenético, combos, desafiador"),
            Map.entry("Hyrule Warriors: Age of Calamity", "batalhas contra multidões, mundo de Zelda, cooperativo local"),
            Map.entry("Devil May Cry 5 Special Edition", "combate estiloso, combos, muito desafiador"),
            Map.entry("Need for Speed Unbound", "corrida de rua, mundo aberto, personalizar carros, multijogador online"),
            Map.entry("Hot Wheels Unleashed", "pistas malucas, criar pistas, multijogador local, para toda a família"),
            Map.entry("Crash Team Racing Nitro-Fueled", "kart divertido, multijogador local, para toda a família"),
            Map.entry("Sid Meier's Civilization VII", "estratégia por turnos, construir um império, planejamento, partidas longas"),
            Map.entry("Company of Heroes 3", "estratégia em tempo real, Segunda Guerra, táticas, planejamento"),
            Map.entry("Crusader Kings III", "estratégia de dinastias, intrigas, escolhas que mudam a história, planejamento"),
            Map.entry("Advance Wars 1+2: Re-Boot Camp", "estratégia por turnos, batalhas táticas, multijogador local"),
            Map.entry("Just Dance 2025 Edition", "dança, festa com amigos e família, multijogador local"),
            Map.entry("Party Animals", "festa com amigos, bichinhos fofos, caos divertido, multijogador online"),
            Map.entry("Super Mario Party Jamboree", "minijogos, festa com amigos e família, multijogador local"),
            Map.entry("Pico Park", "festa com amigos, quebra-cabeças cooperativos, partidas rápidas"),
            Map.entry("Tekken 8", "luta 3D, competitivo online, partidas rápidas, história"),
            Map.entry("Mortal Kombat 1", "luta violenta, competitivo online, partidas rápidas, história"),
            Map.entry("Super Smash Bros. Ultimate", "luta com personagens da Nintendo, festa com amigos, multijogador local"),
            Map.entry("Guilty Gear -Strive-", "luta 2D estilo anime, competitivo online, partidas rápidas"),
            Map.entry("Sonic Superstars", "plataforma 2D veloz, cooperativo local, divertido para a família"),
            Map.entry("Crash Bandicoot 4: It's About Time", "plataforma 3D desafiadora, colecionáveis, humor"),
            Map.entry("Ori and the Will of the Wisps", "plataforma 2D, exploração, história emocionante, desafiador"),
            Map.entry("Puyo Puyo Tetris 2", "quebra-cabeças rápidos, competitivo, festa com amigos, multijogador local"),
            Map.entry("The Talos Principle 2", "quebra-cabeças inteligentes, exploração, ritmo tranquilo"),
            Map.entry("Cocoon", "quebra-cabeças criativos, mundos dentro de mundos, ritmo tranquilo"),
            Map.entry("Captain Toad: Treasure Tracker", "quebra-cabeças fáceis, cooperativo local, ideal para crianças"),
            Map.entry("The Witcher 3: Wild Hunt Complete Edition", "mundo aberto, história marcante, escolhas que mudam a história, exploração"),
            Map.entry("Pokémon Scarlet", "capturar e treinar Pokémon, mundo aberto, para todas as idades"),
            Map.entry("Xenoblade Chronicles 3", "história épica, mundo enorme, exploração"),
            Map.entry("Grounded", "sobrevivência no quintal, construir bases, cooperativo com amigos, exploração"),
            Map.entry("Palworld", "capturar criaturas, construir bases, sobrevivência, cooperativo online"),
            Map.entry("PowerWash Simulator", "limpar tudo com jato de água, relaxante, ritmo tranquilo, cooperativo"),
            Map.entry("Farming Simulator 25", "fazenda realista, máquinas agrícolas, ritmo tranquilo"),
            Map.entry("Disney Dreamlight Valley", "personagens da Disney, decorar e criar o seu vale, relaxante, para todas as idades"),
            Map.entry("Dead Space", "terror espacial, sustos, tensão, sobrevivência"),
            Map.entry("Little Nightmares II", "terror sombrio, suspense, quebra-cabeças"),
            Map.entry("Luigi's Mansion 3", "mansão assombrada, sustos leves, cooperativo local, divertido para a família"),
            Map.entry("Alien: Isolation", "terror de sobrevivência, suspense, muita tensão"),
            Map.entry("Call of Duty: Black Ops 6", "tiro em primeira pessoa, competitivo online, campanha de ação"),
            Map.entry("Doom Eternal", "tiro frenético, combate intenso, muito desafiador"),
            Map.entry("Splatoon 3", "tiro de tinta colorido, competitivo online, para todas as idades"),
            Map.entry("Metroid Prime Remastered", "tiro em primeira pessoa, exploração, ficção científica"),
            Map.entry("Helldivers 2", "tiro cooperativo com amigos, missões caóticas, ficção científica"));

    /**
     * RNF0044 — versões do mesmo jogo em outras plataformas em que ele foi lançado de fato. Cada versão é
     * um item do catálogo (com estoque próprio), como numa loja real, para o assistente recomendar o jogo
     * na plataforma que o cliente tem.
     */
    private static final Map<String, List<String>> OUTRAS_PLATAFORMAS = Map.ofEntries(
            Map.entry("Elden Ring", List.of("PC", "Xbox Series X|S")),
            Map.entry("God of War Ragnarök", List.of("PC")),
            Map.entry("EA Sports FC 25", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Forza Horizon 5", List.of("PC", "PlayStation 5")),
            Map.entry("Minecraft", List.of("PC", "PlayStation 5", "Xbox Series X|S")),
            Map.entry("Cyberpunk 2077", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Baldur's Gate 3", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Final Fantasy XVI", List.of("PC")),
            Map.entry("Starfield", List.of("PC")),
            Map.entry("Hogwarts Legacy", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Marvel's Spider-Man 2", List.of("PC")),
            Map.entry("Red Dead Redemption 2", List.of("PC")),
            Map.entry("NBA 2K25", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Tony Hawk's Pro Skater 1+2", List.of("PC", "PlayStation 5", "Nintendo Switch")),
            Map.entry("Hollow Knight", List.of("Nintendo Switch")),
            Map.entry("Terraria", List.of("Nintendo Switch")),
            Map.entry("Stardew Valley", List.of("PC")),
            Map.entry("Microsoft Flight Simulator 2024", List.of("PC")),
            Map.entry("Sid Meier's Civilization VI", List.of("Nintendo Switch")),
            Map.entry("No Man's Sky", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("It Takes Two", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Valheim", List.of("Xbox Series X|S")),
            Map.entry("Resident Evil 4", List.of("PC", "Xbox Series X|S")),
            Map.entry("Alan Wake 2", List.of("PC", "PlayStation 5")),
            Map.entry("Overcooked! All You Can Eat", List.of("PC", "PlayStation 5", "Xbox Series X|S")),
            Map.entry("Street Fighter 6", List.of("PC", "Xbox Series X|S")),
            Map.entry("Halo Infinite", List.of("PC")),
            Map.entry("Celeste", List.of("Nintendo Switch")),
            Map.entry("Forza Motorsport", List.of("PC")),
            Map.entry("Hades", List.of("Nintendo Switch", "PlayStation 5", "Xbox Series X|S")),
            Map.entry("Devil May Cry 5 Special Edition", List.of("Xbox Series X|S")),
            Map.entry("Need for Speed Unbound", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Hot Wheels Unleashed", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Sid Meier's Civilization VII", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Company of Heroes 3", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Crusader Kings III", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Just Dance 2025 Edition", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Party Animals", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Pico Park", List.of("Nintendo Switch")),
            Map.entry("Tekken 8", List.of("PC", "Xbox Series X|S")),
            Map.entry("Mortal Kombat 1", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Guilty Gear -Strive-", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Sonic Superstars", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Crash Bandicoot 4: It's About Time", List.of("PC", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Ori and the Will of the Wisps", List.of("PC", "Nintendo Switch")),
            Map.entry("Puyo Puyo Tetris 2", List.of("PC", "PlayStation 5", "Xbox Series X|S")),
            Map.entry("The Talos Principle 2", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Cocoon", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("The Witcher 3: Wild Hunt Complete Edition", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Grounded", List.of("PC", "PlayStation 5", "Nintendo Switch")),
            Map.entry("Palworld", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("PowerWash Simulator", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Farming Simulator 25", List.of("PlayStation 5", "Xbox Series X|S")),
            Map.entry("Disney Dreamlight Valley", List.of("PC", "PlayStation 5", "Xbox Series X|S")),
            Map.entry("Dead Space", List.of("PC", "Xbox Series X|S")),
            Map.entry("Little Nightmares II", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Alien: Isolation", List.of("Nintendo Switch")),
            Map.entry("Call of Duty: Black Ops 6", List.of("PC", "Xbox Series X|S")),
            Map.entry("Doom Eternal", List.of("PlayStation 5", "Xbox Series X|S", "Nintendo Switch")),
            Map.entry("Helldivers 2", List.of("PC", "Xbox Series X|S")));

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
            // mais opções por plataforma: indie de plataforma no PC e corrida de simulação no Xbox
            jogo("Celeste", "PC", "Plataforma", "Maddy Makes Games", "Maddy Makes Games", "10", 2018, null, "36.90", 40, "0.100");
            jogo("Forza Motorsport", "Xbox Series X|S", "Corrida", "Turn 10 Studios", "Xbox Game Studios", "L", 2023, null, "249.90", 20, "0.110");
            // pelo menos 3 jogos de cada gênero em cada plataforma (RNF0044: recomendação para qualquer combinação do perfil)
            jogo("Hades", "PC", "Ação", "Supergiant Games", "Supergiant Games", "14", 2020, null, "79.90", 40, "0.100");
            jogo("Bayonetta 3", "Nintendo Switch", "Ação", "PlatinumGames", "Nintendo", "16", 2022, null, "299.90", 20, "0.100");
            jogo("Hyrule Warriors: Age of Calamity", "Nintendo Switch", "Ação", "Koei Tecmo", "Nintendo", "12", 2020, null, "299.90", 20, "0.100");
            jogo("Devil May Cry 5 Special Edition", "PlayStation 5", "Ação", "Capcom", "Capcom", "18", 2020, null, "129.90", 20, "0.120");
            jogo("Need for Speed Unbound", "PC", "Corrida", "Criterion Games", "Electronic Arts", "12", 2022, null, "199.90", 20, "0.150");
            jogo("Hot Wheels Unleashed", "PlayStation 5", "Corrida", "Milestone", "Milestone", "L", 2021, null, "149.90", 25, "0.120");
            jogo("Crash Team Racing Nitro-Fueled", "Nintendo Switch", "Corrida", "Beenox", "Activision", "L", 2019, null, "199.90", 20, "0.100");
            jogo("Sid Meier's Civilization VII", "PC", "Estratégia", "Firaxis Games", "2K", "10", 2025, null, "299.90", 20, "0.150");
            jogo("Company of Heroes 3", "PC", "Estratégia", "Relic Entertainment", "Sega", "16", 2023, null, "199.90", 15, "0.150");
            jogo("Crusader Kings III", "PC", "Estratégia", "Paradox Development Studio", "Paradox Interactive", "14", 2020, null, "149.90", 15, "0.150");
            jogo("Advance Wars 1+2: Re-Boot Camp", "Nintendo Switch", "Estratégia", "WayForward", "Nintendo", "10", 2023, null, "299.90", 20, "0.100");
            jogo("Just Dance 2025 Edition", "Nintendo Switch", "Festa", "Ubisoft Paris", "Ubisoft", "L", 2024, null, "249.90", 25, "0.100");
            jogo("Party Animals", "PC", "Festa", "Recreate Games", "Source Technology", "L", 2023, null, "99.90", 25, "0.150");
            jogo("Super Mario Party Jamboree", "Nintendo Switch", "Festa", "Nintendo Cube", "Nintendo", "L", 2024, null, "299.90", 25, "0.100");
            jogo("Pico Park", "PC", "Festa", "TECOPARK", "TECOPARK", "L", 2019, null, "19.90", 40, "0.100");
            jogo("Tekken 8", "PlayStation 5", "Luta", "Bandai Namco Studios", "Bandai Namco", "14", 2024, null, "249.90", 20, "0.120");
            jogo("Mortal Kombat 1", "PlayStation 5", "Luta", "NetherRealm Studios", "Warner Bros. Games", "18", 2023, null, "249.90", 20, "0.120");
            jogo("Super Smash Bros. Ultimate", "Nintendo Switch", "Luta", "Bandai Namco Studios", "Nintendo", "10", 2018, null, "299.90", 30, "0.100");
            jogo("Guilty Gear -Strive-", "PlayStation 5", "Luta", "Arc System Works", "Arc System Works", "12", 2021, null, "149.90", 15, "0.120");
            jogo("Sonic Superstars", "PlayStation 5", "Plataforma", "Sonic Team", "Sega", "L", 2023, null, "199.90", 20, "0.120");
            jogo("Crash Bandicoot 4: It's About Time", "PlayStation 5", "Plataforma", "Toys for Bob", "Activision", "10", 2020, null, "149.90", 20, "0.120");
            jogo("Ori and the Will of the Wisps", "Xbox Series X|S", "Plataforma", "Moon Studios", "Xbox Game Studios", "10", 2020, null, "79.90", 25, "0.110");
            jogo("Puyo Puyo Tetris 2", "Nintendo Switch", "Puzzle", "Sega", "Sega", "L", 2020, null, "149.90", 25, "0.100");
            jogo("The Talos Principle 2", "PC", "Puzzle", "Croteam", "Devolver Digital", "10", 2023, null, "149.90", 15, "0.150");
            jogo("Cocoon", "PC", "Puzzle", "Geometric Interactive", "Annapurna Interactive", "10", 2023, null, "99.90", 20, "0.150");
            jogo("Captain Toad: Treasure Tracker", "Nintendo Switch", "Puzzle", "Nintendo", "Nintendo", "L", 2018, null, "249.90", 20, "0.100");
            jogo("The Witcher 3: Wild Hunt Complete Edition", "PC", "RPG", "CD Projekt Red", "CD Projekt", "18", 2015, null, "149.90", 20, "0.150");
            jogo("Pokémon Scarlet", "Nintendo Switch", "RPG", "Game Freak", "Nintendo", "L", 2022, null, "299.90", 30, "0.100");
            jogo("Xenoblade Chronicles 3", "Nintendo Switch", "RPG", "Monolith Soft", "Nintendo", "12", 2022, null, "299.90", 15, "0.100");
            jogo("Grounded", "Xbox Series X|S", "Sandbox", "Obsidian Entertainment", "Xbox Game Studios", "12", 2022, null, "149.90", 20, "0.110");
            jogo("Palworld", "PC", "Sandbox", "Pocketpair", "Pocketpair", "12", 2024, null, "99.90", 25, "0.150");
            jogo("PowerWash Simulator", "PC", "Simulação", "FuturLab", "Square Enix", "L", 2022, null, "99.90", 25, "0.150");
            jogo("Farming Simulator 25", "PC", "Simulação", "Giants Software", "Giants Software", "L", 2024, null, "199.90", 20, "0.150");
            jogo("Disney Dreamlight Valley", "Nintendo Switch", "Simulação", "Gameloft", "Gameloft", "L", 2023, null, "149.90", 25, "0.100");
            jogo("Dead Space", "PlayStation 5", "Terror", "Motive Studio", "Electronic Arts", "18", 2023, null, "249.90", 15, "0.120");
            jogo("Little Nightmares II", "PC", "Terror", "Tarsier Studios", "Bandai Namco", "14", 2021, null, "99.90", 20, "0.150");
            jogo("Luigi's Mansion 3", "Nintendo Switch", "Terror", "Next Level Games", "Nintendo", "L", 2019, null, "299.90", 20, "0.100");
            jogo("Alien: Isolation", "PC", "Terror", "Creative Assembly", "Sega", "16", 2014, null, "99.90", 15, "0.150");
            jogo("Call of Duty: Black Ops 6", "PlayStation 5", "Tiro", "Treyarch", "Activision", "18", 2024, null, "349.90", 30, "0.120");
            jogo("Doom Eternal", "PC", "Tiro", "id Software", "Bethesda Softworks", "18", 2020, null, "99.90", 20, "0.150");
            jogo("Splatoon 3", "Nintendo Switch", "Tiro", "Nintendo", "Nintendo", "10", 2022, null, "299.90", 25, "0.100");
            jogo("Metroid Prime Remastered", "Nintendo Switch", "Tiro", "Retro Studios", "Nintendo", "12", 2023, null, "199.90", 20, "0.100");
            jogo("Helldivers 2", "PlayStation 5", "Tiro", "Arrowhead Game Studios", "Sony Interactive Entertainment", "16", 2024, null, "199.90", 20, "0.120");
            outrasPlataformas();
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

    /** Mesmos dados do jogo, na outra plataforma (o código de barras muda por versão, então fica em branco). */
    private void outrasPlataformas() {
        for (Jogo base : jogoRepository.findAll()) {
            for (String plataforma : OUTRAS_PLATAFORMAS.getOrDefault(base.getTitulo(), List.of())) {
                jogoRepository.save(new Jogo(base.getTitulo(), plataforma, base.getGenero(), base.getDesenvolvedora(),
                        base.getDistribuidora(), base.getClassificacaoIndicativa(), base.getAno(), null, base.getPreco(),
                        base.getEstoque(), base.getPesoKg()));
            }
        }
    }

    private void cupom(String codigo, String valor, LocalDate validade) {
        if (!cupomRepository.existsByCodigoIgnoreCase(codigo)) {
            cupomRepository.save(new Cupom(codigo, TipoCupom.PROMOCIONAL, new BigDecimal(valor), null, validade, null));
        }
    }
}
