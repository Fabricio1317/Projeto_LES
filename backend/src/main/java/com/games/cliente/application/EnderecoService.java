package com.games.cliente.application;

import com.games.cliente.adapter.in.web.dto.EnderecoRequest;
import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.adapter.out.persistence.EnderecoRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Endereco;
import com.games.cliente.domain.enums.TipoEndereco;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF0026 — gestão dos múltiplos endereços do cliente.
 * RN0021 — o cliente deve manter ao menos um endereço de cobrança (ou AMBOS).
 * RN0022 — o cliente deve manter ao menos um endereço de entrega (ou AMBOS).
 * RNF0034 — cada endereço é alterado isoladamente, sem afetar os demais dados do cliente.
 */
@Service
public class EnderecoService {

    private final EnderecoRepository repository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public EnderecoService(EnderecoRepository repository, ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Endereco cadastrar(Long clienteId, EnderecoRequest req) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new ClienteNaoEncontradoException(clienteId);
        }

        // CORREÇÃO: Repassando os novos campos do RN0023 para o construtor
        Endereco endereco = new Endereco(
                clienteId, req.apelido(), req.tipo(), req.tipoResidencia(), req.tipoLogradouro(),
                req.logradouro(), req.numero(), req.bairro(), req.cep(), req.cidade(),
                req.estado(), req.pais(), req.observacoes()
        );

        endereco = repository.save(endereco);
        auditoria.registrar("Endereco", endereco.getId(), "INSERT", "cliente-" + clienteId,
                "Endereço cadastrado: apelido=" + endereco.getApelido() + ", tipo=" + endereco.getTipo());
        return endereco;
    }

    @Transactional(readOnly = true)
    public List<Endereco> listar(Long clienteId) {
        return repository.findByClienteIdOrderByIdAsc(clienteId);
    }

    /** RNF0034 — altera um endereço específico, isoladamente. */
    @Transactional
    public Endereco alterar(Long clienteId, Long enderecoId, EnderecoRequest req) {
        Endereco endereco = buscarDoCliente(clienteId, enderecoId);

        // CORREÇÃO: Repassando os novos campos do RN0023 para o método de atualização
        endereco.atualizar(
                req.apelido(), req.tipo(), req.tipoResidencia(), req.tipoLogradouro(),
                req.logradouro(), req.numero(), req.bairro(), req.cep(), req.cidade(),
                req.estado(), req.pais(), req.observacoes()
        );

        endereco = repository.save(endereco);
        auditoria.registrar("Endereco", endereco.getId(), "UPDATE", "cliente-" + clienteId,
                "Endereço alterado: apelido=" + endereco.getApelido());
        return endereco;
    }

    /**
     * Remove um endereço, desde que isso não deixe o cliente sem nenhum
     * endereço de cobrança (RN0021) ou sem nenhum de entrega (RN0022).
     */
    @Transactional
    public void remover(Long clienteId, Long enderecoId) {
        Endereco endereco = buscarDoCliente(clienteId, enderecoId);
        TipoEndereco tipo = endereco.getTipo();

        boolean cobreCobranca = tipo == TipoEndereco.COBRANCA || tipo == TipoEndereco.AMBOS;
        boolean cobreEntrega = tipo == TipoEndereco.ENTREGA || tipo == TipoEndereco.AMBOS;

        if (cobreCobranca && contarPorTipoEquivalente(clienteId, TipoEndereco.COBRANCA) <= 1) {
            throw new RegraNegocioException("RN0021", "O cliente deve manter ao menos um endereço de cobrança.");
        }
        if (cobreEntrega && contarPorTipoEquivalente(clienteId, TipoEndereco.ENTREGA) <= 1) {
            throw new RegraNegocioException("RN0022", "O cliente deve manter ao menos um endereço de entrega.");
        }
        repository.delete(endereco);
    }

    private long contarPorTipoEquivalente(Long clienteId, TipoEndereco tipoBuscado) {
        return listar(clienteId).stream()
                .filter(e -> e.getTipo() == tipoBuscado || e.getTipo() == TipoEndereco.AMBOS)
                .count();
    }

    private Endereco buscarDoCliente(Long clienteId, Long enderecoId) {
        Endereco endereco = repository.findById(enderecoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço não encontrado: id=" + enderecoId));
        if (!endereco.getClienteId().equals(clienteId)) {
            throw new RegraNegocioException("ENDERECO_NAO_PERTENCE_AO_CLIENTE",
                    "Este endereço não pertence ao cliente informado.");
        }
        return endereco;
    }
}