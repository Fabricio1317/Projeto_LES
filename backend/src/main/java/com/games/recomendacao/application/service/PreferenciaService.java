package com.games.recomendacao.application.service;

import com.games.cliente.adapter.out.persistence.repository.ClienteRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.recomendacao.adapter.out.persistence.repository.PreferenciaClienteRepository;
import com.games.recomendacao.domain.entity.PreferenciaCliente;
import com.games.vendas.adapter.out.persistence.repository.JogoRepository;
import com.games.vendas.domain.entity.Jogo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Preferências do cliente (critério "e" da atividade): escolhidas por ele mesmo no perfil, entre os
 * gêneros e plataformas que existem no catálogo, com orçamento por jogo e observações opcionais.
 */
@Service
public class PreferenciaService {

    public record OpcoesPreferencia(List<String> generos, List<String> plataformas) {}

    private final PreferenciaClienteRepository preferenciaRepository;
    private final ClienteRepository clienteRepository;
    private final JogoRepository jogoRepository;

    public PreferenciaService(PreferenciaClienteRepository preferenciaRepository, ClienteRepository clienteRepository,
                              JogoRepository jogoRepository) {
        this.preferenciaRepository = preferenciaRepository;
        this.clienteRepository = clienteRepository;
        this.jogoRepository = jogoRepository;
    }

    @Transactional(readOnly = true)
    public OpcoesPreferencia opcoes() {
        List<Jogo> jogos = jogoRepository.findByAtivoTrueOrderByTituloAsc();
        return new OpcoesPreferencia(
                jogos.stream().map(Jogo::getGenero).distinct().sorted().toList(),
                jogos.stream().map(Jogo::getPlataforma).distinct().sorted().toList());
    }

    @Transactional(readOnly = true)
    public PreferenciaCliente buscar(Long clienteId) {
        exigirCliente(clienteId);
        return preferenciaRepository.findById(clienteId).orElseGet(() -> new PreferenciaCliente(clienteId));
    }

    @Transactional
    public PreferenciaCliente salvar(Long clienteId, Collection<String> generos, Collection<String> plataformas,
                                     BigDecimal orcamentoMaximo, String observacoes) {
        exigirCliente(clienteId);
        OpcoesPreferencia opcoes = opcoes();
        List<String> generosInformados = generos == null ? List.of() : List.copyOf(generos);
        List<String> plataformasInformadas = plataformas == null ? List.of() : List.copyOf(plataformas);
        generosInformados.stream().filter(g -> !opcoes.generos().contains(g)).findFirst().ifPresent(g -> {
            throw new RegraNegocioException("RNF0044", "Gênero fora do catálogo: " + g + ".");
        });
        plataformasInformadas.stream().filter(p -> !opcoes.plataformas().contains(p)).findFirst().ifPresent(p -> {
            throw new RegraNegocioException("RNF0044", "Plataforma fora do catálogo: " + p + ".");
        });
        if (orcamentoMaximo != null && orcamentoMaximo.signum() <= 0) {
            throw new RegraNegocioException("RNF0044", "O orçamento por jogo deve ser maior que zero.");
        }
        if (observacoes != null && observacoes.length() > 500) {
            throw new RegraNegocioException("RNF0044", "As observações podem ter no máximo 500 caracteres.");
        }
        PreferenciaCliente preferencia = preferenciaRepository.findById(clienteId).orElseGet(() -> new PreferenciaCliente(clienteId));
        preferencia.atualizar(generosInformados, plataformasInformadas, orcamentoMaximo, observacoes);
        return preferenciaRepository.save(preferencia);
    }

    private void exigirCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) throw new ClienteNaoEncontradoException(clienteId);
    }
}
