package com.games.cliente.adapter.out.persistence;

import com.games.cliente.domain.Cliente;
import com.games.cliente.domain.StatusCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * RF0024 — consulta de clientes com filtros combinados ou isolados por
 * nome, CPF, e-mail e status. Qualquer parâmetro pode ser omitido (null),
 * caso em que a consulta ignora aquele filtro.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCpf(String cpf);

    Optional<Cliente> findByEmail(String email);

    Optional<Cliente> findByCodigoCliente(String codigoCliente);

    boolean existsByCpf(String cpf);

    boolean existsByEmail(String email);

    @Query("""
        SELECT c FROM Cliente c
        WHERE (CAST(:nome AS String) IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:nome AS String), '%')))
          AND (CAST(:cpf AS String) IS NULL OR c.cpf = CAST(:cpf AS String))
          AND (CAST(:email AS String) IS NULL OR LOWER(c.email) = LOWER(CAST(:email AS String)))
          AND (CAST(:status AS String) IS NULL OR c.status = :status)
        ORDER BY c.nome ASC
        """)
    List<Cliente> buscarComFiltros(@Param("nome") String nome,
                                   @Param("cpf") String cpf,
                                   @Param("email") String email,
                                   @Param("status") StatusCliente status);
}