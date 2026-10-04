package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.PedidoItem;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    /** RN0028 — itens de compras ainda EM PROCESSAMENTO seguem reservados, sem baixa no estoque. */
    @Query("""
        SELECT COALESCE(SUM(i.quantidade), 0) FROM PedidoItem i
        WHERE i.jogo.id = :jogoId AND i.pedido.status = :status
        """)
    long somarReservado(@Param("jogoId") Long jogoId, @Param("status") StatusPedido status);
}
