package com.games.cliente.adapter.out.persistence;

import com.games.cliente.domain.entity.LogTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogTransacaoRepository extends JpaRepository<LogTransacao, Long> {
    List<LogTransacao> findAllByOrderByDataHoraDesc();
    List<LogTransacao> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, Long entidadeId);
}
