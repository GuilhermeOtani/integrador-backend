package com.example.integrador.DTO;

import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Enum.StatusOnibus;

/** Projeção interna: evita carregar a foto/LOB e os dados pessoais das entidades. */
public record ViagemDashboardBase(Long id, Long gradeId, DiaSemana diaSemana,
        String gradeDescricao, Long rotaId, Long onibusId, String numeroIdentificacao,
        String placa, String modelo, Integer capacidade, StatusOnibus statusOnibus,
        Long motoristaId, String motoristaNome) {}
