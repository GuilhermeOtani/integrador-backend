package com.example.integrador.Service;

import com.example.integrador.DTO.RecebimentoResponse;
import com.example.integrador.Enum.*;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.*;
import com.example.integrador.Repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@PreAuthorize("hasRole('ALUNO')")
public class RecebimentoService {
    private final RecebimentoRepository recebimentos;
    private final ContaReceberRepository contas;
    private final PessoaBloqueioService bloqueios;
    private final EntityManager em;
    private final Clock clock;
    public RecebimentoService(RecebimentoRepository recebimentos, ContaReceberRepository contas,
                             PessoaBloqueioService bloqueios, EntityManager em, Clock clock) {
        this.recebimentos = recebimentos; this.contas = contas; this.bloqueios = bloqueios; this.em = em; this.clock = clock;
    }
    @Transactional
    public RecebimentoResponse realizarPagamento(Long contaId, FormaPagamento forma, Long alunoId) {
        return realizarPagamento(contaId, forma, alunoId, null, null);
    }
    @Transactional
    public RecebimentoResponse realizarPagamento(Long contaId, FormaPagamento forma, Long alunoId, BigDecimal valor, String chave) {
        Long proprietario = contas.findAlunoIdById(contaId).filter(id->id.equals(alunoId))
                .orElseThrow(()->new RecursoNaoEncontradoException("Matrícula não encontrada."));
        Aluno aluno = bloqueios.bloquearAluno(proprietario); bloqueios.exigirNaoArquivado(aluno);
        ContaReceber conta = contas.findLockedById(contaId).orElseThrow(()->new RecursoNaoEncontradoException("Matrícula não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE); validarProprietario(conta, alunoId);
        if (forma == null) throw new RegraNegocioException("Informe a forma de pagamento.");
        if (valor != null && (valor.signum() <= 0 || valor.stripTrailingZeros().scale()>2)) throw new RegraNegocioException("O pagamento deve ser positivo, com até duas casas decimais.");
        if (chave != null) {
            try { if (!UUID.fromString(chave).toString().equals(chave)) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException e) { throw new RegraNegocioException("Idempotency-Key deve ser um UUID em minúsculas."); }
        }
        String requisicao = contaId+"|"+forma+"|"+(valor == null?"RESTANTE":valor.stripTrailingZeros().toPlainString());
        if (chave != null) {
            var anterior = recebimentos.findByChaveIdempotencia(chave);
            if (anterior.isPresent()) {
                if (!requisicao.equals(anterior.get().getRequisicaoIdempotencia())) throw new ConflitoException("Chave de idempotência já utilizada em outro recebimento.");
                return RecebimentoResponse.fromEntity(anterior.get());
            }
        }
        if (conta.getValor() == null || conta.getValor().signum() <= 0) throw new RegraNegocioException("Regularize o valor da cobrança antes de pagar.");
        BigDecimal recebido = recebimentos.totalRecebido(contaId), saldo = conta.getValor().subtract(recebido);
        boolean quitacaoLegada = conta.getStatus() == StatusFinanceiro.PAGO && !recebimentos.existsByContaReceberId(contaId);
        if (quitacaoLegada || saldo.signum() <= 0) throw new ConflitoException("Esta matrícula já foi paga.");
        if (recebido.signum()<0) throw new ConflitoException("Regularize os recebimentos negativos legados antes de pagar.");
        BigDecimal efetivo = valor == null?saldo:valor;
        if (efetivo.compareTo(saldo)>0) throw new RegraNegocioException("O pagamento não pode ultrapassar o saldo restante.");
        Recebimento r = new Recebimento(); r.setContaReceber(conta); r.setValor(efetivo); r.setFormaPagamento(forma);
        r.setDataRecebimento(LocalDate.now(clock)); r.setChaveIdempotencia(chave); r.setRequisicaoIdempotencia(chave == null?null:requisicao);
        recebimentos.saveAndFlush(r);
        boolean quitou = efetivo.compareTo(saldo) == 0;
        conta.setStatus(quitou?StatusFinanceiro.PAGO:StatusFinanceiro.PENDENTE);
        if (quitou) aluno.setStatusMatricula(StatusMatricula.ATIV0);
        return RecebimentoResponse.fromEntity(r);
    }
    @Transactional(readOnly = true)
    public RecebimentoResponse buscarRecibo(Long contaId, Long alunoId) {
        ContaReceber conta = contas.findById(contaId).orElseThrow(()->new RecursoNaoEncontradoException("Matrícula não encontrada."));
        validarProprietario(conta, alunoId);
        return recebimentos.findFirstByContaReceberIdOrderByDataRecebimentoDescIdDesc(contaId).map(RecebimentoResponse::fromEntity)
                .orElseThrow(()->new RecursoNaoEncontradoException("Recibo da matrícula não encontrado."));
    }
    @Transactional(readOnly = true)
    public List<RecebimentoResponse> listarPorConta(Long contaId, Long alunoId) {
        ContaReceber conta = contas.findById(contaId).orElseThrow(()->new RecursoNaoEncontradoException("Matrícula não encontrada."));
        validarProprietario(conta, alunoId);
        return recebimentos.findAllByContaReceberIdOrderByDataRecebimentoDescIdDesc(contaId).stream().map(RecebimentoResponse::fromEntity).toList();
    }
    private void validarProprietario(ContaReceber conta, Long alunoId) {
        if (!conta.getAluno().getId().equals(alunoId)) throw new RecursoNaoEncontradoException("Matrícula não encontrada.");
    }
}
