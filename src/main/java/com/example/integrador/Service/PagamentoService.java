package com.example.integrador.Service;

import com.example.integrador.DTO.ReciboDTO;
import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Model.Pagamento;
import com.example.integrador.Repository.ContaPagarRepository;
import com.example.integrador.Repository.PagamentoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PagamentoService {
    private final PagamentoRepository pagamentoRepository;
    private final ContaPagarRepository contaPagarRepository;

    public PagamentoService(PagamentoRepository pagamentoRepository, ContaPagarRepository contaPagarRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.contaPagarRepository = contaPagarRepository;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ReciboDTO buscarReciboDTOPorConta(Long contaId) {
        Pagamento pagamento = pagamentoRepository.findByContaPagarId(contaId);

        if (pagamento == null) {
            return null;
        }

        return ReciboDTO.fromEntity(pagamento);
    }

    @Transactional
    public Pagamento realizarPagamento(Long contaId, FormaPagamento formaPagamento, BigDecimal valorPago) {

        ContaPagar conta = contaPagarRepository.findById(contaId)
                .orElseThrow(() -> new RuntimeException("ContaPagar não encontrada!"));

        if (conta.getStatus() == StatusFinanceiro.PAGO) {
            throw new RuntimeException("Esta conta já foi paga!");
        }

        Pagamento recibo = new Pagamento();
        recibo.setContaPagar(conta);
        recibo.setDataPagamento(LocalDate.now());
        recibo.setFormaPagamento(formaPagamento);

        if (valorPago == null) {
            recibo.setValor(conta.getValor());
        } else {
            recibo.setValor(valorPago);
        }

        pagamentoRepository.save(recibo);

        conta.setStatus(StatusFinanceiro.PAGO);
        contaPagarRepository.save(conta);

        return recibo;
    }

}

