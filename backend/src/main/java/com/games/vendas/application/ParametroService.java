package com.games.vendas.application;

import com.games.cliente.application.AuditoriaService;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.vendas.adapter.out.persistence.ParametroSistemaRepository;
import com.games.vendas.domain.entity.ParametroSistema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Parâmetros do sistema — em especial o prazo de bloqueio do carrinho (RN0044). */
@Service
public class ParametroService {

    public static final long PRAZO_PADRAO_SEGUNDOS = 1800;

    private final ParametroSistemaRepository repository;
    private final AuditoriaService auditoria;

    public ParametroService(ParametroSistemaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public long prazoBloqueioCarrinhoSegundos() {
        return repository.findById(ParametroSistema.PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS)
                .map(p -> Long.parseLong(p.getValor()))
                .orElse(PRAZO_PADRAO_SEGUNDOS);
    }

    @Transactional(readOnly = true)
    public List<ParametroSistema> listar() {
        return repository.findAll();
    }

    @Transactional
    public ParametroSistema alterar(String chave, String valor) {
        ParametroSistema parametro = repository.findById(chave)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Parâmetro não encontrado: " + chave));
        boolean prazoInvalido = !valor.matches("\\d{1,9}") || Long.parseLong(valor) <= 0;
        if (ParametroSistema.PRAZO_BLOQUEIO_CARRINHO_SEGUNDOS.equals(chave) && prazoInvalido) {
            throw new RegraNegocioException("RN0044", "O prazo de bloqueio do carrinho deve ser um número inteiro de segundos maior que zero.");
        }
        parametro.alterarValor(valor);
        parametro = repository.save(parametro);
        auditoria.registrar("ParametroSistema", 0L, "UPDATE", "admin", chave + " alterado para " + valor);
        return parametro;
    }
}
