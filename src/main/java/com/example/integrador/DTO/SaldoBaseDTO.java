package com.example.integrador.DTO;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SaldoBaseDTO(@NotNull LocalDate dataReferencia,
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal saldo, @PositiveOrZero Long versao) {}
