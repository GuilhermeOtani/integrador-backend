package com.example.integrador.Service;

import com.example.integrador.DTO.SaldoBaseDTO;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.SaldoBaseCaixa;
import com.example.integrador.Repository.SaldoBaseCaixaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class SaldoBaseCaixaService {
    private final SaldoBaseCaixaRepository repository;
    public SaldoBaseCaixaService(SaldoBaseCaixaRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public SaldoBaseDTO buscar() {
        return dto(repository.findById(1L).orElseThrow(() -> new RecursoNaoEncontradoException("Cadastre o saldo base de caixa antes de gerar o fluxo de caixa.")));
    }
    @Transactional
    public SaldoBaseDTO salvar(SaldoBaseDTO entrada) {
        if (entrada.dataReferencia() == null || entrada.dataReferencia().getYear() < 1 || entrada.dataReferencia().getYear() > 9999 || entrada.saldo() == null || entrada.saldo().stripTrailingZeros().scale() > 2)
            throw new RegraNegocioException("Informe data de referência e saldo com até duas casas decimais.");
        SaldoBaseCaixa atual = repository.findById(1L).orElse(null);
        if (atual == null) {
            if (entrada.versao() != null) throw new ConflitoException("O saldo base ainda não foi cadastrado. Atualize a tela.");
            atual = new SaldoBaseCaixa();
        } else if (!Objects.equals(atual.getVersao(), entrada.versao()))
            throw new ConflitoException("O saldo base foi atualizado. Recarregue antes de salvar.");
        atual.setDataReferencia(entrada.dataReferencia()); atual.setSaldo(entrada.saldo());
        return dto(repository.saveAndFlush(atual));
    }
    private SaldoBaseDTO dto(SaldoBaseCaixa c) { return new SaldoBaseDTO(c.getDataReferencia(), c.getSaldo(), c.getVersao()); }
}
