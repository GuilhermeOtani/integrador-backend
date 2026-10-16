package com.example.integrador.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class SaldoBaseCaixa {
    @Id private Long id = 1L;
    @Column(nullable = false) private LocalDate dataReferencia;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal saldo;
    @Version private Long versao;
}
