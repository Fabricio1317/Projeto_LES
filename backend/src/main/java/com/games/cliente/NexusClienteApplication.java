package com.games.cliente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * Ponto de entrada da aplicação. Reúne os módulos de Cliente
 * (com.games.cliente) e de Vendas (com.games.vendas).
 */
@SpringBootApplication(scanBasePackages = "com.games")
@EntityScan("com.games")
@EnableJpaRepositories("com.games")
@EnableScheduling
public class NexusClienteApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusClienteApplication.class, args);
    }
}
