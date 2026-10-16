# Guia de implementação do pagamento de matrícula no frontend

> Atualização de 16/10/2026: o contrato atual de parcelas, saldos, idempotência, competência e recorrência mensal está em **GUIA_CONTAS_RECEBER_FRONTEND.md**. As regras de pagamento exclusivamente integral e intervalos fixos de 30 dias descritas abaixo são a versão anterior.

Este guia descreve as mudanças necessárias no frontend Angular 21 standalone. O backend já gera cobranças mensais em `conta_receber`, permite que o ALUNO consulte somente as próprias cobranças e registra o pagamento em `recebimento`.

## 1. Fluxo implementado no backend

1. O ADMIN cadastra o aluno informando `mensalidade`.
2. O backend cria a primeira cobrança com status `PENDENTE` e vencimento em 30 dias.
3. Diariamente às 02:00, a rotina financeira verifica a última cobrança de cada aluno.
4. Quando a data de vencimento chega, cria uma nova cobrança com vencimento 30 dias depois.
5. O ALUNO autenticado consulta `GET /contareceber/minhas`.
6. Ao pagar, escolhe somente a forma de pagamento. O valor vem da cobrança e não pode ser alterado pelo navegador.
7. O backend cria `Recebimento`, marca a cobrança como `PAGO` e altera `statusMatricula` para `ATIV0`.
8. O aluno pode consultar o recibo da cobrança paga.

> O enum existente usa `ATIV0` e `INATIV0` com o algarismo zero. Use exatamente esses valores até que o enum do projeto seja corrigido em uma migração futura.

## 2. Atualize o contrato de aluno

Inclua `mensalidade` no model usado pelos formulários de criação e edição:

```ts
export interface AlunoRequest {
  nome: string;
  cpfCnpj: string | null;
  telefone: string | null;
  matricula: string;
  mensalidade: number;
  faculdadeId: number | null;
}
```

No formulário administrativo, torne a mensalidade obrigatória, maior que zero e com duas casas decimais:

```ts
readonly form = this.fb.nonNullable.group({
  nome: ['', Validators.required],
  cpfCnpj: [''],
  telefone: [''],
  matricula: ['', Validators.required],
  mensalidade: [0, [Validators.required, Validators.min(0.01)]],
  faculdadeId: this.fb.control<number | null>(null),
});
```

Alunos antigos que ainda possuem `mensalidade = null` devem ser editados pelo ADMIN. Ao salvar a primeira mensalidade válida, o backend cria a primeira cobrança se ela ainda não existir.

## 3. Contratos TypeScript financeiros

Crie `src/app/core/financeiro/matricula.models.ts`:

```ts
export type StatusFinanceiro = 'PENDENTE' | 'PAGO' | 'ATRASADO';
export type FormaPagamento =
  | 'PIX'
  | 'DINHEIRO'
  | 'CARTAO'
  | 'BOLETO'
  | 'TRANSFERENCIA';

export interface ContaReceber {
  id: number;
  descricao: string;
  valor: number;
  dataVencimento: string;
  status: StatusFinanceiro;
  alunoId: number;
  nomeAluno: string;
}

export interface ReciboMatricula {
  id: number;
  contaId: number;
  valor: number;
  dataRecebimento: string;
  formaPagamento: FormaPagamento;
  nomeAluno: string;
  descricaoConta: string;
}
```

Use o `ApiProblem` já criado no guia JWT para tratar os erros do backend.

## 4. Service da matrícula

Crie `src/app/services/matricula.service.ts`:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ContaReceber,
  FormaPagamento,
  ReciboMatricula,
} from '../core/financeiro/matricula.models';

@Injectable({ providedIn: 'root' })
export class MatriculaService {
  private readonly http = inject(HttpClient);

  listarMinhas(): Observable<ContaReceber[]> {
    return this.http.get<ContaReceber[]>(
      `${environment.apiUrl}/contareceber/minhas`,
    );
  }

  pagar(contaId: number, formaPagamento: FormaPagamento): Observable<ReciboMatricula> {
    const params = new HttpParams().set('formaPagamento', formaPagamento);
    return this.http.post<ReciboMatricula>(
      `${environment.apiUrl}/recebimentos/realizar/${contaId}`,
      null,
      { params },
    );
  }

  buscarRecibo(contaId: number): Observable<ReciboMatricula> {
    return this.http.get<ReciboMatricula>(
      `${environment.apiUrl}/recebimentos/recibo/conta/${contaId}`,
    );
  }
}
```

O interceptor JWT já existente acrescentará `Authorization: Bearer <token>`. Não envie `alunoId`, `pessoaId` ou valor: o backend obtém o aluno do JWT e o valor da própria cobrança.

## 5. Tela “Minhas matrículas”

Crie um componente standalone, por exemplo:

```text
src/app/components/matricula/minhas-matriculas/
  minhas-matriculas.ts
  minhas-matriculas.html
  minhas-matriculas.css
```

Estrutura sugerida do componente:

```ts
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MatriculaService } from '../../../services/matricula.service';
import {
  ContaReceber,
  FormaPagamento,
  ReciboMatricula,
} from '../../../core/financeiro/matricula.models';

@Component({
  selector: 'app-minhas-matriculas',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ButtonModule, DialogModule, SelectModule, TableModule, TagModule],
  templateUrl: './minhas-matriculas.html',
  styleUrl: './minhas-matriculas.css',
})
export class MinhasMatriculas {
  private readonly matriculaService = inject(MatriculaService);

  readonly contas = signal<ContaReceber[]>([]);
  readonly carregando = signal(false);
  readonly pagando = signal(false);
  readonly contaSelecionada = signal<ContaReceber | null>(null);
  readonly recibo = signal<ReciboMatricula | null>(null);
  readonly erro = signal<string | null>(null);
  readonly formas = ['PIX', 'DINHEIRO', 'CARTAO', 'BOLETO', 'TRANSFERENCIA'] as const;
  formaPagamento: FormaPagamento = 'PIX';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.matriculaService.listarMinhas()
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: (contas) => this.contas.set(contas),
        error: (error) => this.erro.set(error.error?.detail ?? 'Não foi possível carregar as matrículas.'),
      });
  }

  abrirPagamento(conta: ContaReceber): void {
    if (conta.status === 'PAGO') return;
    this.contaSelecionada.set(conta);
    this.formaPagamento = 'PIX';
    this.erro.set(null);
  }

  confirmarPagamento(): void {
    const conta = this.contaSelecionada();
    if (!conta || this.pagando()) return;

    this.pagando.set(true);
    this.matriculaService.pagar(conta.id, this.formaPagamento)
      .pipe(finalize(() => this.pagando.set(false)))
      .subscribe({
        next: (recibo) => {
          this.contaSelecionada.set(null);
          this.recibo.set(recibo);
          this.carregar();
        },
        error: (error) => this.erro.set(error.error?.detail ?? 'Não foi possível registrar o pagamento.'),
      });
  }

  visualizarRecibo(conta: ContaReceber): void {
    this.matriculaService.buscarRecibo(conta.id).subscribe({
      next: (recibo) => this.recibo.set(recibo),
      error: (error) => this.erro.set(error.error?.detail ?? 'Recibo não encontrado.'),
    });
  }
}
```

Na tabela, mostre:

- descrição;
- valor formatado com `currency:'BRL'`;
- vencimento com `date:'dd/MM/yyyy'`;
- status com `p-tag`;
- botão **Pagar** somente para `PENDENTE` ou `ATRASADO`;
- botão **Ver recibo** somente para `PAGO`.

Antes do POST, abra um diálogo de confirmação contendo descrição, vencimento, valor e forma de pagamento. Desabilite o botão enquanto `pagando()` for verdadeiro para evitar clique duplo. O backend também usa bloqueio transacional e impede o segundo pagamento com `409`.

## 6. Rota e menu

Adicione uma rota exclusiva de ALUNO:

```ts
{
  path: 'minhas-matriculas',
  loadComponent: () =>
    import('./components/matricula/minhas-matriculas/minhas-matriculas')
      .then((m) => m.MinhasMatriculas),
  canActivate: [roleGuard(['ALUNO'])],
}
```

Adicione **Minhas matrículas** ao menu do aluno. Não mostre essa tela para ADMIN ou MOTORISTA. O guard controla a navegação, mas a autorização definitiva continua no backend.

## 7. Contratos HTTP

Listar cobranças do aluno autenticado:

```http
GET /contareceber/minhas
Authorization: Bearer <token-aluno>
```

Registrar o pagamento integral:

```http
POST /recebimentos/realizar/15?formaPagamento=PIX
Authorization: Bearer <token-aluno>
```

Consultar recibo:

```http
GET /recebimentos/recibo/conta/15
Authorization: Bearer <token-aluno>
```

Formas aceitas: `PIX`, `DINHEIRO`, `CARTAO`, `BOLETO` e `TRANSFERENCIA`.

## 8. Tratamento de erros

- `400`: forma de pagamento inválida ou dados do aluno inválidos;
- `401`: token ausente, expirado ou revogado; o interceptor deve encerrar a sessão;
- `403`: papel diferente de ALUNO;
- `404`: cobrança ou recibo inexistente, inclusive tentativa de acessar cobrança de outro aluno;
- `409`: cobrança já paga;
- status `0`: backend indisponível.

Em `409`, feche o diálogo de pagamento e recarregue a tabela, pois outro clique ou requisição pode já ter concluído o pagamento.

## 9. Checklist manual

- [ ] O cadastro e a edição de aluno enviam `mensalidade` maior que zero.
- [ ] Um aluno novo recebe uma cobrança com vencimento em 30 dias.
- [ ] Aluno antigo sem cobrança recebe a primeira após salvar uma mensalidade válida.
- [ ] O menu “Minhas matrículas” aparece somente para ALUNO.
- [ ] A tabela mostra apenas cobranças ligadas ao `pessoaId` do token.
- [ ] O frontend não envia `alunoId`, `pessoaId` nem valor do pagamento.
- [ ] O botão Pagar não aparece em cobrança `PAGO`.
- [ ] O diálogo confirma valor e forma de pagamento.
- [ ] Pagamento bem-sucedido atualiza a tabela e abre o recibo.
- [ ] Um segundo pagamento da mesma cobrança recebe `409`.
- [ ] Um aluno não consegue acessar a cobrança ou recibo de outro aluno.
- [ ] Após o pagamento, `/auth/me` informa `statusMatricula: "ATIV0"`.
- [ ] Na data de vencimento, a rotina cria somente uma nova cobrança para mais 30 dias.

## 10. Observação sobre pagamento real

Esta implementação registra o pagamento no sistema acadêmico, assim como o fluxo atual de salário registra uma saída financeira. Ela não conversa com banco, operadora de cartão ou provedor PIX. Para cobrança financeira real seria necessário integrar um gateway, criar uma intenção de pagamento e confirmar o resultado por webhook antes de marcar a conta como `PAGO`.
