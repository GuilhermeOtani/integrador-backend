package com.example.integrador.DTO;

import com.example.integrador.Model.Motorista;
import com.example.integrador.Model.Onibus;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;

public class MotoristaDTO {

    private Long id;
    private String nome;
    private String cpfCnpj;
    private String cnh;
    private BigDecimal salario;
    private String telefone;


    public MotoristaDTO() {
    }

    public MotoristaDTO(Motorista motorista) {
        this.id = motorista.getId();
        this.nome = motorista.getNome();
        this.cpfCnpj = motorista.getCpfCnpj();
        this.cnh = motorista.getCnh();
        this.salario = motorista.getSalario();
        this.telefone = motorista.getTelefone();


    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public void setCpfCnpj(String cpfCnpj) {
        this.cpfCnpj = cpfCnpj;
    }

    public String getCnh() {
        return cnh;
    }

    public void setCnh(String cnh) {
        this.cnh = cnh;
    }

    public BigDecimal getSalario() {
        return salario;
    }

    public void setSalario(BigDecimal salario) {
        this.salario = salario;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }
}