package com.games.vendas.config;

import com.games.vendas.adapter.out.persistence.CupomRepository;
import com.games.vendas.adapter.out.persistence.JogoRepository;
import com.games.vendas.adapter.out.persistence.ParametroSistemaRepository;
import com.games.vendas.application.ParametroService;
import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.entity.Jogo;
import com.games.vendas.domain.entity.ParametroSistema;
import com.games.vendas.domain.enums.TipoCupom;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RNF0013 — carga dos registros de domínio necessários para o sistema
 * funcionar na implantação: parâmetros, catálogo inicial de jogos e cupons
 * promocionais. Só insere o que ainda não existe, então é seguro a cada subida.
 */
@Component
public class CargaInicialDominio implements ApplicationRunner {

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
        }

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
