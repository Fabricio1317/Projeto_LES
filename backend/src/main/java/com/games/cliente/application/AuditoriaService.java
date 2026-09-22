package com.games.cliente.application;

import com.games.cliente.adapter.out.persistence.LogTransacaoRepository;
import com.games.cliente.domain.entity.LogTransacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RNF0012 — registra data, hora, usuário responsável e o dado alterado em
 * toda operação de inserção ou alteração feita pelos demais serviços deste
 * módulo (ClienteService, EnderecoService, CartaoService).
 */
@Service
public class AuditoriaService {

    private final LogTransacaoRepository repository;

    public AuditoriaService(LogTransacaoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String entidade, Long entidadeId, String operacao, String usuario, String dadoAlterado) {
        String resumo = dadoAlterado.length() > 1000 ? dadoAlterado.substring(0, 1000) : dadoAlterado;
        repository.save(new LogTransacao(entidade, entidadeId, operacao, usuario, resumo));
    }

    @Transactional(readOnly = true)
    public List<LogTransacao> listarTudo() {
        return repository.findAllByOrderByDataHoraDesc();
    }

    @Transactional(readOnly = true)
    public List<LogTransacao> listarPorEntidade(String entidade, Long entidadeId) {
        return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId);
    }
}
