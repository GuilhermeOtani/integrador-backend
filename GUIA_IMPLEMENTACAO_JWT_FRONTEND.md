# Guia de implementação JWT no frontend Angular 21

Este guia foi escrito para o frontend standalone existente em `integrador-frontend 2026/integrador-frontend`, que usa Angular 21, PrimeNG 21 e `app.config.ts`. Ele não exige mudanças no backend além das já implementadas.

## 1. Entenda o fluxo antes de programar

1. O administrador cadastra primeiro um aluno ou motorista.
2. Em seguida, chama `POST /usuarios` informando a pessoa cadastrada, e-mail e senha.
3. O backend identifica a subclasse gravada em `pessoa.dtype` e define sozinho `ALUNO` ou `MOTORISTA`.
4. No login, o frontend envia e-mail e senha a `POST /auth/login`.
5. O backend compara a senha com o hash BCrypt e devolve um JWT assinado válido por duas horas.
6. O frontend guarda token, usuário e horário de expiração em `sessionStorage`.
7. O interceptor acrescenta `Authorization: Bearer <token>` às chamadas da API.
8. Guards e menus melhoram a navegação, mas o backend continua sendo a autoridade final.

O JWT é assinado, não criptografado. Não coloque senha nem dados sensíveis nele e não trate seu conteúdo como segredo.

## 2. Contratos TypeScript

Crie `src/app/core/auth/auth.models.ts`:

```ts
export type Papel = 'ADMIN' | 'ALUNO' | 'MOTORISTA';
export type StatusMatricula = 'ATIV0' | 'INATIV0' | 'PENDENTE';

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface UsuarioResumo {
  id: number;
  email: string;
  tipoPessoa: Papel;
  pessoaId: number;
  nome: string;
  ativo: boolean;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  usuario: UsuarioResumo;
}

export interface AlunoPerfil {
  matricula: string | null;
  statusMatricula: StatusMatricula | null;
  dataCadastro: string | null;
  mensalidade: number | null;
  faculdadeId: number | null;
}

export interface MotoristaPerfil {
  cnh: string | null;
  salario: number | null;
}

export interface PessoaPerfil {
  id: number;
  nome: string;
  cpfCnpj: string | null;
  telefone: string | null;
  aluno: AlunoPerfil | null;
  motorista: MotoristaPerfil | null;
}

export interface MeResponse {
  id: number;
  email: string;
  tipoPessoa: Papel;
  pessoa: PessoaPerfil;
}

export interface Sessao {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: number;
  usuario: UsuarioResumo;
}

export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
```

Não crie um campo de papel no formulário de conta de aluno/motorista. `POST /usuarios` aceita somente:

```ts
export interface CriarUsuarioRequest {
  pessoaId: number;
  email: string;
  senha: string;
}

export interface AtualizarEmailUsuarioRequest {
  email: string;
}

export interface RedefinirSenhaUsuarioRequest {
  senha: string;
}

export interface AtualizarStatusUsuarioRequest {
  ativo: boolean;
}

export interface PessoaDisponivel {
  id: number;
  nome: string;
  tipoPessoa: 'ALUNO' | 'MOTORISTA';
}
```

`tokenVersion`, hash da senha, papel e pessoa vinculada são internos do backend. Não os inclua nos contratos de resposta nem ofereça campos para editá-los.

## 3. Centralize a URL da API

Crie `src/environments/environment.ts`:

```ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
};
```

Substitua URLs repetidas nos services atuais. Exemplo:

```ts
private readonly apiUrl = `${environment.apiUrl}/aluno`;
```

Não inclua `/` no final de `apiUrl`; assim os caminhos ficam previsíveis.

## 4. AuthService com signals e sessionStorage

Crie `src/app/core/auth/auth.service.ts`:

```ts
import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LoginRequest,
  LoginResponse,
  MeResponse,
  Papel,
  Sessao,
} from './auth.models';

const STORAGE_KEY = 'integrador.sessao';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessaoState = signal<Sessao | null>(this.restaurar());

  readonly sessao = this.sessaoState.asReadonly();
  readonly usuario = computed(() => this.sessaoState()?.usuario ?? null);
  readonly papel = computed(() => this.usuario()?.tipoPessoa ?? null);
  readonly autenticado = computed(() => this.sessaoState() !== null);

  login(dados: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, dados).pipe(
      tap((response) => {
        const sessao: Sessao = {
          accessToken: response.accessToken,
          tokenType: response.tokenType,
          expiresAt: Date.now() + response.expiresIn * 1000,
          usuario: response.usuario,
        };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(sessao));
        this.sessaoState.set(sessao);
      }),
    );
  }

  carregarPerfil(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${environment.apiUrl}/auth/me`);
  }

  token(): string | null {
    const sessao = this.sessaoState();
    if (!sessao || sessao.expiresAt <= Date.now()) {
      this.limparSessao();
      return null;
    }
    return sessao.accessToken;
  }

  temPapel(...papeis: Papel[]): boolean {
    const papelAtual = this.papel();
    return papelAtual !== null && papeis.includes(papelAtual);
  }

  logout(): void {
    this.limparSessao();
    void this.router.navigateByUrl('/login');
  }

  redirecionarAposLogin(): void {
    const destino =
      this.papel() === 'ADMIN'
        ? '/alunos'
        : this.papel() === 'MOTORISTA'
          ? '/minhas-viagens'
          : '/rotas';
    void this.router.navigateByUrl(destino);
  }

  private restaurar(): Sessao | null {
    const json = sessionStorage.getItem(STORAGE_KEY);
    if (!json) return null;

    try {
      const sessao = JSON.parse(json) as Sessao;
      if (!sessao.accessToken || !sessao.usuario || sessao.expiresAt <= Date.now()) {
        sessionStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return sessao;
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }

  private limparSessao(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.sessaoState.set(null);
  }
}
```

`sessionStorage` é apagado quando a aba é fechada. Ainda assim, qualquer JavaScript executado na página consegue lê-lo; evite `[innerHTML]` com conteúdo não confiável e mantenha dependências atualizadas.

## 5. Tela standalone de login

Crie `src/app/components/login/login.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', Validators.required],
  });

  entrar(): void {
    if (this.form.invalid || this.carregando()) {
      this.form.markAllAsTouched();
      return;
    }

    this.erro.set(null);
    this.carregando.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: () => this.auth.redirecionarAposLogin(),
        error: (error: HttpErrorResponse) => {
          this.erro.set(
            error.status === 0
              ? 'Não foi possível conectar ao servidor.'
              : error.error?.detail ?? 'Não foi possível entrar.',
          );
        },
      });
  }
}
```

Crie `login.html`:

```html
<div class="min-h-screen flex align-items-center justify-content-center surface-ground p-3">
  <form class="surface-card border-round shadow-2 p-5 w-full" style="max-width: 28rem"
        [formGroup]="form" (ngSubmit)="entrar()">
    <h1 class="mt-0">Entrar</h1>
    <p class="text-color-secondary">Use a conta criada pelo administrador.</p>

    <label for="email" class="block mb-2">E-mail</label>
    <input id="email" pInputText type="email" formControlName="email" class="w-full mb-3" />

    <label for="senha" class="block mb-2">Senha</label>
    <p-password inputId="senha" formControlName="senha" [feedback]="false"
                [toggleMask]="true" styleClass="w-full mb-3" inputStyleClass="w-full" />

    @if (erro()) {
      <p-message severity="error" [text]="erro()!" styleClass="w-full mb-3" />
    }

    <p-button type="submit" label="Entrar" styleClass="w-full"
              [loading]="carregando()" [disabled]="form.invalid" />
  </form>
</div>
```

Não informe se o e-mail ou a senha está incorreto separadamente. O backend retorna deliberadamente uma mensagem genérica.

## 6. Interceptor funcional

Crie `src/app/core/auth/auth.interceptor.ts`:

```ts
import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const chamadaDoBackend = request.url.startsWith(environment.apiUrl);
  const token = chamadaDoBackend ? auth.token() : null;
  const autenticada = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(autenticada).pipe(
    catchError((error: HttpErrorResponse) => {
      const login = request.url === `${environment.apiUrl}/auth/login`;
      if (chamadaDoBackend && error.status === 401 && !login) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
```

Registre-o no `src/app/app.config.ts`. O projeto atual ainda não registra `HttpClient`, então os dois itens são necessários:

```ts
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    // providers atuais...
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
```

Use interceptores funcionais no Angular atual. Não misture esta configuração com `HTTP_INTERCEPTORS` baseado em classes.

## 7. Guards funcionais

Crie `src/app/core/auth/auth.guards.ts`:

```ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Papel } from './auth.models';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { retorno: state.url } });
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token() ? router.createUrlTree(['/perfil']) : true;
};

export function roleGuard(papeis: Papel[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.token()) return router.createUrlTree(['/login']);
    return auth.temPapel(...papeis) ? true : router.createUrlTree(['/sem-permissao']);
  };
}
```

Retorne `UrlTree`; não chame `navigate()` dentro do guard. O guard evita uma tela incorreta, mas não protege dados sozinho. Toda autorização importante precisa continuar no backend.

## 8. Organize as rotas por papel

Adapte `app.routes.ts`. Os componentes `Perfil`, `MinhasViagens` e `SemPermissao` ainda precisam ser criados:

```ts
import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';
import { Login } from './components/login/login';
import { Perfil } from './components/perfil/perfil';
import { MinhasViagens } from './components/viagem/minhas-viagens/minhas-viagens';
import { SemPermissao } from './components/sem-permissao/sem-permissao';

// mantenha os imports atuais dos componentes CRUD

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'sem-permissao', component: SemPermissao, canActivate: [authGuard] },
  { path: 'perfil', component: Perfil, canActivate: [authGuard] },

  { path: 'rotas', component: RotaListar, canActivate: [roleGuard(['ADMIN', 'ALUNO'])] },
  { path: 'grades', component: GradeDiariaListar, canActivate: [roleGuard(['ADMIN', 'ALUNO'])] },
  {
    path: 'pontosembarque',
    component: PontoEmbarqueListar,
    canActivate: [roleGuard(['ADMIN', 'ALUNO'])],
  },
  {
    path: 'minhas-viagens',
    component: MinhasViagens,
    canActivate: [roleGuard(['MOTORISTA'])],
  },

  { path: 'alunos', component: AlunoListar, canActivate: [roleGuard(['ADMIN'])] },
  { path: 'faculdades', component: FaculdadeListar, canActivate: [roleGuard(['ADMIN'])] },
  { path: 'onibuss', component: OnibusListar, canActivate: [roleGuard(['ADMIN'])] },
  { path: 'motoristas', component: MotoristaListar, canActivate: [roleGuard(['ADMIN'])] },
  { path: 'contapagar', component: ContaPagarComponent, canActivate: [roleGuard(['ADMIN'])] },

  { path: '', pathMatch: 'full', redirectTo: 'perfil' },
  { path: '**', redirectTo: 'perfil' },
];
```

O caminho atual `onibuss` contém dois “s”. Você pode mantê-lo por compatibilidade ou renomeá-lo para `onibus`, atualizando todos os links.

No componente de viagens do motorista, chame sempre:

```ts
listarMinhas(): Observable<Viagem[]> {
  return this.http.get<Viagem[]>(`${environment.apiUrl}/viagem/minhas`);
}
```

Não envie `motoristaId`: o backend o obtém do token.

## 9. Layout, sidebar, menus e logout

O `app.html` atual sempre mostra `<app-sidebar>`. Altere-o para depender da autenticação:

```html
@if (auth.autenticado()) {
  <div class="flex h-screen w-full bg-surface-50 dark:bg-surface-950 overflow-hidden">
    <app-sidebar />
    <main class="flex-1 overflow-y-auto p-4 md:p-8">
      <router-outlet />
    </main>
  </div>
} @else {
  <router-outlet />
}
```

No `app.ts`, exponha o serviço:

```ts
readonly auth = inject(AuthService);
```

Na sidebar, filtre entradas por papel e implemente o botão sair:

```ts
readonly auth = inject(AuthService);

readonly itens = computed(() => {
  if (this.auth.temPapel('ADMIN')) {
    return ITENS_ADMIN;
  }
  if (this.auth.temPapel('ALUNO')) {
    return ITENS_ALUNO;
  }
  return ITENS_MOTORISTA;
});

sair(): void {
  this.auth.logout();
}
```

Sugestão de menus:

| Papel | Itens visíveis |
|---|---|
| ADMIN | perfil, alunos, motoristas, ônibus, faculdades, rotas, pontos, grades, viagens, contas e usuários |
| ALUNO | perfil, rotas, pontos, grades e minhas matrículas |
| MOTORISTA | perfil e minhas viagens |

Também esconda botões de cadastrar, editar e excluir nas páginas de leitura do aluno. O backend retornará `403` mesmo que alguém tente chamar esses métodos manualmente.

## 10. Administração segura de usuários

Crie ou adapte `src/app/services/usuario.service.ts`. Todos estes métodos são exclusivos de ADMIN:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AtualizarEmailUsuarioRequest,
  AtualizarStatusUsuarioRequest,
  CriarUsuarioRequest,
  PessoaDisponivel,
  RedefinirSenhaUsuarioRequest,
  UsuarioResumo,
} from '../core/auth/auth.models';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/usuarios`;

  listar(): Observable<UsuarioResumo[]> {
    return this.http.get<UsuarioResumo[]>(this.url);
  }

  listarPessoasSemUsuario(): Observable<PessoaDisponivel[]> {
    return this.http.get<PessoaDisponivel[]>(`${this.url}/pessoas-sem-usuario`);
  }

  criar(dados: CriarUsuarioRequest): Observable<UsuarioResumo> {
    return this.http.post<UsuarioResumo>(this.url, dados);
  }

  atualizarEmail(id: number, email: string): Observable<UsuarioResumo> {
    const dados: AtualizarEmailUsuarioRequest = { email: email.trim() };
    return this.http.patch<UsuarioResumo>(`${this.url}/${id}`, dados);
  }

  redefinirSenha(id: number, senha: string): Observable<void> {
    const dados: RedefinirSenhaUsuarioRequest = { senha };
    return this.http.put<void>(`${this.url}/${id}/senha`, dados);
  }

  atualizarStatus(id: number, ativo: boolean): Observable<UsuarioResumo> {
    const dados: AtualizarStatusUsuarioRequest = { ativo };
    return this.http.patch<UsuarioResumo>(`${this.url}/${id}/status`, dados);
  }

  excluirAcesso(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

Na tabela, mostre `ativo` como tag “Ativo” ou “Inativo” e ofereça estas ações:

- **Editar e-mail**: diálogo com `required` e `email`; o backend normaliza para minúsculas e rejeita duplicidade com `409`.
- **Redefinir senha**: diálogo com confirmação da senha, mínimo de 8 e máximo de 72 caracteres. A senha nunca volta na resposta.
- **Desativar/Reativar**: ação principal para suspender ou devolver o acesso sem apagar a conta.
- **Excluir acesso**: coloque em menu de ações perigosas, com confirmação explícita mencionando o e-mail. A exclusão remove somente as credenciais; não remove o aluno ou motorista.

Exemplo de confirmação com PrimeNG:

```ts
confirmarExclusao(usuario: UsuarioResumo): void {
  if (usuario.id === this.auth.usuario()?.id) return;

  this.confirmationService.confirm({
    header: 'Excluir acesso?',
    message: `A conta ${usuario.email} perderá o acesso. A pessoa cadastrada será preservada.`,
    icon: 'pi pi-exclamation-triangle',
    acceptButtonStyleClass: 'p-button-danger',
    acceptLabel: 'Excluir acesso',
    rejectLabel: 'Cancelar',
    accept: () => this.excluirAcesso(usuario.id),
  });
}
```

Desabilite visualmente **Desativar** e **Excluir acesso** quando `usuario.id === auth.usuario()?.id`. O backend também impede essas operações e protege o último ADMIN ativo. Não dependa apenas do bloqueio visual.

Depois de cada operação bem-sucedida, atualize a lista de usuários. Após criar ou excluir uma conta de ALUNO/MOTORISTA, atualize também `pessoas-sem-usuario`: quando o acesso é excluído, a pessoa preservada volta automaticamente para essa lista.

Editar o e-mail ou redefinir a senha incrementa a versão de segurança e invalida imediatamente todos os JWTs anteriores daquela conta. Se o ADMIN editar o próprio e-mail ou redefinir a própria senha, chame `auth.logout()` após o sucesso e peça um novo login. Desativar, reativar e excluir também invalidam tokens antigos; uma conta reativada precisa fazer um novo login.

Papel, pessoa vinculada, nome, hash BCrypt e versão do token não são editáveis nesta tela. Para alterar dados pessoais, use o cadastro específico de aluno ou motorista.

## 11. Perfil e formatos específicos

O componente de perfil deve chamar `GET /auth/me` e renderizar os blocos conforme o papel:

```ts
readonly perfil = toSignal(this.auth.carregarPerfil(), { initialValue: null });
```

Com `toSignal`, importe `toSignal` de `@angular/core/rxjs-interop`. No template:

```html
@if (perfil(); as dados) {
  <h1>{{ dados.pessoa.nome }}</h1>
  <p>{{ dados.email }}</p>

  @if (dados.pessoa.aluno; as aluno) {
    <p>Matrícula: {{ aluno.matricula || 'Não informada' }}</p>
  }

  @if (dados.pessoa.motorista; as motorista) {
    <p>CNH: {{ motorista.cnh || 'Não informada' }}</p>
  }
}
```

Para ADMIN, `aluno` e `motorista` serão `null`; os dados comuns ficam em `pessoa`.

## 12. Tratamento de erros

Crie uma função reutilizável:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from './auth.models';

export function mensagemApi(error: HttpErrorResponse): string {
  if (error.status === 0) return 'Servidor indisponível. Verifique se o backend está em execução.';

  const problem = error.error as ApiProblem | undefined;
  switch (error.status) {
    case 400:
      return problem?.detail ?? 'Revise os dados informados.';
    case 401:
      return 'Sua sessão é inválida ou expirou. Entre novamente.';
    case 403:
      return 'Você não possui permissão para esta operação.';
    case 404:
      return problem?.detail ?? 'A conta não foi encontrada. Atualize a tabela.';
    case 409:
      return problem?.detail ?? 'A operação entra em conflito com o estado atual.';
    default:
      return problem?.detail ?? 'Ocorreu um erro inesperado.';
  }
}
```

Tratamento visual recomendado:

- `401`: limpar a sessão e redirecionar ao login;
- `403`: manter a sessão e mostrar “sem permissão”;
- `404`: fechar o diálogo e recarregar a tabela, pois o registro pode ter sido removido;
- `409`: manter o formulário e mostrar a causa, como e-mail duplicado, tentativa sobre a própria conta ou proteção do último ADMIN;
- status `0`: informar que o backend está indisponível, sem fazer logout;
- `400` com `errors`: associar mensagens aos campos quando possível.

## 13. Exemplos completos de requisição

Login:

```http
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "email": "usuario@email.com",
  "senha": "SenhaInformada123"
}
```

Resposta resumida:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "usuario": {
    "id": 1,
    "email": "usuario@email.com",
    "tipoPessoa": "ALUNO",
    "pessoaId": 2,
    "nome": "Nome",
    "ativo": true
  }
}
```

Requisição autenticada:

```http
GET http://localhost:8080/auth/me
Authorization: Bearer eyJ...
```

Criação de conta pelo ADMIN:

```http
POST http://localhost:8080/usuarios
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "pessoaId": 5,
  "email": "motorista@email.com",
  "senha": "SenhaInicial123"
}
```

Administração de uma conta pelo ADMIN:

```http
PATCH http://localhost:8080/usuarios/7
Authorization: Bearer <token-admin>
Content-Type: application/json

{"email":"novo@email.com"}
```

```http
PUT http://localhost:8080/usuarios/7/senha
Authorization: Bearer <token-admin>
Content-Type: application/json

{"senha":"NovaSenha123"}
```

```http
PATCH http://localhost:8080/usuarios/7/status
Authorization: Bearer <token-admin>
Content-Type: application/json

{"ativo":false}
```

```http
DELETE http://localhost:8080/usuarios/7
Authorization: Bearer <token-admin>
```

## 14. Checklist manual

- [ ] `provideHttpClient(withInterceptors([authInterceptor]))` está em `app.config.ts`.
- [ ] Login válido grava `integrador.sessao` em `sessionStorage`.
- [ ] Login inválido mostra mensagem genérica e não grava token.
- [ ] Recarregar a página restaura uma sessão ainda válida.
- [ ] Alterar `expiresAt` para o passado força logout.
- [ ] O interceptor não envia o token para URLs fora de `environment.apiUrl`.
- [ ] `GET /auth/me` funciona para ADMIN, ALUNO e MOTORISTA.
- [ ] A sidebar não aparece na tela de login.
- [ ] ADMIN vê todos os menus e operações.
- [ ] ALUNO vê perfil, rotas, grades, pontos e suas próprias matrículas, sem botões administrativos.
- [ ] MOTORISTA vê somente perfil e `/viagem/minhas`.
- [ ] Colar manualmente uma URL proibida leva a `/sem-permissao`.
- [ ] Uma chamada manual proibida ainda recebe `403` do backend.
- [ ] Token apagado, expirado ou adulterado resulta em `401` e retorno ao login.
- [ ] `409` de e-mail/pessoa duplicada preserva o formulário e mostra a causa.
- [ ] A tabela diferencia contas ativas e inativas.
- [ ] Editar e-mail e redefinir senha invalidam imediatamente o token anterior da conta.
- [ ] Conta inativa não consegue fazer login; após reativação, um novo login funciona.
- [ ] Desativar e excluir estão bloqueados visualmente para a própria conta.
- [ ] A API ainda retorna `409` se alguém tentar contornar esse bloqueio visual.
- [ ] Excluir acesso preserva a pessoa e devolve ALUNO/MOTORISTA a `pessoas-sem-usuario`.
- [ ] A exclusão possui confirmação explícita e destaque de perigo.
- [ ] Nenhuma tela permite editar papel, pessoa vinculada, hash ou versão do token.
- [ ] Edição do próprio e-mail/senha encerra a sessão para um novo login.
- [ ] Backend desligado produz mensagem de indisponibilidade, não “senha incorreta”.
- [ ] Logout remove completamente `integrador.sessao`.

## 15. Ordem segura de implementação

Implemente e teste nesta sequência:

1. models e `environment.apiUrl`;
2. `AuthService`;
3. tela de login;
4. registro do `HttpClient` e interceptor;
5. `authGuard` e `roleGuard`;
6. rotas por papel;
7. layout e sidebar;
8. perfil e viagens próprias;
9. tela administrativa de criação de contas;
10. tabela administrativa e ações de e-mail, senha, status e exclusão;
11. testes manuais do checklist.

Referências oficiais: [interceptores HTTP funcionais](https://angular.dev/guide/http/interceptors) e [guards de rotas](https://angular.dev/guide/routing/route-guards).
