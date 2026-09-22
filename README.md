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
│       ├── domain/
│       │   ├── entity/           # Cliente, Endereco, Cartao, LogTransacao (entidades JPA)
│       │   └── enums/            # GeneroCliente, StatusCliente, TipoEndereco, Bandeira
│       ├── application/         # ClienteService, EnderecoService, CartaoService, AuditoriaService + validation/ + exception/
│       └── adapter/
│           ├── in/web/          # ClienteController, EnderecoController, CartaoController, AuditoriaController, GlobalExceptionHandler, dto/
│           └── out/persistence/ # ClienteRepository, EnderecoRepository, CartaoRepository, LogTransacaoRepository (Spring Data JPA)
│       resources/                # application.properties
│       test/java/...            # testes unitários (JUnit + Mockito) do ClienteService
├── cypress/
│   ├── e2e/cliente-crud.cy.js   # suíte de testes de interface (37 testes)
│   └── support/cpf-generator.js # gera CPFs válidos únicos por execução
├── cypress.config.js
└── package.json
```

## Como executar

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

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
| RF0021 / RN0026 | Cadastro com campos obrigatórios (gênero, nome, data de nascimento, CPF, telefone, e-mail, senha, endereço residencial) | `POST /api/clientes` | "deve cadastrar um cliente com todos os campos obrigatórios válidos" |
| RN0026 | Nome obrigatório | `POST /api/clientes` | "não deve cadastrar cliente sem o nome" |
| RN0026 | Endereço residencial obrigatório (CEP) | `POST /api/clientes` | "não deve cadastrar cliente sem endereço residencial (CEP obrigatório)" |
| RN0026 | CPF e data de nascimento obrigatórios | `POST /api/clientes` | "não deve cadastrar cliente sem CPF e Data de Nascimento" |
| RN0026 | Telefone (tipo + DDD + número, campos separados) e e-mail obrigatórios | `POST /api/clientes` | "não deve cadastrar cliente sem telefone e e-mail" |
| RNF0031 | Senha forte | `POST /api/clientes`, `PATCH /api/clientes/{id}/senha` | "não deve cadastrar com senha fraca" / "não deve alterar para uma nova senha fraca" |
| RNF0032 | Confirmação de senha | `POST /api/clientes` | "não deve cadastrar quando a confirmação diverge" |
| RNF0033 | Senha criptografada (nunca em texto puro) | `POST /api/clientes` | validado no teste unitário `deveCadastrarClienteComDadosValidos` |
| RNF0035 | Código único de cliente | `POST /api/clientes` | validado no teste unitário (`getCodigoCliente().startsWith("CLI-")`) |
| — | CPF inválido / duplicado | `POST /api/clientes` | "não deve cadastrar com CPF inválido" / "...com o mesmo CPF" |
| RF0024 | Consulta com filtros combinados/isolados por nome, CPF, e-mail, código do cliente e status | `GET /api/clientes` | "deve listar" / "por nome" / "por CPF" / "pelo código do cliente" / "combinar filtros (nome + status)" |
| RF0022 | Alterar dados cadastrais (nome, telefone, e-mail, endereço) | `PUT /api/clientes/{id}` | "deve alterar nome, telefone, e-mail e endereço" |
| RF0028 | Alterar senha isoladamente, sem tocar nos demais dados | `PATCH /api/clientes/{id}/senha` | "deve alterar a senha do cliente com sucesso" / "senha atual incorreta" |
| RF0023 | Inativar cliente | `PATCH /api/clientes/{id}/inativar` | "deve inativar um cliente ativo" |
| RN — | Não permitir inativar um cliente já inativo | `PATCH /api/clientes/{id}/inativar` | "não deve permitir inativar um cliente que já está inativo" |
| — | Distinção inativação × exclusão (registro continua existindo e consultável) | `PATCH /api/clientes/{id}/inativar` | "a inativação NÃO deve excluir o cliente" |
| RF0026 | Cadastrar endereço (múltiplos, cobrança/entrega/ambos, apelido de identificação) | `POST /api/clientes/{id}/enderecos` | "deve adicionar um endereço de cobrança ao cliente" |
| RN0023 | Tipo do endereço obrigatório | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço sem o tipo selecionado" |
| RF0026 / RN0023 | Apelido (frase curta de identificação) obrigatório | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço sem a frase curta de identificação" |
| RN0023 | Logradouro, número, bairro e cidade obrigatórios | `POST /api/clientes/{id}/enderecos` | "não deve adicionar endereço faltando logradouro, número, bairro ou cidade" |
| RN0021 | Não remover o único endereço de cobrança | `DELETE /api/clientes/{id}/enderecos/{id}` | "não deve permitir remover o único endereço de cobrança" |
| RN0022 | Não remover o único endereço de entrega | `DELETE /api/clientes/{id}/enderecos/{id}` | "não deve permitir remover o único endereço de entrega" |
| RN0021 / RN0022 | Remover endereço quando não é o último do seu tipo | `DELETE /api/clientes/{id}/enderecos/{id}` | "deve remover um endereço quando não é o último do seu tipo" |
| RNF0034 | Alteração isolada de **endereço**, sem editar os demais dados do cliente | `PUT /api/clientes/{id}/enderecos/{id}` | citado no javadoc de `Endereco.atualizar()` |
| RF0027 / RN0024 | Cadastrar cartão (número, nome impresso, bandeira, código de segurança) | `POST /api/clientes/{id}/cartoes` | "deve adicionar um cartão e exibir o número mascarado" |
| RN0024 | Campos obrigatórios do cartão (nome impresso, código de segurança) | `POST /api/clientes/{id}/cartoes` | "não deve adicionar cartão faltando dados obrigatórios (Nome Impresso e CVV)" |
| RN0025 | Bandeira deve ser uma das aceitas pelo sistema | `POST /api/clientes/{id}/cartoes` | "não deve adicionar cartão com bandeira em branco ou não permitida" |
| RF0027 | Primeiro cartão cadastrado é automaticamente o preferencial | `POST /api/clientes/{id}/cartoes` | "o primeiro cartão cadastrado deve ser automaticamente o preferencial" |
| RF0027 | Troca de cartão preferencial | `PATCH /api/clientes/{id}/cartoes/{id}/preferencial` | "deve trocar o cartão preferencial, desmarcando o anterior" |
| RF0027 | Remover cartão | `DELETE /api/clientes/{id}/cartoes/{id}` | "deve remover um cartão" |
| RF0027 | Promoção automática ao remover o preferencial | `DELETE /api/clientes/{id}/cartoes/{id}` | "ao remover o cartão preferencial, outro cartão restante deve assumir o posto" |
| RNF0011 | Consultas respondidas em até 1s | `GET /api/clientes` | não medido em teste automatizado; ver índices em `nome`/`status` (Cliente) e `cliente_id` (Endereco/Cartao) em `@Table(indexes = ...)` |
| RNF0012 | Log de auditoria (data, hora, usuário, dado alterado) em toda inserção/alteração | `GET /api/auditoria` | "deve registrar no log de auditoria o cadastro de um novo cliente" / "...a inativação de um cliente" |

## Sobre o telefone (RN0026)

O DRS exige que o telefone seja composto por tipo, DDD e número — por
isso o campo não é um texto livre único, e sim três campos separados
(`telefoneTipo`, `telefoneDdd`, `telefoneNumero`) tanto no cadastro
quanto na alteração de dados cadastrais, com validação de formato
(DDD com 2 dígitos, número com 8 ou 9 dígitos) no backend.

## Sobre o escopo de Endereço e Cartão

O "endereço residencial" pedido em RN0026 (parte do cadastro básico do
cliente) é diferente dos endereços de RF0026: aquele é um dado de
identificação do cliente; estes são os endereços de entrega/cobrança
usados nas compras, com múltiplos registros por cliente. Por isso o
Cliente mantém seus próprios campos de endereço residencial, e
Endereco/Cartão são entidades à parte, vinculadas por `clienteId`.

Para que RN0021 e RN0022 (ao menos um endereço de cobrança e um de
entrega) já valham a partir do cadastro — e não só depois que o cliente
adicionar um endereço manualmente em RF0026 — o endereço residencial
informado no cadastro é replicado automaticamente como o primeiro
registro da lista de endereços, com tipo `AMBOS` (apelido "Residencial").
Ele aparece normalmente na tela de "Endereços do cliente" e pode ser
removido como qualquer outro, desde que a remoção não deixe o cliente
sem cobrança ou sem entrega.

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
