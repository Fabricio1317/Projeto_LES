package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.Jogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByAtivoTrueOrderByTituloAsc();

    List<Jogo> findAllByOrderByTituloAsc();

    /** RF0055 — as categorias de produto são os gêneros dos jogos. */
    @Query("SELECT DISTINCT j.genero FROM Jogo j ORDER BY j.genero")
    List<String> listarCategorias();
}
