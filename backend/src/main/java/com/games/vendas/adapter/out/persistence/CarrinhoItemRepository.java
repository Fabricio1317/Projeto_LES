package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.CarrinhoItem;
import com.games.vendas.domain.enums.SituacaoItemCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CarrinhoItemRepository extends JpaRepository<CarrinhoItem, Long> {

    /**
     * RN0044 — quantidade do jogo bloqueada em carrinhos de outros clientes
     * cujo prazo de bloqueio ainda não expirou.
     */
    @Query("""
        SELECT COALESCE(SUM(i.quantidade), 0) FROM CarrinhoItem i
        WHERE i.jogo.id = :jogoId
          AND i.situacao = :situacao
          AND i.carrinho.clienteId <> :clienteIdExcluido
          AND i.carrinho.expiraEm > :agora
        """)
    long somarBloqueado(@Param("jogoId") Long jogoId,
                        @Param("clienteIdExcluido") Long clienteIdExcluido,
                        @Param("situacao") SituacaoItemCarrinho situacao,
                        @Param("agora") LocalDateTime agora);
}
