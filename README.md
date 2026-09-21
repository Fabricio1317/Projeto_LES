# Nexus — CRUD de Cliente (backend + frontend + testes automatizados)

Módulo completo de gestão de clientes do Nexus: cadastrar, consultar,
alterar e inativar clientes, mais a gestão de endereços (RF0026), cartões
de crédito (RF0027) e troca de senha (RF0028) — com testes automatizados
de interface (Cypress) cobrindo os RF/RN/RNF do DRS.

## Estrutura

```
nexus-crud/
├── backend/                     # Spring Boot (Java 21)
│   ├── pom.xml
│   └── src/main/java/com/games/cliente/
│       ├── domain/              # Cliente, StatusCliente, GeneroCliente, Endereco, TipoEndereco, Cartao, Bandeira, LogTransacao
│       ├── application/         # ClienteService, EnderecoService, CartaoService, AuditoriaService + validation/ + exception/
│       └── adapter/
│           ├── in/web/          # ClienteController, EnderecoController, CartaoController, AuditoriaController, GlobalExceptionHandler, dto/
│           └── out/persistence/ # ClienteRepository, EnderecoRepository, CartaoRepository, LogTransacaoRepository (Spring Data JPA)
│       resources/                # application.properties (H2 em memória)
│       test/java/...            # testes unitários (JUnit + Mockito) do ClienteService
├── cypress/
│   ├── e2e/cliente-crud.cy.js   # suíte de testes de interface (29 testes)
│   └── support/cpf-generator.js # gera CPFs válidos únicos por execução
├── cypress.config.js
└── package.json
```

## Como executar

### 1. Backend (H2 em memória, zero configuração externa)

```bash
cd backend
mvn spring-boot:run
```

Console H2 disponível em `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:nexusdb`, usuário `sa`, sem senha).

### 2. Testes automatizados de interface (Cypress)

Com o backend rodando em outro terminal (o frontend é servido pelo próprio Spring Boot):

```bash
npm install
npx cypress open     # modo interativo, bom para a apresentação ao vivo
# ou
npx cypress run      # modo headless, para rodar toda a suíte de uma vez
```

## Tabela de rastreabilidade — RF/RN/RNF → Endpoint → Teste

| RF/RN/RNF | Regra | Endpoint | Teste Cypress |
|---|---|---|---|
| RF0021 / RN0026 | Cadastro com campos obrigatórios | `POST /api/clientes` | "deve cadastrar um cliente com todos os campos obrigatórios válidos" |
| RNF0031 | Senha forte | `POST /api/clientes` | "não deve cadastrar com senha fraca" |
| RNF0032 | Confirmação de senha | `POST /api/clientes` | "não deve cadastrar quando a confirmação diverge" |
| RNF0033 | Senha criptografada (nunca em texto puro) | `POST /api/clientes` | validado no teste unitário `deveCadastrarClienteComDadosValidos` |
| RNF0035 | Código único de cliente | `POST /api/clientes` | validado no teste unitário (`getCodigoCliente().startsWith("CLI-")`) |
| — | CPF inválido / duplicado | `POST /api/clientes` | "não deve cadastrar com CPF inválido" / "...CPF duplicado" |
| RF0024 | Consulta com filtros combinados/isolados | `GET /api/clientes` | "deve listar" / "filtrar por nome" / "por CPF" / "combinar filtros" |
| RF0022 | Alterar dados cadastrais | `PUT /api/clientes/{id}` | "deve alterar nome, telefone, e-mail e endereço" |
| RF0028 | Alterar senha isoladamente | `PATCH /api/clientes/{id}/senha` | "deve alterar a senha do cliente com sucesso" |
| RF0023 | Inativar cliente | `PATCH /api/clientes/{id}/inativar` | "deve inativar um cliente ativo" |
| — | Distinção inativação × exclusão | `PATCH /api/clientes/{id}/inativar` | "a inativação NÃO deve excluir o cliente" |
| RF0026 | Cadastrar endereço (múltiplos, cobrança/entrega) | `POST /api/clientes/{id}/enderecos` | "deve adicionar um endereço de cobrança ao cliente" |
| RN0021 | Não remover o único endereço de cobrança | `DELETE /api/clientes/{id}/enderecos/{id}` | "não deve permitir remover o único endereço de cobrança" |
| RN0022 | Não remover o único endereço de entrega | `DELETE /api/clientes/{id}/enderecos/{id}` | coberto pelo mesmo teste acima (regra simétrica) |
| RNF0034 | Alteração isolada de **endereço** | `PUT /api/clientes/{id}/enderecos/{id}` | citado no javadoc de `Endereco.atualizar()` |
| RF0027 | Cadastrar cartão (múltiplos, um preferencial) | `POST /api/clientes/{id}/cartoes` | "deve adicionar um cartão e exibir o número mascarado" |
| RN0025 | Bandeira deve ser uma das aceitas | `POST /api/clientes/{id}/cartoes` | validação garantida pelo enum `Bandeira` (rejeitado pelo Jackson se inválida) |
| — | Troca de cartão preferencial | `PATCH /api/clientes/{id}/cartoes/{id}/preferencial` | "deve trocar o cartão preferencial, desmarcando o anterior" |
| — | Promoção automática ao remover o preferencial | `DELETE /api/clientes/{id}/cartoes/{id}` | "ao remover o cartão preferencial, outro cartão restante deve assumir o posto" |
| RNF0011 | Consultas respondidas em até 1s | `GET /api/clientes` | Índices em `nome` e `status` (Cliente) e em `cliente_id` (Endereco/Cartao) — ver `@Table(indexes = ...)` |
| RNF0012 | Log de auditoria (data, hora, usuário, dado alterado) | `GET /api/auditoria` | "deve registrar no log de auditoria o cadastro de um novo cliente" / "...a inativação de um cliente" |

## Sobre o escopo de Endereço e Cartão

O "endereço residencial" pedido em RN0026 (parte do cadastro básico do
cliente) é diferente dos endereços de RF0026: aquele é um dado de
identificação do cliente; estes são os endereços de entrega/cobrança
usados nas compras, com múltiplos registros por cliente. Por isso o
Cliente mantém seus próprios campos de endereço residencial, e
Endereco/Cartão são entidades à parte, vinculadas por `clienteId`.

## Sobre o log de auditoria (RNF0012)

Como este módulo isolado de Cliente não implementa autenticação (não há
login de administrador separado), o campo "usuário responsável" é
preenchido com o código do próprio cliente afetado — já que toda ação
aqui é o cliente gerenciando seus próprios dados. Em um sistema com login
real, este valor viria do usuário autenticado na sessão. A auditoria
cobre inserções e alterações (INSERT/UPDATE), conforme o texto literal
do requisito; remoções de endereço/cartão não geram log, pois RNF0012
menciona apenas inserção e alteração.

## Fora do escopo deste módulo (pertencem a Pedido/Compra)

- **RF0025** — consulta de histórico de transações do cliente
- **RN0027** — cálculo real do ranking numérico por perfil de compra
  (o campo existe e inicia em 0, mas seu cálculo depende de dados de
  pedidos que não existem neste recorte isolado de Cliente)
