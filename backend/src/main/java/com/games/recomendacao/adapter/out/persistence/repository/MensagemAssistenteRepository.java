package com.games.recomendacao.adapter.out.persistence.repository;

import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensagemAssistenteRepository extends JpaRepository<MensagemAssistente, Long> {

    List<MensagemAssistente> findByClienteIdAndConversaIdOrderByIdAsc(Long clienteId, String conversaId);

    List<MensagemAssistente> findByClienteIdAndAvaliacaoIsNotNullOrderByDataAvaliacaoDesc(Long clienteId);

    List<MensagemAssistente> findByAvaliacaoIsNotNullOrderByDataAvaliacaoDesc();

    List<MensagemAssistente> findByAvaliacaoOrderByIdAsc(AvaliacaoResposta avaliacao);

    List<MensagemAssistente> findByClienteIdAndAvaliacao(Long clienteId, AvaliacaoResposta avaliacao);

    long countByAvaliacao(AvaliacaoResposta avaliacao);

    long countByAvaliacaoIsNotNull();
}
