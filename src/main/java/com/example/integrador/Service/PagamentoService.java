package com.example.integrador.Service;

import com.example.integrador.Model.Pagamento;
import com.example.integrador.Repository.PagamentoRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PagamentoService {
    private final PagamentoRepository pagamentoRepository;

    public PagamentoService(PagamentoRepository pagamentoRepository) {
        this.pagamentoRepository = pagamentoRepository;
    }

    public List<Pagamento> listarTodosPagamento() {
        return pagamentoRepository.findAll();
    }

    public Pagamento salvarPagamento(Pagamento pagamento) {
        return pagamentoRepository.save(pagamento);
    }

    public Pagamento buscarPagamentoPorId(Long id) {
        return pagamentoRepository
                .findById(id).orElseThrow(() -> new RuntimeException("Pagamento de ID " + id + " não encontrado"));
    }

    public void deletarPagamentoPorId(Long id) {
        pagamentoRepository.deleteById(id);
    }

    public Pagamento atualizarPagamento(Long id, Pagamento pagamento) {
        Pagamento pagamentoSalvo = buscarPagamentoPorId(id);
        BeanUtils.copyProperties(pagamento, pagamentoSalvo, "id");
        return pagamentoRepository.save(pagamentoSalvo);
    }

}
