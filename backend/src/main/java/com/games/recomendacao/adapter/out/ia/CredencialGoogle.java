package com.games.recomendacao.adapter.out.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credencial de conta de serviço do Google Cloud (arquivo JSON baixado no console). Troca um JWT
 * assinado com a chave privada por um token de acesso OAuth2 (fluxo "JWT bearer"), renovado
 * automaticamente antes de expirar. Feito à mão para não acrescentar dependências ao projeto.
 */
class CredencialGoogle {

    private static final String ESCOPO = "https://www.googleapis.com/auth/cloud-platform";

    private final ObjectMapper mapper;
    private final String email;
    private final String tokenUri;
    private final String projeto;
    private final PrivateKey chave;

    private String token;
    private Instant expiraEm = Instant.EPOCH;

    private CredencialGoogle(ObjectMapper mapper, String email, String tokenUri, String projeto, PrivateKey chave) {
        this.mapper = mapper;
        this.email = email;
        this.tokenUri = tokenUri;
        this.projeto = projeto;
        this.chave = chave;
    }

    static CredencialGoogle carregar(Path arquivo, ObjectMapper mapper) throws IOException, GeneralSecurityException {
        JsonNode json = mapper.readTree(Files.readString(arquivo));
        if (!"service_account".equals(json.path("type").asText())) {
            throw new IOException("o arquivo não é uma chave de conta de serviço (type = service_account)");
        }
        String pem = json.path("private_key").asText()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey chave = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        return new CredencialGoogle(mapper, json.path("client_email").asText(),
                json.path("token_uri").asText("https://oauth2.googleapis.com/token"), json.path("project_id").asText(""), chave);
    }

    String projeto() {
        return projeto;
    }

    String email() {
        return email;
    }

    synchronized String token(RestClient http) {
        if (token != null && Instant.now().isBefore(expiraEm.minusSeconds(60))) {
            return token;
        }
        try {
            long agora = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", email);
            claims.put("scope", ESCOPO);
            claims.put("aud", tokenUri);
            claims.put("iat", agora);
            claims.put("exp", agora + 3600);
            String conteudo = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                    + "." + base64Url(mapper.writeValueAsBytes(claims));
            Signature assinatura = Signature.getInstance("SHA256withRSA");
            assinatura.initSign(chave);
            assinatura.update(conteudo.getBytes(StandardCharsets.UTF_8));
            String jwt = conteudo + "." + base64Url(assinatura.sign());

            JsonNode resposta = http.post().uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                            + "&assertion=" + jwt)
                    .retrieve().body(JsonNode.class);
            token = resposta.path("access_token").asText();
            expiraEm = Instant.now().plusSeconds(resposta.path("expires_in").asLong(3600));
            return token;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Não foi possível assinar o token da conta de serviço: " + e.getMessage(), e);
        }
    }

    private static String base64Url(byte[] dados) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(dados);
    }
}
