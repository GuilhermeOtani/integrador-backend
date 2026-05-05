package com.example.integrador.DTO;

import com.example.integrador.Model.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReciboDTO {
    private Long id;
    private BigDecimal valor;
    private LocalDate dataPagamento;
    private String formaPagamento;
    private String nomeMotorista;
    private String descricaoConta;

    public static ReciboDTO fromEntity(Pagamento pagamento) {
        ReciboDTO dto = new ReciboDTO();
        dto.setId(pagamento.getId());
        dto.setValor(pagamento.getValor());
        dto.setDataPagamento(pagamento.getDataPagamento());
        dto.setFormaPagamento(pagamento.getFormaPagamento().toString());
        dto.setNomeMotorista(pagamento.getContaPagar().getMotorista().getNome());
        dto.setDescricaoConta(pagamento.getContaPagar().getDescricao());
        return dto;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }

    public void setDataPagamento(LocalDate dataPagamento) {
        this.dataPagamento = dataPagamento;
    }

    public String getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(String formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public String getNomeMotorista() {
        return nomeMotorista;
    }

    public void setNomeMotorista(String nomeMotorista) {
        this.nomeMotorista = nomeMotorista;
    }

    public String getDescricaoConta() {
        return descricaoConta;
    }

    public void setDescricaoConta(String descricaoConta) {
        this.descricaoConta = descricaoConta;
    }
}
