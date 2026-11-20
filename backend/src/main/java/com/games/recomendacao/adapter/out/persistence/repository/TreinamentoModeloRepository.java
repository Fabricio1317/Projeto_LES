package com.games.recomendacao.adapter.out.persistence.repository;

import com.games.recomendacao.domain.entity.TreinamentoModelo;
import com.games.recomendacao.domain.enums.StatusTreinamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TreinamentoModeloRepository extends JpaRepository<TreinamentoModelo, Long> {

    List<TreinamentoModelo> findAllByOrderByIdDesc();

    List<TreinamentoModelo> findByStatus(StatusTreinamento status);

    Optional<TreinamentoModelo> findFirstByStatusOrderByIdDesc(StatusTreinamento status);

    Optional<TreinamentoModelo> findFirstByStatusInOrderByIdDesc(Collection<StatusTreinamento> status);

    boolean existsByStatusIn(Collection<StatusTreinamento> status);
}
