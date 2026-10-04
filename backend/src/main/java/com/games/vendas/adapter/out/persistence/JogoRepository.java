package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.Jogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByAtivoTrueOrderByTituloAsc();

    List<Jogo> findAllByOrderByTituloAsc();
}
