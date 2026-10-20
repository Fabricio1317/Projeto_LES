package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByClienteIdOrderByDataHoraDescIdDesc(Long clienteId);

    List<Notificacao> findByClienteIdAndLidaFalse(Long clienteId);
}
