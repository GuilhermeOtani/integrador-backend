# Guia de implementação: chamados de manutenção de ônibus

Backend implementado em 8 de outubro de 2026. Este guia descreve a próxima entrega do frontend; nenhum arquivo Angular foi alterado nesta implementação.

## 1. O que aproveitar do frontend atual

O projeto Angular 21 standalone com PrimeNG 21 já possui `AuthService`, `roleGuard`, interceptor JWT, `environment.apiUrl` e os helpers `mensagemApi`/`problemaApi`. Reutilize-os: não crie outro login, interceptor ou armazenamento de token.

Raiz atual do frontend: `C:/Users/melhe/OneDrive/Área de Trabalho/integrador-frontend 2026/integrador-frontend`.

Organização sugerida, dentro dessa raiz:

```text
src/app/core/manutencao/
  manutencao.models.ts
  manutencao.service.ts
src/app/components/manutencao/
  meus-chamados.ts
  meus-chamados.html
  manutencao-admin.ts
  manutencao-admin.html
```

Não abra o cadastro `/onibus` para ALUNO ou MOTORISTA: essa tela continua administrativa. A seleção do ônibus usa um endpoint próprio com dados mínimos.

## 2. Regras que a interface deve refletir

| Papel | Pode fazer |
|---|---|
| ALUNO/MOTORISTA | Selecionar qualquer ônibus, relatar um problema, consultar os próprios chamados e acompanhar a resposta |
| ADMIN | Consultar todos, filtrar, definir prioridade, atender, resolver, reabrir e opcionalmente alterar o estado do ônibus |

O autor é identificado no servidor pelo `sub` do JWT, que identifica `Usuario`, e pela `Pessoa` vinculada. **Não envie nome, pessoaId, usuarioId, solicitante ou responsável na criação.** Mesmo que alguém modifique o formulário, não consegue escolher outro autor.

O relato nasce `ABERTO` com prioridade `MEDIA`. ADMIN pode mudar a prioridade para `BAIXA`, `MEDIA` ou `ALTA`. Não exiba seletor de prioridade ou status no formulário de abertura.

Estado do chamado e estado do ônibus são coisas diferentes. Resolver um relato sobre o ar-condicionado não libera o veículo automaticamente. O ADMIN precisa escolher explicitamente alterar o ônibus quando isso for necessário.

Não há edição, cancelamento ou exclusão pelo solicitante; não há exclusão pelo ADMIN nesta versão. Um relato improcedente/duplicado é resolvido com explicação. A observação é a resposta atual do ADMIN, não um chat nem um histórico de mensagens.

## 3. Contratos TypeScript

Crie `manutencao.models.ts`:

```ts
export type PrioridadeChamado = 'BAIXA' | 'MEDIA' | 'ALTA';
export type StatusChamado = 'ABERTO' | 'EM_ANDAMENTO' | 'RESOLVIDO';
export type StatusOnibus = 'OPERANDO' | 'MANUTENCAO' | 'QUEBRADO' | 'INATIVO';

export interface OnibusChamado {
  id: number;
  numeroIdentificacao: string | null;
  placa: string | null;
  modelo: string | null;
  statusOnibus: StatusOnibus | null;
}

export interface ChamadoManutencao {
  id: number;
  descricao: string;
  dataAbertura: string; // ISO yyyy-MM-dd, não é timestamp
  urgencia: PrioridadeChamado;
  status: StatusChamado;
  observacaoAdmin: string | null;
  onibus: OnibusChamado;
  solicitante: {
    pessoaId: number;
    nome: string;
    tipoPessoa: 'ALUNO' | 'MOTORISTA' | 'ADMIN';
  };
}

export interface AbrirChamado {
  onibusId: number;
  descricao: string;
}

export interface AtenderChamado {
  urgencia: PrioridadeChamado;
  status: StatusChamado;
  observacaoAdmin: string | null;
  statusOnibus?: StatusOnibus;
}

export interface FiltrosChamado {
  status?: StatusChamado;
  urgencia?: PrioridadeChamado;
  onibusId?: number;
}

export const PRIORIDADES = [
  { label: 'Baixa', value: 'BAIXA' },
  { label: 'Média', value: 'MEDIA' },
  { label: 'Alta', value: 'ALTA' },
];
export const ESTADOS_CHAMADO = [
  { label: 'Aberto', value: 'ABERTO' },
  { label: 'Em andamento', value: 'EM_ANDAMENTO' },
  { label: 'Resolvido', value: 'RESOLVIDO' },
];
export const ESTADOS_ONIBUS = [
  { label: 'Operando', value: 'OPERANDO' },
  { label: 'Em manutenção', value: 'MANUTENCAO' },
  { label: 'Quebrado', value: 'QUEBRADO' },
  { label: 'Inativo', value: 'INATIVO' },
];
```

Os valores enviados à API são sempre maiúsculos e sem acento. Os rótulos visuais podem usar acentos. `statusOnibus` pode ser `null` em um cadastro antigo; exiba “Não informado” sem inventar um estado.

## 4. Serviço HTTP

Crie `manutencao.service.ts`:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  AbrirChamado, AtenderChamado, ChamadoManutencao,
  FiltrosChamado, OnibusChamado,
} from './manutencao.models';

@Injectable({ providedIn: 'root' })
export class ManutencaoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl.replace(/\/$/, '')}/chamados-manutencao`;

  listarOnibus() { return this.http.get<OnibusChamado[]>(`${this.url}/onibus`); }
  minhas() { return this.http.get<ChamadoManutencao[]>(`${this.url}/minhas`); }
  buscar(id: number) { return this.http.get<ChamadoManutencao>(`${this.url}/${id}`); }
  abrir(body: AbrirChamado) { return this.http.post<ChamadoManutencao>(this.url, body); }

  listar(filtros: FiltrosChamado = {}) {
    let params = new HttpParams();
    if (filtros.status) params = params.set('status', filtros.status);
    if (filtros.urgencia) params = params.set('urgencia', filtros.urgencia);
    if (filtros.onibusId !== undefined) params = params.set('onibusId', filtros.onibusId);
    return this.http.get<ChamadoManutencao[]>(this.url, { params });
  }

  atender(id: number, body: AtenderChamado) {
    return this.http.put<ChamadoManutencao>(`${this.url}/${id}/atendimento`, body);
  }
}
```

Não monte `Authorization` neste serviço: o interceptor existente já adiciona Bearer às chamadas do backend. Não passe a identificação do aluno/motorista na URL nem em query parameters.

As listas são arrays, ordenados do chamado mais recente para o mais antigo. A paginação do `p-table` neste exemplo é local; não envie `page`/`size`, pois não existe paginação no backend nesta versão.

## 5. Rotas e menu

Em `app.routes.ts`, antes do redirecionamento curinga, acrescente:

```ts
{
  path: 'meus-chamados',
  loadComponent: () => import('./components/manutencao/meus-chamados')
    .then(m => m.MeusChamados),
  canActivate: [roleGuard(['ALUNO', 'MOTORISTA'])],
},
{
  path: 'manutencao',
  loadComponent: () => import('./components/manutencao/manutencao-admin')
    .then(m => m.ManutencaoAdmin),
  canActivate: [roleGuard(['ADMIN'])],
},
```

No `Sidebar` existente, inclua no ramo ALUNO e no ramo MOTORISTA:

```ts
{ label: 'Meus chamados', icon: 'pi pi-wrench', rota: '/meus-chamados' }
```

No ramo ADMIN, inclua em Logística:

```ts
{ label: 'Manutenção', icon: 'pi pi-wrench', rota: '/manutencao' }
```

Um atalho “Ver chamados” na tabela administrativa de ônibus é opcional. Se adotado, navegue para `/manutencao?onibusId=<id>` e leia o parâmetro para preencher o filtro; ele não substitui a página principal. Não adicione esse atalho para solicitantes ao cadastro administrativo de ônibus.

## 6. Tela “Meus chamados”: componente completo de referência

Crie `meus-chamados.ts`:

```ts
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { ManutencaoService } from '../../core/manutencao/manutencao.service';
import { ChamadoManutencao, OnibusChamado } from '../../core/manutencao/manutencao.models';
import { mensagemApi } from '../../core/auth/api-error';

@Component({
  standalone: true,
  selector: 'app-meus-chamados',
  imports: [DatePipe, ReactiveFormsModule, ButtonModule, DialogModule,
    SelectModule, TableModule, TextareaModule, ToastModule],
  providers: [MessageService],
  templateUrl: './meus-chamados.html',
})
export class MeusChamados implements OnInit {
  private readonly api = inject(ManutencaoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly messages = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  readonly chamados = signal<ChamadoManutencao[]>([]);
  readonly onibus = signal<OnibusChamado[]>([]);
  readonly carregando = signal(false);
  readonly carregandoOnibus = signal(false);
  readonly salvando = signal(false);
  readonly carregandoDetalhe = signal(false);
  readonly detalhe = signal<ChamadoManutencao | null>(null);
  novoAberto = false;
  detalhesAberto = false;

  readonly opcoesOnibus = computed(() => this.onibus().map(o => ({
    id: o.id,
    label: `${o.numeroIdentificacao ?? 'Sem número'} — ${o.placa ?? 'Sem placa'} — ${o.modelo ?? 'Sem modelo'}`,
  })));
  readonly form = this.fb.group({
    onibusId: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    descricao: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(2000)]),
  });

  ngOnInit() { this.carregar(); this.carregarOnibus(); }

  carregar() {
    if (this.carregando()) return;
    this.carregando.set(true);
    this.api.minhas().pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregando.set(false))).subscribe({
      next: lista => this.chamados.set(lista), error: e => this.erro(e),
    });
  }

  carregarOnibus() {
    this.carregandoOnibus.set(true);
    this.api.listarOnibus().pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregandoOnibus.set(false))).subscribe({
      next: lista => this.onibus.set(lista), error: e => this.erro(e),
    });
  }

  abrirFormulario() {
    this.form.reset({ onibusId: null, descricao: '' });
    this.novoAberto = true;
  }

  enviar() {
    if (this.salvando()) return;
    this.form.markAllAsTouched();
    const { onibusId, descricao } = this.form.getRawValue();
    if (this.form.invalid || onibusId === null || !descricao.trim()) return;
    this.salvando.set(true);
    this.api.abrir({ onibusId, descricao: descricao.trim() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.salvando.set(false)))
      .subscribe({
        next: () => {
          this.novoAberto = false;
          this.messages.add({ severity: 'success', summary: 'Chamado enviado' });
          this.carregar();
        },
        error: e => this.erro(e),
      });
  }

  ver(id: number) {
    if (this.carregandoDetalhe()) return;
    this.detalhe.set(null);
    this.detalhesAberto = true;
    this.carregandoDetalhe.set(true);
    this.api.buscar(id).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregandoDetalhe.set(false))).subscribe({
      next: chamado => this.detalhe.set(chamado),
      error: e => { this.detalhesAberto = false; this.erro(e); },
    });
  }

  private erro(error: HttpErrorResponse) {
    if (error.status !== 401) { // O interceptor já trata logout e redirecionamento.
      this.messages.add({ severity: 'error', summary: 'Não foi possível concluir', detail: mensagemApi(error) });
    }
  }
}
```

Crie `meus-chamados.html`:

```html
<p-toast />
<h1>Meus chamados</h1>
<p>Relate problemas e acompanhe a resposta da administração.</p>
<p-button label="Abrir chamado" icon="pi pi-plus" (onClick)="abrirFormulario()" />
<p-button label="Atualizar" icon="pi pi-refresh" [disabled]="carregando()" (onClick)="carregar()" />

<p-table [value]="chamados()" [loading]="carregando()" [paginator]="true" [rows]="10" dataKey="id">
  <ng-template #header><tr><th>Nº</th><th>Ônibus</th><th>Abertura</th><th>Prioridade</th><th>Andamento</th><th>Ações</th></tr></ng-template>
  <ng-template #body let-c>
    <tr>
      <td>{{ c.id }}</td><td>{{ c.onibus.numeroIdentificacao }} — {{ c.onibus.placa }}</td>
      <td>{{ c.dataAbertura | date:'dd/MM/yyyy':'UTC' }}</td>
      <td>{{ c.urgencia }}</td><td>{{ c.status }}</td>
      <td><p-button label="Ver detalhes" [disabled]="carregandoDetalhe()" (onClick)="ver(c.id)" /></td>
    </tr>
  </ng-template>
  <ng-template #emptymessage><tr><td colspan="6">Você ainda não abriu chamados.</td></tr></ng-template>
</p-table>

<p-dialog header="Abrir chamado" [(visible)]="novoAberto" [modal]="true"
  [closable]="!salvando()" [closeOnEscape]="!salvando()" [style]="{width:'min(95vw, 600px)'}">
  <form [formGroup]="form" (ngSubmit)="enviar()">
    <label for="onibus">Ônibus</label>
    <p-select inputId="onibus" formControlName="onibusId" [options]="opcoesOnibus()"
      optionLabel="label" optionValue="id" [filter]="true" filterBy="label"
      [loading]="carregandoOnibus()" placeholder="Selecione número, placa ou modelo" appendTo="body" />
    @if (form.controls.onibusId.touched && form.controls.onibusId.invalid) {
      <small>Selecione um ônibus.</small>
    }
    @if (!carregandoOnibus() && !onibus().length) {
      <p>Nenhum ônibus disponível para seleção. Atualize ou procure a administração.</p>
      <p-button type="button" label="Recarregar ônibus" (onClick)="carregarOnibus()" />
    }
    <label for="descricao">Descreva o problema</label>
    <textarea pTextarea id="descricao" formControlName="descricao" rows="5" maxlength="2000"></textarea>
    <small>{{ form.controls.descricao.value.length }}/2000</small>
    @if (form.controls.descricao.touched && !form.controls.descricao.value.trim()) {
      <small>Informe o problema encontrado.</small>
    }
    <p>Seu nome será identificado automaticamente pelo login.</p>
    <p-button type="submit" label="Enviar chamado" [loading]="salvando()"
      [disabled]="form.invalid || !form.controls.descricao.value.trim() || salvando() || carregandoOnibus()" />
  </form>
</p-dialog>

<p-dialog header="Detalhes do chamado" [(visible)]="detalhesAberto" [modal]="true"
  [style]="{width:'min(95vw, 600px)'}">
  @if (carregandoDetalhe()) { <p>Carregando...</p> }
  @if (detalhe(); as c) {
    <p>Chamado #{{ c.id }} — {{ c.status }} — Prioridade {{ c.urgencia }}</p>
    <p>Ônibus {{ c.onibus.numeroIdentificacao }} — {{ c.onibus.placa }}</p>
    <p>Estado atual do ônibus: {{ c.onibus.statusOnibus ?? 'Não informado' }}</p>
    <h2>Seu relato</h2><p style="white-space: pre-wrap">{{ c.descricao }}</p>
    <h2>Resposta da administração</h2>
    <p style="white-space: pre-wrap">{{ c.observacaoAdmin || 'Aguardando resposta.' }}</p>
  }
</p-dialog>
```

Nunca use `[innerHTML]` para renderizar descrição ou observação. Use interpolação Angular para tratar o conteúdo como texto. O sufixo `UTC` na formatação evita deslocar um dia em datas sem horário.

## 7. Tela administrativa “Manutenção”

Use uma tabela com: ID, ônibus, nome/papel do solicitante, data, prioridade, andamento e botão “Atender”. Acima, filtros independentes para status, urgência e ônibus, com “Aplicar”, “Limpar” e “Atualizar”. Os filtros devem chamar `listar`, não `minhas`.

O diálogo de atendimento mostra o relato original em modo leitura e um formulário com prioridade, andamento, observação, checkbox “Alterar também o estado do ônibus” e seletor de estado habilitado somente quando marcado. Não preencha esse checkbox automaticamente.

Crie `manutencao-admin.ts`:

```ts
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { ManutencaoService } from '../../core/manutencao/manutencao.service';
import {
  AtenderChamado, ChamadoManutencao, OnibusChamado, PrioridadeChamado,
  StatusChamado, StatusOnibus, PRIORIDADES, ESTADOS_CHAMADO, ESTADOS_ONIBUS,
} from '../../core/manutencao/manutencao.models';
import { mensagemApi } from '../../core/auth/api-error';

@Component({
  standalone: true,
  selector: 'app-manutencao-admin',
  imports: [DatePipe, ReactiveFormsModule, ButtonModule, CheckboxModule,
    DialogModule, SelectModule, TableModule, TextareaModule, ToastModule],
  providers: [MessageService],
  templateUrl: './manutencao-admin.html',
})
export class ManutencaoAdmin implements OnInit {
  private readonly api = inject(ManutencaoService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly messages = inject(MessageService);
  readonly prioridades = PRIORIDADES;
  readonly estados = ESTADOS_CHAMADO;
  readonly estadosOnibus = ESTADOS_ONIBUS;
  readonly chamados = signal<ChamadoManutencao[]>([]);
  readonly onibus = signal<OnibusChamado[]>([]);
  readonly carregando = signal(false);
  readonly abrindo = signal(false);
  readonly salvando = signal(false);
  readonly selecionado = signal<ChamadoManutencao | null>(null);
  dialogoAberto = false;
  readonly filtros = this.fb.group({
    status: this.fb.control<StatusChamado | null>(null),
    urgencia: this.fb.control<PrioridadeChamado | null>(null),
    onibusId: this.fb.control<number | null>(null),
  });
  readonly form = this.fb.group({
    urgencia: this.fb.nonNullable.control<PrioridadeChamado>('MEDIA', Validators.required),
    status: this.fb.nonNullable.control<StatusChamado>('ABERTO', Validators.required),
    observacaoAdmin: this.fb.nonNullable.control('', Validators.maxLength(2000)),
    alterarOnibus: this.fb.nonNullable.control(false),
    statusOnibus: this.fb.control<StatusOnibus | null>(null),
  });

  ngOnInit() { this.carregar(); this.carregarOnibus(); }

  carregarOnibus() {
    this.api.listarOnibus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: lista => this.onibus.set(lista), error: e => this.erro(e),
    });
  }

  carregar() {
    if (this.carregando()) return;
    const f = this.filtros.getRawValue();
    this.carregando.set(true);
    this.api.listar({ status: f.status ?? undefined, urgencia: f.urgencia ?? undefined,
      onibusId: f.onibusId ?? undefined }).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregando.set(false))).subscribe({
      next: lista => this.chamados.set(lista), error: e => this.erro(e),
    });
  }

  limpar() { this.filtros.reset(); this.carregar(); }

  atender(id: number) {
    if (this.abrindo() || this.salvando()) return;
    this.abrindo.set(true);
    this.api.buscar(id).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.abrindo.set(false))).subscribe({
      next: c => {
        this.selecionado.set(c);
        this.form.reset({ urgencia: c.urgencia, status: c.status,
          observacaoAdmin: c.observacaoAdmin ?? '', alterarOnibus: false,
          statusOnibus: c.onibus.statusOnibus });
        this.dialogoAberto = true;
      },
      error: e => this.erro(e),
    });
  }

  salvar() {
    if (this.salvando()) return;
    this.form.markAllAsTouched();
    const c = this.selecionado();
    const v = this.form.getRawValue();
    if (!c || this.form.invalid) return;
    if (v.status === 'RESOLVIDO' && !v.observacaoAdmin.trim()) {
      this.messages.add({ severity: 'warn', summary: 'Informe a justificativa de resolução' });
      return;
    }
    if (v.alterarOnibus && !v.statusOnibus) {
      this.messages.add({ severity: 'warn', summary: 'Selecione o estado do ônibus' });
      return;
    }
    const body: AtenderChamado = { urgencia: v.urgencia, status: v.status,
      observacaoAdmin: v.observacaoAdmin.trim() || null };
    if (v.alterarOnibus && v.statusOnibus) body.statusOnibus = v.statusOnibus;
    this.salvando.set(true);
    this.api.atender(c.id, body).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false))).subscribe({
      next: atualizado => {
        this.selecionado.set(atualizado);
        this.dialogoAberto = false;
        this.messages.add({ severity: 'success', summary: 'Atendimento salvo' });
        this.carregar();
        this.carregarOnibus();
      },
      error: e => this.erro(e),
    });
  }

  private erro(error: HttpErrorResponse) {
    if (error.status !== 401) this.messages.add({ severity: 'error',
      summary: 'Não foi possível concluir', detail: mensagemApi(error) });
  }
}
```

Crie `manutencao-admin.html`:

```html
<p-toast />
<h1>Manutenção</h1>
<form [formGroup]="filtros" (ngSubmit)="carregar()">
  <p-select ariaLabel="Filtrar andamento" formControlName="status" [options]="estados"
    optionLabel="label" optionValue="value" [showClear]="true" placeholder="Todos os andamentos" />
  <p-select ariaLabel="Filtrar prioridade" formControlName="urgencia" [options]="prioridades"
    optionLabel="label" optionValue="value" [showClear]="true" placeholder="Todas as prioridades" />
  <p-select ariaLabel="Filtrar ônibus" formControlName="onibusId" [options]="onibus()"
    optionLabel="placa" optionValue="id" [showClear]="true" [filter]="true" placeholder="Todos os ônibus" />
  <p-button type="submit" label="Aplicar / atualizar" [disabled]="carregando()" />
  <p-button type="button" label="Limpar" [disabled]="carregando()" (onClick)="limpar()" />
</form>
<p-table [value]="chamados()" [loading]="carregando()" [paginator]="true" [rows]="10" dataKey="id">
  <ng-template #header><tr><th>Nº</th><th>Ônibus</th><th>Solicitante</th><th>Abertura</th><th>Prioridade</th><th>Andamento</th><th>Ações</th></tr></ng-template>
  <ng-template #body let-c><tr>
    <td>{{ c.id }}</td><td>{{ c.onibus.numeroIdentificacao }} — {{ c.onibus.placa }}</td>
    <td>{{ c.solicitante.nome }} ({{ c.solicitante.tipoPessoa }})</td>
    <td>{{ c.dataAbertura | date:'dd/MM/yyyy':'UTC' }}</td>
    <td>{{ c.urgencia }}</td><td>{{ c.status }}</td>
    <td><p-button label="Atender" [disabled]="abrindo() || salvando()" (onClick)="atender(c.id)" /></td>
  </tr></ng-template>
  <ng-template #emptymessage><tr><td colspan="7">Nenhum chamado encontrado para estes filtros.</td></tr></ng-template>
</p-table>

<p-dialog header="Atendimento" [(visible)]="dialogoAberto" [modal]="true"
  [closable]="!salvando()" [closeOnEscape]="!salvando()" [style]="{width:'min(95vw, 700px)'}">
  @if (selecionado(); as c) {
    <p>Chamado #{{ c.id }} — {{ c.solicitante.nome }} ({{ c.solicitante.tipoPessoa }})</p>
    <p>Ônibus {{ c.onibus.numeroIdentificacao }} — {{ c.onibus.placa }}</p>
    <p>Estado atual: {{ c.onibus.statusOnibus ?? 'Não informado' }}</p>
    <h2>Relato original</h2><p style="white-space: pre-wrap">{{ c.descricao }}</p>
    <form [formGroup]="form" (ngSubmit)="salvar()">
      <label for="urgencia">Prioridade do conserto</label>
      <p-select inputId="urgencia" formControlName="urgencia" [options]="prioridades"
        optionLabel="label" optionValue="value" appendTo="body" />
      <label for="status">Andamento do chamado</label>
      <p-select inputId="status" formControlName="status" [options]="estados"
        optionLabel="label" optionValue="value" appendTo="body" />
      <label for="observacao">Resposta / justificativa</label>
      <textarea pTextarea id="observacao" formControlName="observacaoAdmin" rows="5" maxlength="2000"></textarea>
      @if (form.controls.status.value === 'RESOLVIDO') { <small>Justificativa obrigatória para resolver.</small> }
      <p-checkbox inputId="alterarOnibus" formControlName="alterarOnibus" [binary]="true" />
      <label for="alterarOnibus">Alterar também o estado do ônibus</label>
      @if (form.controls.alterarOnibus.value) {
        <label for="estadoOnibus">Novo estado do ônibus</label>
        <p-select inputId="estadoOnibus" formControlName="statusOnibus" [options]="estadosOnibus"
          optionLabel="label" optionValue="value" appendTo="body" />
        <p>A alteração afeta o cadastro e as próximas atribuições de viagens. Não cancela viagens existentes.</p>
      }
      @if (c.status === 'RESOLVIDO') {
        <p>Para reabrir, selecione ABERTO ou EM_ANDAMENTO e salve.</p>
      }
      <p-button type="submit" label="Salvar atendimento" [loading]="salvando()" [disabled]="form.invalid || salvando()" />
    </form>
  }
</p-dialog>
```

O envio substitui a observação atual: preencher `null` ou texto vazio limpa a observação, exceto quando o status enviado é `RESOLVIDO`, que exige texto. Preserve no formulário a resposta recebida antes de editar para não apagá-la inadvertidamente.

Não use o PUT genérico de ônibus para atender: ele poderia sobrescrever outros campos. O endpoint de atendimento altera exclusivamente o estado do ônibus quando informado.

Se existirem duas sessões administrativas editando simultaneamente, a última gravação prevalece; não há controle de versões do atendimento nesta entrega. Busque novamente os detalhes antes de abrir o diálogo. Se precisar confirmar uma liberação de veículo, confira também outros chamados abertos para ele.

## 8. Exemplos reais de requisição e resposta

Os placeholders abaixo devem ser substituídos pelo token obtido no login; não fixe tokens no código.

```http
POST /chamados-manutencao
Authorization: Bearer <token-aluno-ou-motorista>
Content-Type: application/json

{"onibusId":1,"descricao":"O ar-condicionado não funciona."}
```

Resposta `201 Created`, com cabeçalho `Location: /chamados-manutencao/10`:

```json
{
  "id": 10,
  "descricao": "O ar-condicionado não funciona.",
  "dataAbertura": "2026-10-08",
  "urgencia": "MEDIA",
  "status": "ABERTO",
  "observacaoAdmin": null,
  "onibus": {
    "id": 1,
    "numeroIdentificacao": "01",
    "placa": "ABC1234",
    "modelo": "Modelo",
    "statusOnibus": "OPERANDO"
  },
  "solicitante": {"pessoaId":2,"nome":"Aluno Exemplo","tipoPessoa":"ALUNO"}
}
```

```http
GET /chamados-manutencao/minhas
Authorization: Bearer <token-solicitante>

GET /chamados-manutencao?status=ABERTO&urgencia=MEDIA&onibusId=1
Authorization: Bearer <token-admin>

PUT /chamados-manutencao/10/atendimento
Authorization: Bearer <token-admin>
Content-Type: application/json

{"urgencia":"ALTA","status":"EM_ANDAMENTO","observacaoAdmin":"Encaminhado à oficina.","statusOnibus":"MANUTENCAO"}
```

O PUT retorna `200` com o chamado completo atualizado. Para resolver sem mudar o ônibus:

```json
{"urgencia":"ALTA","status":"RESOLVIDO","observacaoAdmin":"Ar-condicionado consertado."}
```

Para reabrir:

```json
{"urgencia":"ALTA","status":"ABERTO","observacaoAdmin":"Problema reapareceu; nova avaliação necessária."}
```

## 9. Viagens, exclusões e erros

Somente um ônibus `OPERANDO` pode ser atribuído a uma nova viagem ou substituir o ônibus de uma viagem, inclusive dentro de uma grade diária. Um cadastro antigo sem estado também não pode ser atribuído.

Nas telas de viagem/grade, filtre a seleção de novas atribuições para `OPERANDO`. Ao editar uma viagem cujo ônibus ficou indisponível, preserve sua opção atual e mostre um aviso: é permitido manter esse ônibus e editar os demais campos. **Preserve o ID das viagens existentes no DTO da grade**; sem ele o backend interpreta como nova viagem. Não aceite IDs de outra grade. Se o estado mudar entre carregar o formulário e salvar, o backend retornará `409`.

Nenhuma viagem é cancelada automaticamente ao abrir/atender um chamado. O ADMIN deve revisar suas viagens separadamente.

Pessoa ou ônibus com chamados não pode ser excluído fisicamente. Na tela de ônibus, explique o `409` e recomende alterar o estado para `INATIVO`, em vez de apagar. Excluir apenas `/usuarios/{id}` remove as credenciais, mas preserva a pessoa e o chamado. Nome e papel apresentados correspondem ao cadastro da pessoa; não existe cópia histórica do nome nesta versão.

| HTTP | Comportamento visual |
|---|---|
| `400` | Manter formulário aberto; mostrar `detail` e/ou `errors`; corrigir enum, limite de texto ou justificativa |
| `401` | Interceptor limpa sessão e leva ao login; não repetir automaticamente o envio |
| `403` | Informar falta de permissão; não tratar como sessão expirada |
| `404` | Recurso ausente ou chamado de outra pessoa; não tentar descobrir o autor |
| `409` | Mostrar conflito informado pelo servidor; preservar formulário e recarregar estado se necessário |
| `0` | Backend/rede indisponível; manter texto digitado e oferecer nova tentativa manual |

Não reenvie automaticamente POST após timeout: o primeiro envio pode ter sido gravado. Atualize “Meus chamados” antes de tentar novamente. O botão desabilitado evita cliques repetidos, mas não existe chave de idempotência no backend.

## 10. Checklist de implementação e validação

- [ ] Criar os modelos, serviço, componentes e templates; incluir rotas antes do curinga.
- [ ] Acrescentar menus nos ramos de papel corretos, mantendo `/onibus` exclusivo do ADMIN.
- [ ] Usar JWT/interceptor e `apiUrl` existentes; nenhum segredo ou token fixo no código.
- [ ] ALUNO e MOTORISTA abrem chamados escolhendo ônibus, sem digitar seu nome.
- [ ] Cada solicitante vê apenas seus chamados; detalhes de outro retornam `404`.
- [ ] ADMIN vê nomes, papéis, ônibus e filtros; solicitante não acessa atendimento.
- [ ] Resolver sem justificativa falha e não altera o ônibus.
- [ ] Prioridade e andamento são atualizados; resolver/reabrir não altera automaticamente o ônibus.
- [ ] Alteração explícita do ônibus é refletida no cadastro sem alterar foto, placa, modelo ou capacidade.
- [ ] Listas e opções de ônibus são recarregadas após salvar; botões indicam carregamento.
- [ ] Textos são interpolados, sem HTML não confiável; datas sem horário não mudam de dia.
- [ ] Viagens/grades tratam `409`, mantêm ônibus atual ao editar e preservam IDs das viagens existentes.
- [ ] Exclusão de acesso preserva o relato; exclusão física de pessoa/ônibus com chamado informa conflito.
- [ ] Backend indisponível e sessão expirada recebem tratamentos diferentes.
- [ ] Verificar layout mobile, labels, navegação por teclado e mensagens de erro.
- [ ] Executar `npm run build` e os testes do Angular depois de implementar as telas.

Testes automatizados sugeridos: serviço HTTP com `HttpTestingController` para URLs/corpos/filtros; formulários obrigatórios e limite de 2.000; ausência de campos de autoria; checkbox de alteração do ônibus; observação ao resolver; reabertura; guards por papel; estados de carregamento e mensagens de erro.

Guards e menus melhoram a experiência, mas a autorização verdadeira está no backend. Não há anexos, notificações, chat, cobrança ou oficina cadastrada nesta versão.

## 11. Preparação do backend

Reinicie o backend atualizado para o Flyway aplicar V5. Faça backup antes de migrar um banco importante. A migration não apaga dados nem inventa autoria: se houver chamado antigo incompleto, a inicialização falha com mensagem solicitando corrigir seus campos.

Os testes normais usam H2. Para validar migrations em PostgreSQL, existem testes opcionais `ManutencaoPostgresMigrationTests`: configure `MANUTENCAO_TEST_DB_URL`, `MANUTENCAO_TEST_DB_USER` e `MANUTENCAO_TEST_DB_PASSWORD` somente no processo de teste. Eles criam e removem apenas schemas temporários `test_manutencao_*`, sem migrar o schema público ou modificar o ADMIN real. Não versione credenciais.
