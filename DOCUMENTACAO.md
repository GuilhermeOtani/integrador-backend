# Documentação do Projeto Integrador 2026

## 1. Escopo desta documentação

Este documento descreve o estado efetivamente implementado no backend em **31 de agosto de 2026**. As informações abaixo foram conferidas diretamente nos controllers, services, repositories, entidades, DTOs, enums, configurações, `pom.xml` e teste existente.

O projeto está em desenvolvimento. Por isso, o documento diferencia recursos expostos pela API REST, recursos apenas internos, regras de negócio implementadas e limitações presentes no código atual.

---

## 2. Visão geral

O Projeto Integrador é um backend para gestão de transporte escolar/universitário. A aplicação usa **Java 21**, **Spring Boot 4.0.3**, Spring Web MVC, Spring Data JPA, Spring Security, PostgreSQL e Lombok.

No estado atual, a API possui operações para alunos, motoristas, ônibus, faculdades, rotas, pontos de embarque, viagens, grades diárias, contas a pagar, pagamentos e recibos. Também possui autenticação JWT, consulta do perfil autenticado e administração de contas de acesso.

Continuam apenas internos os recursos de pessoa, conta a receber e chamado de manutenção. `Recebimento` existe somente como entidade.

---

## 3. Arquitetura

A aplicação segue uma arquitetura em camadas:

```text
Requisição HTTP
      |
      v
Controller  ->  Service  ->  Repository  ->  PostgreSQL
      |             |
      |             +-- regras de negócio e conversões
      +-- entidades ou DTOs em JSON
```

Estrutura principal:

```text
src/main/java/com/example/integrador/
|-- Config/       configurações de CORS
|-- Controller/   endpoints REST
|-- DTO/          objetos de entrada e resposta
|-- Enum/         valores enumerados persistidos como texto
|-- Model/        entidades JPA
|-- Repository/   interfaces Spring Data JPA
|-- Security/     configuração do Spring Security
|-- Service/      CRUD, conversões e regras de negócio
`-- IntegradorApplication.java
```

`IntegradorApplication` habilita o agendamento com `@EnableScheduling`.

---

## 4. Tecnologias e configuração

| Item | Estado atual |
|---|---|
| Java | 21, definido em `pom.xml` |
| Spring Boot | 4.0.3 |
| Persistência | Spring Data JPA e Hibernate |
| API web | Spring Web MVC |
| Segurança | Spring Security 7, JWT HS256 stateless, BCrypt e RBAC |
| Migrações | Flyway com baseline V1, JWT V2 e administração segura V3 |
| Validação | Jakarta Bean Validation nos contratos de autenticação e usuários |
| Banco | PostgreSQL |
| Redução de boilerplate | Lombok |
| Build | Maven Wrapper 3.3.4, Maven 3.9.12 configurado |
| Empacotamento | JAR, versão `0.0.1-SNAPSHOT` |

Configuração ativa de `application.properties`:

| Propriedade | Valor atual |
|---|---|
| Nome da aplicação | `integrador` |
| URL JDBC | `jdbc:postgresql://localhost:5432/integrador` |
| Usuário do banco | `postgres` |
| Senha do banco | `postgres` |
| Estratégia do schema | `spring.jpa.hibernate.ddl-auto=validate` |
| Flyway | habilitado, com baseline automático na versão 1 |
| Emissor JWT | `integrador-api` |
| Validade JWT | 2 horas (`PT2H`) |
| Origem CORS | `http://localhost:4200` |
| Exibição de SQL | habilitada |
| Limite de formulário do Tomcat | `10MB` |
| Porta HTTP | padrão do Spring Boot, normalmente `8080` |

> A senha do banco ainda está versionada em texto simples. Para outros ambientes, substitua-a por configuração externa ou variável de ambiente. Já o segredo JWT e a senha inicial do administrador são obrigatoriamente externos e não possuem valor padrão utilizável.

---

## 5. Modelo de domínio atual

Todos os identificadores são `Long` com geração `IDENTITY`, salvo indicação em contrário. Os enums são persistidos com `EnumType.STRING`.

### 5.1 Pessoa

`Pessoa` é uma entidade concreta e também a superclasse Java de `Aluno` e `Motorista`. Como a hierarquia não declara outra estratégia, o JPA usa `SINGLE_TABLE`: existe somente a tabela `pessoa`, e a coluna discriminadora `dtype` identifica `Pessoa`, `Aluno` ou `Motorista`. Não existem tabelas `aluno` e `motorista`.

- `id`: `Long`;
- `nome`: `String`;
- `cpfCnpj`: `String`;
- `telefone`: `String`;
- `tipoPessoa`: `TipoPessoa`.

### 5.2 Aluno

Estende `Pessoa` e acrescenta:

- `matricula`: `String`;
- `statusMatricula`: `StatusMatricula`;
- `dataCadastro`: `LocalDate`;
- `mensalidade`: `BigDecimal`;
- `faculdade`: relação N:1 com `Faculdade`.

No código atual, `Aluno` **não possui relação com `Rota` nem com `Viagem`**.

### 5.3 Motorista

Estende `Pessoa` e acrescenta `cnh` (`String`) e `salario` (`BigDecimal`). No modelo atual, `Motorista` **não possui associação direta com `Onibus`**. Uma viagem associa separadamente um motorista e um ônibus.

### 5.4 Usuario

Entidade separada que implementa `UserDetails` e representa exclusivamente as credenciais de uma pessoa:

- `id`: `Long`;
- `email`: `String`, usado como username;
- `senha`: hash BCrypt, retornado internamente por `getPassword()` e ignorado na serialização JSON;
- `tipoPessoa`: `TipoPessoa`, obrigatório;
- `ativo`: `boolean`, controla se a conta pode autenticar, iniciado como `true`;
- `tokenVersion`: `long`, versão interna usada para revogar JWTs, iniciada em zero;
- `pessoa`: relação 1:1 obrigatória e única com `Pessoa`, pela FK `usuario.pessoa_id`.

`getAuthorities()` produz uma autoridade no formato `ROLE_<TIPO_PESSOA>`. Expiração, bloqueio e credencial expirada retornam `true`; `isEnabled()` reflete `ativo`.

O e-mail é normalizado para minúsculas e possui unicidade case-insensitive no banco. Uma pessoa só pode estar vinculada a uma conta. Para aluno e motorista, o papel é derivado da subclasse real da pessoa e não é aceito da requisição. O primeiro administrador é criado por configuração externa; administradores autenticados podem criar outros administradores. `senha` e `tokenVersion` nunca fazem parte de `UsuarioResponse`.

### 5.5 Faculdade

- `id`: `Long`;
- `nome`: `String`;
- `endereco`: `String`;
- `telefone`: `String`.

### 5.6 Onibus

- `id`: `Long`;
- `numeroIdentificacao`: `String`;
- `placa`: `String`;
- `modelo`: `String`;
- `statusOnibus`: `StatusOnibus`;
- `fotoUrl`: `String`, persistida como `TEXT` com `@Lob`;
- `capacidade`: `int`.

O código não define restrição `unique` para `numeroIdentificacao` ou `placa`.

### 5.7 Rota

- `id`: `Long`;
- `nome`: `String`;
- `descricao`: `String`;
- `faculdade`: relação N:1 com `Faculdade`;
- `pontosEmbarque`: relação N:M com `PontoEmbarque`, com cascade `MERGE`.

### 5.8 PontoEmbarque

- `id`: `Long`;
- `ordemParada`: `Integer`;
- `descricao`: `String`;
- `rota`: relação N:1 com `Rota`, omitida do JSON por `@JsonIgnore`.

Há uma inconsistência de mapeamento: `Rota.pontosEmbarque` declara N:M, enquanto `PontoEmbarque.rota` declara N:1. Esses campos representam associações JPA separadas, pois não usam `mappedBy` entre si.

### 5.9 Viagem

- `id`: `Long`;
- `data`: `LocalDate`;
- `rota`: relação N:1 com `Rota`;
- `onibus`: relação N:1 com `Onibus`;
- `motorista`: relação N:1 com `Motorista`;
- `gradeDiaria`: relação N:1 com `GradeDiaria`;
- `faculdades`: relação N:M com `Faculdade`, usando a tabela `viagem_faculdade`.

Não existe coleção de alunos em `Viagem`.

### 5.10 GradeDiaria

- `id`: `Long`;
- `diaSemana`: `DiaSemana`;
- `descricao`: `String`;
- `viagens`: relação 1:N com `Viagem`, `mappedBy="gradeDiaria"`, cascade `ALL` e remoção de órfãos.

A entidade não possui campo `data`. O campo `data` existe apenas em `GradeDiariaDTO` e, portanto, não é persistido.

### 5.11 ContaPagar

- `id`: `Long`;
- `descricao`: `String`;
- `valor`: `BigDecimal`;
- `dataVencimento`: `LocalDate`;
- `status`: `StatusFinanceiro`;
- `motorista`: relação N:1 com `Pessoa`, armazenada pela coluna `pessoa_id`.

Embora o atributo se chame `motorista`, seu tipo permite qualquer registro de `Pessoa`.

### 5.12 Pagamento

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `dataPagamento`: `LocalDate`;
- `formaPagamento`: `FormaPagamento`;
- `contaPagar`: relação N:1 com `ContaPagar`, pela coluna `conta_pagar_id`.

### 5.13 ContaReceber

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `descricao`: `String`;
- `dataVencimento`: `LocalDate`;
- `status`: `StatusFinanceiro`;
- `aluno`: relação N:1 com `Aluno`.

Existe repository e service de CRUD, mas não existe controller REST.

### 5.14 Recebimento

- `id`: `Long`;
- `valor`: `BigDecimal`;
- `dataRecebimento`: `LocalDate`;
- `formaPagamento`: `FormaPagamento`;
- `contaReceber`: relação N:1 com `ContaReceber`.

Existe somente a entidade: não há repository, service ou controller para recebimentos.

### 5.15 ChamadoManutencao

- `id`: `Long`;
- `descricao`: `String`;
- `dataAbertura`: `LocalDate`;
- `urgencia`: `NivelUrgencia`;
- `status`: `StatusChamado`;
- `onibus`: relação N:1 com `Onibus`;
- `responsavel`: relação N:1 com `Pessoa`.

Existe repository e service de CRUD, mas não existe controller REST.

---

## 6. DTOs e contratos específicos

### AlunoDTO

Usado em `POST` e `PUT` de alunos: `id`, `nome`, `cpfCnpj`, `telefone`, `matricula` e `faculdadeId`.

Não contém `statusMatricula`, `dataCadastro`, `mensalidade` ou `tipoPessoa`. Esses campos não são definidos pelo contrato atual de criação/atualização de aluno.

### MotoristaDTO

Usado nas entradas e respostas de motoristas: `id`, `nome`, `cpfCnpj`, `cnh`, `salario` e `telefone`. Não contém ônibus nem `tipoPessoa`.

### ViagemDTO

- `id`, `data`;
- `rotaId`, `rotaNome`;
- `onibusId`, `onibusPlaca`, `numeroIdentificacao`;
- `motoristaId`, `motoristaNome`;
- `faculdades`: lista de objetos `Faculdade`.

Não contém `gradeDiariaId`. Na conversão de entidade para DTO, a lista de faculdades só é copiada dentro da condição que verifica se há motorista; uma viagem sem motorista pode responder com `faculdades` vazia mesmo que a relação esteja preenchida.

### GradeDiariaDTO

Contém `id`, `data`, `diaSemana`, `descricao` e `viagens` (`List<ViagemDTO>`). O campo `data` não corresponde a nenhum campo da entidade e não é preenchido pelo construtor de resposta.

### ReciboDTO

Contém `id`, `valor`, `dataPagamento`, `formaPagamento`, `nomeMotorista` e `descricaoConta`. O nome vem de `pagamento.contaPagar.motorista.nome`. Não há data de vencimento no DTO atual.

---

## 7. API REST disponível

URL base local esperada: `http://localhost:8080`.

Salvo onde indicado, os controllers não definem códigos HTTP específicos: criações e atualizações normalmente respondem `200 OK`, e exclusões respondem `200 OK` sem corpo.

### 7.1 Alunos

| Método | Caminho | Entrada | Saída |
|---|---|---|---|
| GET | `/aluno/listar` | — | lista de `Aluno` |
| GET | `/aluno/buscar-aluno/{id}` | id | `Aluno` |
| POST | `/aluno/salvar-aluno` | `AlunoDTO` | `Aluno` |
| PUT | `/aluno/atualizar-aluno/{id}` | `AlunoDTO` | `Aluno` |
| DELETE | `/aluno/deletar-aluno/{id}` | id | sem corpo |

### 7.2 Motoristas

| Método | Caminho | Entrada | Saída |
|---|---|---|---|
| GET | `/motorista/listar` | — | lista de `MotoristaDTO` |
| GET | `/motorista/buscar-motorista/{id}` | id | `MotoristaDTO` |
| POST | `/motorista/salvar-motorista` | `MotoristaDTO` | `MotoristaDTO` |
| PUT | `/motorista/atualizar-motorista/{id}` | `MotoristaDTO` | `MotoristaDTO` |
| DELETE | `/motorista/deletar-motorista/{id}` | id | sem corpo |

### 7.3 Ônibus

| Método | Caminho |
|---|---|
| GET | `/onibus/listar` |
| GET | `/onibus/buscar-onibus/{id}` |
| POST | `/onibus/salvar-onibus` |
| PUT | `/onibus/atualizar-onibus/{id}` |
| DELETE | `/onibus/deletar-onibus/{id}` |

POST e PUT recebem a entidade `Onibus` em JSON.

### 7.4 Faculdades

| Método | Caminho |
|---|---|
| GET | `/faculdade/listar` |
| GET | `/faculdade/buscar-faculdade/{id}` |
| POST | `/faculdade/salvar-faculdade` |
| PUT | `/faculdade/atualizar-faculdade/{id}` |
| DELETE | `/faculdade/deletar-faculdade/{id}` |

POST e PUT recebem a entidade `Faculdade` em JSON.

### 7.5 Rotas

| Método | Caminho |
|---|---|
| GET | `/rota/listar` |
| GET | `/rota/buscar-rota/{id}` |
| POST | `/rota/salvar-rota` |
| PUT | `/rota/{id}` |
| DELETE | `/rota/deletar-rota/{id}` |

POST e PUT recebem `Rota`, incluindo faculdade e pontos quando aplicável. O caminho de atualização é `/rota/{id}`, não `/rota/atualizar-rota/{id}`.

### 7.6 Pontos de embarque

Os segmentos internos usam `pontoEmbarque` com E maiúsculo; os caminhos são sensíveis à grafia.

| Método | Caminho |
|---|---|
| GET | `/ponto-embarque/listar` |
| GET | `/ponto-embarque/buscar-pontoEmbarque/{id}` |
| POST | `/ponto-embarque/salvar-pontoEmbarque` |
| PUT | `/ponto-embarque/atualizar-pontoEmbarque/{id}` |
| DELETE | `/ponto-embarque/deletar-pontoEmbarque/{id}` |

POST e PUT recebem `PontoEmbarque`.

### 7.7 Viagens

| Método | Caminho | Entrada/saída |
|---|---|---|
| GET | `/viagem/listar` | lista de `ViagemDTO` |
| GET | `/viagem/buscar-viagem/{id}` | `ViagemDTO` |
| POST | `/viagem/salvar-viagem` | recebe e devolve `ViagemDTO` |
| PUT | `/viagem/atualizar-viagem/{id}` | recebe e devolve `ViagemDTO` |
| DELETE | `/viagem/deletar-viagem/{id}` | sem corpo |

Exceções `RuntimeException` nesse controller são convertidas em `400 Bad Request` com a mensagem no corpo.

### 7.8 Grades diárias

| Método | Caminho | Entrada/saída |
|---|---|---|
| GET | `/grade-diaria/listar` | lista de `GradeDiariaDTO` |
| GET | `/grade-diaria/buscar-grade/{id}` | `GradeDiariaDTO` |
| POST | `/grade-diaria/salvar-grade` | recebe e devolve `GradeDiariaDTO` |
| PUT | `/grade-diaria/atualizar-grade/{id}` | recebe e devolve `GradeDiariaDTO` |
| DELETE | `/grade-diaria/deletar-grade/{id}` | sem corpo |

Exceções `RuntimeException` nesse controller são convertidas em `400 Bad Request`.

### 7.9 Contas a pagar

| Método | Caminho |
|---|---|
| GET | `/contapagar/listar` |
| GET | `/contapagar/buscar-contapagar/{id}` |
| POST | `/contapagar/salvar-contapagar` |
| PUT | `/contapagar/atualizar-contapagar/{id}` |
| DELETE | `/contapagar/deletar-contapagar/{id}` |

POST e PUT recebem `ContaPagar`.

### 7.10 Pagamentos e recibos

| Método | Caminho | Parâmetros | Resultado |
|---|---|---|---|
| POST | `/pagamentos/realizar/{contaId}` | query obrigatória `formaPagamento`; query opcional `valorPago` | `Pagamento` |
| GET | `/pagamentos/recibo/conta/{contaId}` | id da conta | `ReciboDTO` ou `404 Not Found` |

Exemplo:

```http
POST /pagamentos/realizar/10?formaPagamento=PIX&valorPago=1500.00
```

Não existem endpoints genéricos de listar, buscar por id, atualizar ou excluir pagamentos.

### 7.11 Autenticação

| Método | Caminho | Acesso | Resultado |
|---|---|---|---|
| POST | `/auth/login` | público | JWT e resumo seguro do usuário |
| GET | `/auth/me` | autenticado | usuário, pessoa e perfil específico do papel |

O login recebe `email` e `senha`. A resposta contém `accessToken`, `tokenType="Bearer"`, `expiresIn=7200` e `usuario`. O resumo do usuário inclui `ativo`, mas nunca senha ou versão interna. O JWT possui somente `sub`, `email`, `pessoaId`, `roles`, `ver`, `iat`, `exp` e `iss`. `ver` corresponde à versão de segurança atual da conta.

### 7.12 Administração de usuários

Todos estes endpoints exigem `ROLE_ADMIN`:

| Método | Caminho | Função |
|---|---|---|
| GET | `/usuarios` | lista contas sem o hash da senha |
| GET | `/usuarios/pessoas-sem-usuario` | lista alunos e motoristas ainda sem conta |
| POST | `/usuarios` | cria conta para uma pessoa existente |
| POST | `/usuarios/admin` | cria uma pessoa-base e sua conta administrativa |
| PATCH | `/usuarios/{id}` | altera somente o e-mail e retorna a conta segura |
| PUT | `/usuarios/{id}/senha` | redefine a senha e retorna `204 No Content` |
| PATCH | `/usuarios/{id}/status` | desativa ou reativa a conta e retorna a conta segura |
| DELETE | `/usuarios/{id}` | exclui somente o acesso e retorna `204 No Content` |

`POST /usuarios` recebe `pessoaId`, `email` e `senha`. O papel é derivado pelo backend. E-mail repetido ou pessoa já vinculada retorna `409 Conflict`.

Os novos corpos são, respectivamente, `{"email":"novo@email.com"}`, `{"senha":"NovaSenha123"}` e `{"ativo":false}`. E-mail e status iguais ao valor atual são idempotentes. Alterar e-mail, redefinir senha, desativar ou reativar incrementa `tokenVersion` e invalida imediatamente os tokens anteriores.

Não é permitido desativar ou excluir a própria conta, nem remover o último ADMIN ativo. Essas violações e e-mail duplicado retornam `409 Conflict`; id inexistente retorna `404` e validação inválida retorna `400`. A exclusão apaga apenas `Usuario`: a `Pessoa` permanece e, no caso de ALUNO ou MOTORISTA, volta a aparecer em `/usuarios/pessoas-sem-usuario`.

### 7.13 Viagens do motorista autenticado

`GET /viagem/minhas` exige `ROLE_MOTORISTA`. O serviço usa o claim `pessoaId` do JWT e ignora qualquer tentativa de o cliente escolher outro motorista, retornando apenas as viagens vinculadas à pessoa autenticada.

### 7.14 Recursos sem endpoint

Não há controllers para `Pessoa`, `ContaReceber`, `Recebimento` ou `ChamadoManutencao`.

---

## 8. Regras de negócio implementadas

### 8.1 Primeira conta de salário

Ao cadastrar um motorista pela API, `MotoristaService` salva o motorista e cria uma `ContaPagar` com descrição `Salário - <nome>`, o salário como valor, vencimento em 30 dias e status `PENDENTE`.

Não há transação envolvendo explicitamente as duas gravações. Uma falha ao criar a conta pode ocorrer depois de o motorista ter sido salvo.

### 8.2 Geração recorrente de contas

`RotinaFinanceiraService` executa diariamente às **02:00**, pelo cron `0 0 2 * * *`.

Para cada motorista, procura a conta com vencimento mais recente. Se ela vencer hoje ou já estiver vencida, cria outra com vencimento 30 dias depois da última. Se o motorista não tiver conta anterior, a rotina não cria a primeira conta.

### 8.3 Pagamento de conta

- a conta precisa existir;
- contas com status `PAGO` não podem ser pagas novamente;
- a data é definida como a data atual;
- sem `valorPago`, usa-se o valor integral da conta;
- após gravar o pagamento, a conta é marcada como `PAGO`.

O código também marca a conta como paga quando `valorPago` é inferior ao valor da conta; não existe controle de saldo ou pagamento parcial.

### 8.4 Validação de capacidade da viagem

Ao criar ou atualizar uma viagem com ônibus e faculdades, o serviço soma todos os alunos de cada faculdade cujo status seja `ATIV0` e rejeita a operação quando o total excede `Onibus.capacidade`.

A validação considera todos os alunos ativos das faculdades, não alunos individualmente vinculados à viagem.

### 8.5 Grade diária e viagens

Ao salvar uma grade, as viagens do DTO são convertidas e associadas a ela. Ao atualizar com uma lista não nula, a coleção antiga é limpa e substituída. Como há cascade `ALL` e `orphanRemoval=true`, viagens removidas da coleção podem ser excluídas do banco.

---

## 9. Enumerações aceitas

Os valores devem ser enviados exatamente como escritos.

| Enum | Valores atuais |
|---|---|
| `DiaSemana` | `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA` |
| `FormaPagamento` | `PIX`, `DINHEIRO`, `CARTAO`, `BOLETO`, `TRANSFERENCIA` |
| `NivelUrgencia` | `BAIXA`, `MEDIA`, `ALTA` |
| `StatusChamado` | `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO` |
| `StatusFinanceiro` | `PENDENTE`, `PAGO`, `ATRASADO` |
| `StatusMatricula` | `ATIV0`, `INATIV0`, `PENDENTE` |
| `StatusOnibus` | `OPERANDO`, `MANUTENCAO`, `QUEBRADO`, `INATIVO` |
| `TipoPessoa` | `ADMIN`, `ALUNO`, `MOTORISTA` |

> `ATIV0` e `INATIV0` terminam com o algarismo zero no código-fonte. Enviar `ATIVO` ou `INATIVO` para `StatusMatricula` causa erro de desserialização.

---

## 10. Segurança e CORS

### Segurança efetiva

A API usa sessão `STATELESS` e OAuth2 Resource Server para validar JWT próprio, sem filtro artesanal. O token é assinado com HS256 por uma chave Base64 externa, tem emissor `integrador-api` e expira em duas horas. CSRF está desabilitado porque a autenticação ocorre exclusivamente pelo cabeçalho Bearer.

As senhas são armazenadas apenas com BCrypt. O JWT é assinado, não criptografado, e por isso não contém senha, CPF/CNPJ, telefone ou outros dados desnecessários. Em cada requisição autenticada, o validador consulta `usuario` pelo `sub` e compara o claim `ver` com `token_version`. Conta inexistente, inativa ou com versão divergente recebe `401`, permitindo revogação imediata após alterações sensíveis. Tokens antigos sem `ver` também recebem `401`.

O logout comum continua local: se nenhuma alteração de segurança ocorrer, o token descartado pelo frontend permanece válido até `exp`. Editar e-mail, redefinir senha, mudar status ou excluir a conta invalida os tokens no servidor imediatamente.

Permissões efetivas:

| Recurso | Público | ADMIN | MOTORISTA | ALUNO |
|---|---:|---:|---:|---:|
| `POST /auth/login` e `OPTIONS` | sim | sim | sim | sim |
| `GET /auth/me` | não | sim | sim | sim |
| `/usuarios/**` | não | sim | não | não |
| `GET /viagem/minhas` | não | não | sim | não |
| GET de `/rota/**`, `/grade-diaria/**` e `/ponto-embarque/**` | não | sim | não | sim |
| Contas, pagamentos e financeiro | não | sim | não | não |
| Demais rotas existentes | não | sim | não | não |

Ausência, expiração, adulteração, versão divergente ou conta desabilitada/excluída retorna `401`. Papel insuficiente retorna `403`. Além das regras por URL, os serviços sensíveis usam `@PreAuthorize`.

### CORS efetivo

O CORS é centralizado em `/**`. Somente `http://localhost:4200` é aceito, para `GET`, `POST`, `PUT`, `DELETE`, `PATCH` e `OPTIONS`, com os cabeçalhos `Authorization` e `Content-Type`. Os `@CrossOrigin("*")` foram removidos dos controllers.

### Formato de erros

As falhas novas seguem `application/problem+json`/`ProblemDetail`: validação `400`, autenticação `401`, autorização `403`, recurso inexistente `404` e conflito `409`. Credenciais incorretas sempre usam a mensagem genérica “E-mail ou senha inválidos”. Alguns controllers antigos ainda possuem tratamento local de `RuntimeException` em texto e permanecem listados como dívida técnica.

---

## 11. Execução local

### Pré-requisitos

- JDK 21;
- PostgreSQL em execução;
- banco `integrador` criado;
- acesso ao Maven Central na primeira resolução de dependências.

Configuração esperada pelo arquivo atual:

```text
host: localhost
porta: 5432
banco: integrador
usuário: postgres
senha: postgres
```

Antes da primeira inicialização, configure uma chave JWT Base64 de pelo menos 32 bytes e os dados do administrador inicial. Exemplo para PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:BOOTSTRAP_ADMIN_NAME = "Administrador"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@exemplo.com"
$env:BOOTSTRAP_ADMIN_PASSWORD = "troque-por-uma-senha-forte"
```

Esses valores valem apenas para o processo/terminal atual e não devem ser gravados no Git. Se já existir um usuário `ADMIN`, o bootstrap não cria outro e as três variáveis de administrador deixam de ser necessárias. `JWT_SECRET` continua obrigatório em toda inicialização.

Com o build corrigido, os comandos previstos para Windows são:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Em Linux/macOS:

```bash
./mvnw test
./mvnw spring-boot:run
```

### Migrações do banco

O elemento Maven inválido que bloqueava a compilação foi removido. O schema agora é administrado pelo Flyway:

- `V1__baseline_schema.sql`: descreve o schema completo para bancos novos;
- `V2__jwt_usuario_pessoa.sql`: preenche `pessoa.tipo_pessoa`, corrige o check de matrícula e adiciona a FK 1:1, obrigatoriedade e unicidades de `usuario`;
- `V3__administracao_segura_usuario.sql`: adiciona `usuario.ativo BOOLEAN NOT NULL DEFAULT TRUE` e `usuario.token_version BIGINT NOT NULL DEFAULT 0`, preservando as contas existentes;
- em um PostgreSQL antigo sem histórico Flyway, `baseline-on-migrate=true` registra a versão 1 e aplica as evoluções posteriores;
- depois das migrações, Hibernate usa `ddl-auto=validate` e falha se o mapeamento divergir do banco.

Antes de executar a migração em um ambiente importante, faça backup do banco. Os registros existentes de `pessoa` não são apagados.

---

## 12. Testes e tratamento de erros

Os testes usam H2 em modo PostgreSQL, Flyway desabilitado e `ddl-auto=create-drop`, sem depender do banco local. A suíte cobre carregamento do contexto, bootstrap único, vínculo usuário-pessoa, derivação dos papéis, conflitos, BCrypt, login, claims e duração do JWT, tokens expirado/adulterado/sem versão, matriz `200/401/403`, isolamento das viagens do motorista, CORS, ausência de senha/versão nas respostas, idempotência, revogação após alterações, conta inativa, proteções administrativas e preservação de `Pessoa` após excluir o acesso.

O `RestExceptionHandler` padroniza as falhas de autenticação, validação, inexistência, conflito e integridade com `ProblemDetail`. `ViagemController` e `GradeDiariaController` ainda mantêm handlers locais antigos para `RuntimeException`, e recibos continuam retornando `404` por `ResponseEntity` quando não encontrados.

---

## 13. Limitações e pontos de atenção identificados

1. As credenciais do PostgreSQL ainda estão em texto simples no repositório.
2. `ATIV0` e `INATIV0` usam zero e podem causar integração incorreta.
3. `Rota` e `PontoEmbarque` modelam a relação de duas formas independentes: N:M de um lado e N:1 do outro.
4. `GradeDiariaDTO.data` não é persistido nem preenchido nas respostas.
5. `ViagemDTO` pode omitir faculdades quando a viagem não possui motorista.
6. O DTO de aluno não aceita status, data de cadastro ou mensalidade. O tipo de pessoa agora é preenchido pelo serviço.
7. O DTO de motorista não aceita tipo de pessoa; o serviço o define automaticamente como `MOTORISTA`.
8. A capacidade da viagem usa todos os alunos `ATIV0` das faculdades, pois não existe vínculo aluno-viagem.
9. Pagamento parcial marca a conta integralmente como `PAGO`.
10. `PagamentoRepository.findByContaPagarId` espera um resultado, embora o modelo não imponha unicidade.
11. Pessoa, conta a receber e chamado têm camadas internas, mas não API; recebimento possui somente entidade.
12. A validação Bean Validation foi adicionada aos contratos novos de autenticação e usuários, mas os DTOs CRUD antigos ainda não possuem validação completa.
13. O JWT não possui refresh token nem lista geral de bloqueio; a revogação ocorre por versão da conta e exige uma consulta ao banco em cada requisição autenticada. O logout comum apenas remove a sessão do frontend.
14. O uso planejado de `sessionStorage` no Angular é simples, mas exige prevenção rigorosa de XSS.
15. Alguns endpoints antigos retornam entidades JPA e usam tratamento de erro não padronizado.

---

## 14. Estado da revisão

- **Última revisão:** 1º de setembro de 2026
- **Versão do projeto:** `0.0.1-SNAPSHOT`
- **Status:** em desenvolvimento
- **Fonte:** implementação atual do repositório
