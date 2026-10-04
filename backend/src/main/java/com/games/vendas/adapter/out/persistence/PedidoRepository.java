package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByClienteIdOrderByDataCompraDesc(Long clienteId);

    List<Pedido> findAllByOrderByDataCompraDesc();

    List<Pedido> findByStatusOrderByDataCompraDesc(StatusPedido status);

    List<Pedido> findByClienteIdAndStatusIn(Long clienteId, Collection<StatusPedido> status);
}
