package com.games.cliente.application;

import com.games.cliente.adapter.in.web.dto.CartaoRequest;
import com.games.cliente.adapter.out.persistence.CartaoRepository;
import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cartao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF0027 — gestão dos múltiplos cartões de crédito do cliente.
 * RN0024 — composição obrigatória do cartão.
 * RN0025 — bandeira deve ser uma das aceitas pelo sistema (garantido pelo enum Bandeira).
 * No máximo um cartão do cliente pode estar marcado como preferencial por vez.
 */
@Service
public class CartaoService {

    private final CartaoRepository repository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public CartaoService(CartaoRepository repository, ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Cartao cadastrar(Long clienteId, CartaoRequest req) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new ClienteNaoEncontradoException(clienteId);
        }
        boolean primeiroCartao = repository.findByClienteIdOrderByIdAsc(clienteId).isEmpty();
        // o primeiro cartão do cliente é sempre preferencial, independente do que foi enviado
        boolean preferencial = req.preferencial() || primeiroCartao;

        Cartao cartao = new Cartao(clienteId, req.numero(), req.nomeImpresso(), req.bandeira(),
                req.codigoSeguranca(), preferencial);

        if (preferencial) {
            desmarcarPreferencialAtual(clienteId);
        }
        cartao = repository.save(cartao);
        auditoria.registrar("Cartao", cartao.getId(), "INSERT", "cliente-" + clienteId,
                "Cartão cadastrado: " + cartao.getNumeroMascarado() + ", bandeira=" + cartao.getBandeira()
                        + ", preferencial=" + cartao.isPreferencial());
        return cartao;
    }

    @Transactional(readOnly = true)
    public List<Cartao> listar(Long clienteId) {
        return repository.findByClienteIdOrderByIdAsc(clienteId);
    }

    /**
     * Remove um cartão. Se o cartão removido era o preferencial e ainda
     * restarem outros cartões, o mais antigo dos restantes é promovido
     * automaticamente a preferencial — RF0027 exige que, havendo cartões,
     * sempre exista exatamente um marcado como preferencial.
     */
    @Transactional
    public void remover(Long clienteId, Long cartaoId) {
        Cartao cartao = buscarDoCliente(clienteId, cartaoId);
        boolean eraPreferencial = cartao.isPreferencial();
        repository.delete(cartao);

        if (eraPreferencial) {
            repository.findByClienteIdOrderByIdAsc(clienteId).stream()
                    .findFirst()
                    .ifPresent(proximo -> {
                        proximo.marcarComoPreferencial();
                        repository.save(proximo);
                    });
        }
    }

    /** RF0027 — define este cartão como o preferencial do cliente, desmarcando o anterior. */
    @Transactional
    public Cartao marcarComoPreferencial(Long clienteId, Long cartaoId) {
        desmarcarPreferencialAtual(clienteId);
        Cartao cartao = buscarDoCliente(clienteId, cartaoId);
        cartao.marcarComoPreferencial();
        cartao = repository.save(cartao);
        auditoria.registrar("Cartao", cartao.getId(), "UPDATE", "cliente-" + clienteId,
                "Cartão " + cartao.getNumeroMascarado() + " definido como preferencial");
        return cartao;
    }

    private void desmarcarPreferencialAtual(Long clienteId) {
        repository.findByClienteIdOrderByIdAsc(clienteId).stream()
                .filter(Cartao::isPreferencial)
                .forEach(c -> { c.desmarcarComoPreferencial(); repository.save(c); });
    }

    private Cartao buscarDoCliente(Long clienteId, Long cartaoId) {
        Cartao cartao = repository.findById(cartaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado: id=" + cartaoId));
        if (!cartao.getClienteId().equals(clienteId)) {
            throw new RegraNegocioException("CARTAO_NAO_PERTENCE_AO_CLIENTE",
                    "Este cartão não pertence ao cliente informado.");
        }
        return cartao;
    }
}
