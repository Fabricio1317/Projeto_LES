package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    Optional<Carrinho> findByClienteId(Long clienteId);
}
