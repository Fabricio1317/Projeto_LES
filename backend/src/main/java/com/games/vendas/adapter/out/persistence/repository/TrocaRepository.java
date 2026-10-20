package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.Troca;
import com.games.vendas.domain.enums.StatusTroca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrocaRepository extends JpaRepository<Troca, Long> {

    List<Troca> findAllByOrderByDataSolicitacaoDesc();

    List<Troca> findByStatusOrderByDataSolicitacaoDesc(StatusTroca status);

    List<Troca> findByClienteIdOrderByDataSolicitacaoDesc(Long clienteId);

    boolean existsByPedidoId(Long pedidoId);
}
