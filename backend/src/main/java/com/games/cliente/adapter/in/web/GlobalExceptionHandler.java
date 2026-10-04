package com.games.cliente.adapter.in.web;

import com.games.cliente.adapter.in.web.dto.ErroResponse;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.enums.Bandeira;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.List;

/**
 * Converte as exceções de negócio em respostas HTTP consistentes, sempre
 * incluindo o código da regra violada (RF/RN/RNF) para que os testes
 * automatizados possam validar explicitamente qual regra foi testada.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validações de presença/formato dos campos (@Valid), identificadas pela regra do DTO validado. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErroResponse.of(400, codigoDaValidacao(ex), "Dados obrigatórios ausentes ou inválidos.", detalhes));
    }

    private static String codigoDaValidacao(MethodArgumentNotValidException ex) {
        String campo = ex.getBindingResult().getFieldErrors().isEmpty() ? ""
                : ex.getBindingResult().getFieldErrors().get(0).getField();
        return switch (ex.getBindingResult().getObjectName()) {
            case "cadastrarClienteRequest", "alterarClienteRequest" -> "RN0026";
            case "enderecoRequest" -> "RN0023";
            case "cartaoRequest" -> "RN0024";
            case "alterarSenhaRequest" -> "RF0028";
            case "itemCarrinhoRequest", "quantidadeRequest" -> "RF0032";
            case "freteRequest" -> "RF0034";
            case "finalizarCompraRequest" -> campo.startsWith("novoEndereco") ? "RN0023"
                    : campo.contains("novoCartao") ? "RN0024" : "RF0036";
            default -> "DADOS_INVALIDOS";
        };
    }

    /**
     * Valor fora da lista aceita para um enum (ex.: bandeira não registrada no
     * sistema — RN0025), que o Jackson rejeita antes mesmo da validação.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleCorpoInvalido(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException formato && formato.getTargetType() == Bandeira.class) {
            return ResponseEntity.badRequest().body(ErroResponse.of(400, "RN0025",
                    "Bandeira não permitida. Bandeiras aceitas: " + Arrays.toString(Bandeira.values()) + "."));
        }
        return ResponseEntity.badRequest().body(ErroResponse.of(400, "DADOS_INVALIDOS",
                "Corpo da requisição inválido: verifique os valores enviados."));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> handleRegraNegocio(RegraNegocioException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErroResponse.of(422, ex.getCodigoRegra(), ex.getMessage()));
    }

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleNaoEncontrado(ClienteNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "CLIENTE_NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "RECURSO_NAO_ENCONTRADO", ex.getMessage()));
    }

    /**
     * Rede de segurança: qualquer exceção não prevista pelos handlers acima
     * (ex.: erro de desserialização JSON, erro de acesso ao banco) cai aqui
     * em vez de virar a página de erro padrão do Spring — que o frontend não
     * sabe interpretar e mostra apenas "Erro inesperado.". Aqui devolvemos a
     * classe e a mensagem reais da exceção, o que é aceitável para esta
     * atividade acadêmica (rodando localmente) e essencial para depuração;
     * em produção isso normalmente seria substituído por um log interno e
     * uma mensagem genérica ao usuário.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGenerico(Exception ex) {
        ex.printStackTrace();
        String detalhe = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return ResponseEntity.internalServerError()
                .body(ErroResponse.of(500, "ERRO_NAO_TRATADO", detalhe));
    }
}
