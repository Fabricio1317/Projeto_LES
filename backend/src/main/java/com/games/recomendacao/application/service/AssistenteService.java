package com.games.recomendacao.application.service;

import com.games.cliente.adapter.out.persistence.repository.ClienteRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.StatusCliente;
import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.application.gateway.IaGenerativa;
import com.games.recomendacao.application.gateway.IaGenerativa.Turno;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.entity.TreinamentoModelo;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chatbot de recomendação (RNF0044, itens 1 e 2): ajuda na busca por jogos, responde dúvidas e
 * recomenda de forma personalizada, usando o modelo ajustado mais recente (ou o modelo base, antes do
 * primeiro ajuste fino). Cada resposta fica registrada para o cliente avaliar (item 3).
 */
@Service
public class AssistenteService {

    public static final int TAMANHO_MAXIMO_MENSAGEM = 1000;
    private static final int TROCAS_DE_CONTEXTO = 10;

    private final PerfilRecomendacaoService perfis;
    private final TreinamentoService treinamentos;
    private final IaGenerativa ia;
    private final MensagemAssistenteRepository mensagemRepository;
    private final ClienteRepository clienteRepository;
    private final ConferenciaResposta conferencia;
    private final ContinuacaoConversa continuacao;
    private final PerguntasDoCatalogo perguntas;

    public AssistenteService(PerfilRecomendacaoService perfis, TreinamentoService treinamentos, IaGenerativa ia,
                             MensagemAssistenteRepository mensagemRepository, ClienteRepository clienteRepository,
                             ConferenciaResposta conferencia, ContinuacaoConversa continuacao, PerguntasDoCatalogo perguntas) {
        this.perfis = perfis;
        this.conferencia = conferencia;
        this.continuacao = continuacao;
        this.perguntas = perguntas;
        this.treinamentos = treinamentos;
        this.ia = ia;
        this.mensagemRepository = mensagemRepository;
        this.clienteRepository = clienteRepository;
    }

    public MensagemAssistente conversar(Long clienteId, String conversaId, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("RNF0044", "Digite uma mensagem para o assistente.");
        }
        if (texto.length() > TAMANHO_MAXIMO_MENSAGEM) {
            throw new RegraNegocioException("RNF0044", "A mensagem pode ter no máximo " + TAMANHO_MAXIMO_MENSAGEM + " caracteres.");
        }
        var cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
        if (cliente.getStatus() != StatusCliente.ATIVO) {
            throw new RegraNegocioException("RNF0044", "O assistente está disponível apenas para clientes ativos.");
        }
        String conversa = conversaId == null || conversaId.isBlank() ? UUID.randomUUID().toString() : conversaId;

        List<MensagemAssistente> anteriores = mensagemRepository.findByClienteIdAndConversaIdOrderByIdAsc(clienteId, conversa);
        PerfilCliente perfil = perfis.perfil(clienteId); // lido a cada mensagem: o que o cliente mudou na tela já vale
        List<JogoCatalogo> catalogo = perfis.catalogo();
        // "sim" a "Quer que eu sugira outros parecidos?" vira "me sugira outros jogos parecidos com Hollow Knight"
        // e "quero ver somente os de PC", depois de "Quais jogos de corrida vocês têm?", vira a busca de corrida no PC
        MensagemAssistente ultima = anteriores.isEmpty() ? null : anteriores.get(anteriores.size() - 1);
        String pedido = continuacao.interpretar(texto.trim(), ultima == null ? null : ultima.getPergunta(),
                ultima == null ? null : ultima.getResposta(), catalogo);

        List<Turno> turnos = new ArrayList<>();
        for (MensagemAssistente anterior : anteriores.subList(Math.max(0, anteriores.size() - TROCAS_DE_CONTEXTO), anteriores.size())) {
            turnos.add(new Turno(true, anterior.getPergunta()));
            turnos.add(new Turno(false, anterior.getResposta()));
        }
        turnos.add(new Turno(true, pedido));

        // pedido por estilo ("quero criar coisas..."), valor ("até R$ 150") ou gênero ("um jogo de corrida"):
        // os jogos que servem para o cliente vão destacados no prompt
        DadosVendas vendas = perfis.dadosVendas();
        String instrucao = perfis.comBlocosDoPedido(perfis.instrucaoSistema(perfil, catalogo, vendas), pedido, perfil, catalogo, vendas)
                + perguntas.bloco(pedido, perfil, catalogo, vendas); // "o mais barato", "os mais vendidos": os dados certos
        Optional<TreinamentoModelo> emUso = treinamentos.modeloEmUso();
        String resposta = ia.responder(emUso.map(TreinamentoModelo::getModeloAjustado).orElse(null), instrucao, turnos);
        String modelo = emUso.map(t -> t.getNomeModelo() + " (ajuste fino de " + t.getModeloBase() + ")")
                .orElse(ia.modeloBase() + " (modelo base)");
        // a IA citou um jogo que o cliente já tem, acima do orçamento ou de outra plataforma: vale a resposta conferida
        // ...ou só repetiu uma resposta que já deu nesta conversa a outra pergunta
        List<String> respostasAnteriores = anteriores.stream()
                .filter(m -> !m.getPergunta().equalsIgnoreCase(texto.trim()))
                .map(MensagemAssistente::getResposta)
                .toList();
        ConferenciaResposta.Resultado conferida = conferencia.conferir(resposta, pedido, perfil, catalogo, vendas, respostasAnteriores);
        if (conferida.substituida()) modelo += " — corrigida pela conferência do perfil";
        return mensagemRepository.save(new MensagemAssistente(clienteId, conversa, texto.trim(), conferida.resposta(), modelo));
    }

    /** As recomendações calculadas para o cliente (o que vai nas SUGESTÕES do prompt), sem passar pela IA. */
    public List<PerfilRecomendacaoService.Recomendacao> recomendacoes(Long clienteId, int quantidade) {
        PerfilCliente perfil = perfis.perfil(clienteId);
        return perfis.recomendar(perfil, perfis.catalogo(), perfis.dadosVendas(), quantidade);
    }

    public MensagemAssistente avaliar(Long mensagemId, AvaliacaoResposta avaliacao) {
        if (avaliacao == null) {
            throw new RegraNegocioException("RNF0044", "Informe se a resposta foi útil ou não útil.");
        }
        MensagemAssistente mensagem = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem do assistente não encontrada: id=" + mensagemId));
        mensagem.avaliar(avaliacao);
        MensagemAssistente salva = mensagemRepository.save(mensagem);
        treinamentos.verificarRetreinoAutomatico();
        return salva;
    }

    public List<MensagemAssistente> conversa(Long clienteId, String conversaId) {
        return mensagemRepository.findByClienteIdAndConversaIdOrderByIdAsc(clienteId, conversaId);
    }

    public List<MensagemAssistente> avaliacoesDoCliente(Long clienteId) {
        return mensagemRepository.findByClienteIdAndAvaliacaoIsNotNullOrderByDataAvaliacaoDesc(clienteId);
    }

    public List<MensagemAssistente> todasAvaliacoes() {
        return mensagemRepository.findByAvaliacaoIsNotNullOrderByDataAvaliacaoDesc();
    }

    public Map<Long, String> nomesDosClientes(Collection<Long> ids) {
        return clienteRepository.findAllById(ids).stream().collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
    }
}
