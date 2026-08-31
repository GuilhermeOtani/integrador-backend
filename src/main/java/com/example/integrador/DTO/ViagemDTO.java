package com.example.integrador.DTO;

import com.example.integrador.Model.Faculdade;
import com.example.integrador.Model.Viagem;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ViagemDTO {

    private Long id;
    private LocalDate data;

    private Long rotaId;
    private String rotaNome;

    private Long onibusId;
    private String onibusPlaca;
    private String numeroIdentificacao;

    private Long motoristaId;
    private String motoristaNome;

    private List<Faculdade> faculdades = new ArrayList<>();

    public ViagemDTO() {
    }

    public ViagemDTO(Viagem viagem) {
        this.id = viagem.getId();
        this.data = viagem.getData();

        if (viagem.getRota() != null) {
            this.rotaId = viagem.getRota().getId();
            this.rotaNome = viagem.getRota().getNome();
        }

        if (viagem.getOnibus() != null) {
            this.onibusId = viagem.getOnibus().getId();
            this.onibusPlaca = viagem.getOnibus().getPlaca();
            this.numeroIdentificacao = viagem.getOnibus().getNumeroIdentificacao();
        }

        if (viagem.getMotorista() != null) {
            this.motoristaId = viagem.getMotorista().getId();
            this.motoristaNome = viagem.getMotorista().getNome();

            if (viagem.getFaculdades() != null) {
                this.faculdades = new ArrayList<>(viagem.getFaculdades());
            }
        }
    }
}