package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.Pedido;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByClienteIdOrderByDataCompraDesc(Long clienteId);

    List<Pedido> findAllByOrderByDataCompraDesc();

    List<Pedido> findByStatusOrderByDataCompraDesc(StatusPedido status);

    List<Pedido> findByClienteIdAndStatusIn(Long clienteId, Collection<StatusPedido> status);

    /** Compras aguardando a validação automática do pagamento (RN0037). */
    List<Pedido> findByStatusAndDataCompraBeforeOrderByIdAsc(StatusPedido status, LocalDateTime limite);
}
