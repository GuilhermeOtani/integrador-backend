package com.example.integrador.Service;

import com.example.integrador.DTO.ContaReceberResponse;
import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.ContaReceber;
import com.example.integrador.Repository.ContaReceberRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContaReceberService {
    private final ContaReceberRepository contareceberRepository;

    public ContaReceberService(ContaReceberRepository contareceberRepository) {
        this.contareceberRepository = contareceberRepository;
    }

    @Transactional(readOnly = true)
    public List<ContaReceberResponse> listarTodasContaReceber() {
        return contareceberRepository.findAll().stream()
                .map(ContaReceberResponse::fromEntity)
                .toList();
    }

    public ContaReceber salvarContaReceber(ContaReceber contareceber) {
        return contareceberRepository.save(contareceber);
    }

    public ContaReceber buscarContaReceberPorId(Long id) {
        return contareceberRepository
                .findById(id).orElseThrow(() -> new RuntimeException("ContaReceber de ID " + id + " não encontrado"));
    }

    public void deletarContaReceberPorId(Long id) {
        contareceberRepository.deleteById(id);
    }

    public ContaReceber atualizarContaReceber(Long id, ContaReceber contareceber) {
        ContaReceber contareceberSalvo = buscarContaReceberPorId(id);
        BeanUtils.copyProperties(contareceber, contareceberSalvo, "id");
        return contareceberRepository.save(contareceberSalvo);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ALUNO')")
    public List<ContaReceberResponse> listarMinhasContas(Long alunoId) {
        return contareceberRepository.findAllByAlunoIdOrderByDataVencimentoDesc(alunoId).stream()
                .map(ContaReceberResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void gerarPrimeiraConta(Aluno aluno) {
        validarMensalidade(aluno.getMensalidade());
        salvarNovaConta(aluno, LocalDate.now().plusDays(30));
    }

    @Transactional
    public void gerarPrimeiraContaSeAusente(Aluno aluno) {
        if (contareceberRepository.findTopByAlunoIdOrderByDataVencimentoDesc(aluno.getId()) == null) {
            gerarPrimeiraConta(aluno);
        }
    }

    @Transactional
    public ContaReceber gerarProximaConta(Aluno aluno, LocalDate dataVencimento) {
        validarMensalidade(aluno.getMensalidade());
        return salvarNovaConta(aluno, dataVencimento);
    }

    private ContaReceber salvarNovaConta(Aluno aluno, LocalDate dataVencimento) {
        ContaReceber novaConta = new ContaReceber();
        novaConta.setDescricao("Matrícula - " + aluno.getNome());
        novaConta.setValor(aluno.getMensalidade());
        novaConta.setDataVencimento(dataVencimento);
        novaConta.setStatus(StatusFinanceiro.PENDENTE);
        novaConta.setAluno(aluno);
        return contareceberRepository.save(novaConta);
    }

    private void validarMensalidade(BigDecimal mensalidade) {
        if (mensalidade == null || mensalidade.signum() <= 0) {
            throw new RegraNegocioException("A mensalidade do aluno deve ser maior que zero");
        }
    }

}
