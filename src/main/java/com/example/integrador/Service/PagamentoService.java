package com.example.integrador.Service;

import com.example.integrador.DTO.ReciboDTO;
import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.Pagamento;
import com.example.integrador.Repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class PagamentoService {
    private final PagamentoRepository pagamentos;
    private final ContaPagarRepository contas;
    private final EntityManager em;
    private final Clock clock;
    public PagamentoService(PagamentoRepository pagamentos, ContaPagarRepository contas, EntityManager em, Clock clock) {
        this.pagamentos = pagamentos; this.contas = contas; this.em = em; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReciboDTO buscarReciboDTOPorConta(Long contaId) {
        var pagamento = pagamentos.findFirstByContaPagarIdOrderByDataPagamentoDescIdDesc(contaId);
        return pagamento == null ? null : ReciboDTO.fromEntity(pagamento);
    }

    @Transactional(readOnly = true)
    public List<ReciboDTO> listarPorConta(Long contaId) {
        if (!contas.existsById(contaId)) throw new RecursoNaoEncontradoException("Conta a pagar não encontrada.");
        return pagamentos.findAllByContaPagarIdOrderByDataPagamentoDescIdDesc(contaId).stream().map(ReciboDTO::fromEntity).toList();
    }

    @Transactional
    public Pagamento realizarPagamento(Long contaId, FormaPagamento forma, BigDecimal valor) {
        return realizarPagamento(contaId, forma, valor, null);
    }

    @Transactional
    public Pagamento realizarPagamento(Long contaId, FormaPagamento forma, BigDecimal valor, String chave) {
        if (forma == null) throw new RegraNegocioException("Informe a forma de pagamento.");
        if (valor != null && (valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2))
            throw new RegraNegocioException("O pagamento deve ser positivo, com até duas casas decimais.");
        if (chave != null) {
            try {
                if (!UUID.fromString(chave).toString().equals(chave)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) { throw new RegraNegocioException("Idempotency-Key deve ser um UUID em minúsculas."); }
        }
        String requisicao = contaId + "|" + forma + "|" + (valor == null ? "RESTANTE" : valor.stripTrailingZeros().toPlainString());
        var conta = contas.findLockedById(contaId).orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar não encontrada."));
        em.refresh(conta, LockModeType.PESSIMISTIC_WRITE);
        if (chave != null) {
            var anterior = pagamentos.findByChaveIdempotencia(chave);
            if (anterior.isPresent()) {
                if (!requisicao.equals(anterior.get().getRequisicaoIdempotencia()))
                    throw new ConflitoException("Chave de idempotência já utilizada em outro pagamento.");
                return anterior.get();
            }
        }
        if (conta.getValor() == null || conta.getValor().signum() <= 0)
            throw new RegraNegocioException("Regularize o valor da conta antes de pagar.");
        BigDecimal pago = pagamentos.totalPago(contaId);
        BigDecimal saldo = conta.getValor().subtract(pago);
        boolean quitacaoLegada = conta.getStatus() == StatusFinanceiro.PAGO && !pagamentos.existsByContaPagarId(contaId);
        if (quitacaoLegada || saldo.signum() <= 0) throw new ConflitoException("Esta conta já foi paga.");
        if (pago.signum() < 0) throw new ConflitoException("Regularize os pagamentos negativos legados antes de pagar.");
        BigDecimal efetivo = valor == null ? saldo : valor;
        if (efetivo.compareTo(saldo) > 0) throw new RegraNegocioException("O pagamento não pode ultrapassar o saldo restante.");
        Pagamento recibo = new Pagamento();
        recibo.setContaPagar(conta); recibo.setValor(efetivo); recibo.setFormaPagamento(forma);
        recibo.setDataPagamento(LocalDate.now(clock)); recibo.setChaveIdempotencia(chave);
        recibo.setRequisicaoIdempotencia(chave == null ? null : requisicao);
        pagamentos.saveAndFlush(recibo);
        conta.setStatus(efetivo.compareTo(saldo) == 0 ? StatusFinanceiro.PAGO : StatusFinanceiro.PENDENTE);
        return recibo;
    }
}
