package com.games.cliente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação. Este módulo isola o CRUD de Cliente do
 * restante do backend ecommerceJogos para fins desta atividade — em produção,
 * este pacote (com.games.cliente) é integrado ao projeto principal.
 */
@SpringBootApplication
public class NexusClienteApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusClienteApplication.class, args);
    }
}
