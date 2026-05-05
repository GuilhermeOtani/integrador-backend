# 📐 Diagrama de Classes - Integrador 2026

## Estrutura das Classes e Relacionamentos

**Última Atualização**: Maio 2026  
**Status**: Totalmente sincronizado com o código-fonte

---

## Hierarquia de Classes

```
Pessoa (Abstract)
├── Aluno
└── Motorista

Usuario (separada, implementa UserDetails)
```

---

## Entidades Principais

### **Pessoa** (Classe Base - Abstract)
```
- id: Long (PK)
- nome: String
- cpfCnpj: String  [⚠️ MUDANÇA: era email]
- telefone: String
- tipoPessoa: TipoPessoa (Enum)
```
**Tipos**: ALUNO, MOTORISTA, ADMINISTRADOR, GERENTE_FINANCEIRO, OPERADOR

**Relacionamentos**:
- `1:N` com ContaPagar (como motorista/responsável)
- `1:N` com ChamadoManutencao (como responsável)

---

### **Aluno** (extends Pessoa)
```
- matricula: String
- statusMatricula: StatusMatricula (Enum)
- dataCadastro: LocalDate
- faculdade: Faculdade (FK)
- rota: Rota (FK)
- viagem: Viagem (FK) [⚠️ NOVO]
```
**Relacionamentos**:
- `N:1` com Faculdade
- `N:1` com Rota
- `N:1` com Viagem [⚠️ NOVO]
- `1:N` com ContaReceber

---

### **Motorista** (extends Pessoa)
```
- cnh: String
- salario: BigDecimal
- onibus: Onibus (FK)
```
**Relacionamentos**:
- `N:1` com Onibus
- `1:N` com Viagem

---

### **Usuario**
```
- id: Long (PK)
- email: String
- senha: String
- tipoPessoa: TipoPessoa (Enum)
```
**Implementa**: UserDetails (Spring Security)

---

### **Faculdade**
```
- id: Long (PK)
- nome: String
- endereco: String
[⚠️ REMOVIDO: telefone]
```
**Relacionamentos**:
- `1:N` com Aluno
- `1:N` com Rota

---

### **Onibus**
```
- id: Long (PK)
- numeroIdentificacao: String (Unique)
- placa: String
- modelo: String
- statusOnibus: StatusOnibus (Enum)
- fotoUrl: String (LONGTEXT)
- capacidade: Double
```
**Relacionamentos**:
- `1:N` com Motorista
- `1:N` com Viagem
- `1:N` com ChamadoManutencao

---

### **Rota**
```
- id: Long (PK)
- nome: String
- descricao: String
- faculdade: Faculdade (FK)
- pontosEmbarque: List<PontoEmbarque> (N:M)
```
**Relacionamentos**:
- `N:1` com Faculdade
- `N:M` com PontoEmbarque
- `1:N` com Aluno
- `1:N` com Viagem

---

### **PontoEmbarque**
```
- id: Long (PK)
- ordemParada: Integer  [⚠️ ÚNICO CAMPO AGORA]
[⚠️ REMOVIDOS: nome, endereco, coordenadas]
```
**Mudança Estrutural**: Simplificado para apenas ordem de parada

**Relacionamentos**:
- `N:M` com Rota

---

### **Viagem**
```
- id: Long (PK)
- data: LocalDate
- onibus: Onibus (FK)
- rota: Rota (FK)
- motorista: Motorista (FK)
- gradeDiaria: GradeDiaria (FK)
- alunos: List<Aluno> [⚠️ NOVO]
```
**Relacionamentos**:
- `N:1` com Onibus
- `N:1` com Rota
- `N:1` com Motorista
- `N:1` com GradeDiaria
- `1:N` com Aluno [⚠️ NOVO]

---

### **GradeDiaria**
```
- id: Long (PK)
- data: LocalDate
- diaSemana: DiaSemana (Enum)
- descricao: String
- viagens: List<Viagem> (1:N)
```
**Relacionamentos**:
- `1:N` com Viagem

---

### **ContaPagar**
```
- id: Long (PK)
- descricao: String
- valor: BigDecimal
- dataVencimento: LocalDate
- status: StatusFinanceiro (Enum)
- motorista: Pessoa (FK)  [⚠️ MUDANÇA: agora é Pessoa genérica]
```
**Relacionamentos**:
- `N:1` com Pessoa
- `1:N` com Pagamento

---

### **ContaReceber**
```
- id: Long (PK)
- valor: Double
- descricao: String  [⚠️ NOVO]
- dataVencimento: LocalDate
- status: StatusFinanceiro (Enum)
- aluno: Aluno (FK)
```
**Relacionamentos**:
- `N:1` com Aluno
- `1:N` com Recebimento

---

### **Pagamento**
```
- id: Long (PK)
- valor: BigDecimal
- dataPagamento: LocalDate
- formaPagamento: FormaPagamento (Enum)
- contaPagar: ContaPagar (FK)
[⚠️ REMOVIDO: contaReceber]
```
**Mudança**: Removido relacionamento com ContaReceber. Recebimentos usam Recebimento.

**Relacionamentos**:
- `N:1` com ContaPagar

---

### **Recebimento**
```
- id: Long (PK)
- valor: Double
- dataRecebimento: LocalDate
- formaPagamento: FormaPagamento (Enum)
- contaReceber: ContaReceber (FK)
```
**Propósito**: Entidade separada para registrar recebimentos

**Relacionamentos**:
- `N:1` com ContaReceber

---

### **ChamadoManutencao**
```
- id: Long (PK)
- descricao: String
- dataAbertura: LocalDate  [⚠️ MUDANÇA: era dataCriacao]
- urgencia: NivelUrgencia (Enum)  [⚠️ MUDANÇA: era nivelUrgencia]
- status: StatusChamado (Enum)
- onibus: Onibus (FK)
- responsavel: Pessoa (FK)  [⚠️ NOVO]
[⚠️ REMOVIDO: dataResolucao]
```
**Relacionamentos**:
- `N:1` com Onibus
- `N:1` com Pessoa

---

## 📊 Enumerações

### StatusMatricula
- ATIVA
- INATIVA
- PENDENTE

### DiaSemana
- SEGUNDA
- TERCA
- QUARTA
- QUINTA
- SEXTA

### StatusOnibus
- ATIVO
- INATIVO
- EM_MANUTENCAO

### StatusFinanceiro
- PENDENTE
- PAGO
- VENCIDO
- CANCELADO

### StatusChamado
- ABERTO
- EM_ANDAMENTO
- CONCLUIDO
- CANCELADO

### NivelUrgencia
- BAIXA
- MEDIA
- ALTA
- CRITICA

### FormaPagamento
- PIX
- BOLETO
- DINHEIRO
- CARTAO_CREDITO
- CARTAO_DEBITO
- TRANSFERENCIA

### TipoPessoa
- ALUNO
- MOTORISTA
- ADMINISTRADOR
- GERENTE_FINANCEIRO
- OPERADOR

---

## Tabela de Relacionamentos

| Classe A | Tipo | Classe B | Descrição | Status |
|----------|------|----------|-----------|--------|
| Aluno | N:1 | Faculdade | Um aluno pertence a uma faculdade | ✓ |
| Aluno | N:1 | Rota | Um aluno utiliza uma rota | ✓ |
| Aluno | N:1 | Viagem | Um aluno é vinculado a uma viagem | ⚠️ NOVO |
| Aluno | 1:N | ContaReceber | Um aluno tem contas a receber | ✓ |
| Motorista | N:1 | Onibus | Um motorista opera um ônibus | ✓ |
| Motorista | 1:N | Viagem | Um motorista realiza várias viagens | ✓ |
| Pessoa | 1:N | ContaPagar | Uma pessoa tem contas a pagar | ⚠️ MUDADO |
| Pessoa | 1:N | ChamadoManutencao | Uma pessoa é responsável | ⚠️ NOVO |
| Onibus | 1:N | Viagem | Um ônibus realiza várias viagens | ✓ |
| Onibus | 1:N | ChamadoManutencao | Um ônibus tem chamados | ✓ |
| Rota | N:1 | Faculdade | Uma rota pertence a uma faculdade | ✓ |
| Rota | N:M | PontoEmbarque | Uma rota tem vários pontos (com ordem) | ✓ |
| Rota | 1:N | Aluno | Uma rota tem vários alunos | ✓ |
| Rota | 1:N | Viagem | Uma rota pode ter várias viagens | ✓ |
| Viagem | 1:N | Aluno | Uma viagem tem vários alunos | ⚠️ NOVO |
| GradeDiaria | 1:N | Viagem | Uma grade tem várias viagens | ✓ |
| ContaPagar | 1:N | Pagamento | Uma conta pode ter vários pagamentos | ✓ |
| ContaReceber | 1:N | Recebimento | Uma conta pode ter vários recebimentos | ✓ |

---

## ⚠️ Resumo de Mudanças - Maio 2026

### Mudanças Principais

1. **Pessoa**: `email` → `cpfCnpj`
2. **PontoEmbarque**: Removidos `nome`, `endereco`, `coordenadas` - Só mantém `ordemParada`
3. **ChamadoManutencao**: Removido `dataResolucao`, renomeado `dataCriacao` → `dataAbertura`, `nivelUrgencia` → `urgencia`, adicionado `responsavel`
4. **Faculdade**: Removido `telefone`
5. **ContaReceber**: Adicionado `descricao`
6. **Viagem**: Adicionado OneToMany `alunos`
7. **Aluno**: Adicionado N:1 `viagem`
8. **ContaPagar**: `motorista` agora é `Pessoa` genérica
9. **Pagamento**: Removido relacionamento com `ContaReceber`
10. **Usuario**: Entidade separada para autenticação

---

**Versão**: 1.0  
**Data**: Maio 2026  
**Status**: Totalmente Sincronizado com Código-Fonte
