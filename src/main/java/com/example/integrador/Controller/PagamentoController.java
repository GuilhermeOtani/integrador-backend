package com.example.integrador.Controller;

import com.example.integrador.DTO.ReciboDTO;
import com.example.integrador.Enum.FormaPagamento;
import com.example.integrador.Model.Pagamento;
import com.example.integrador.Service.PagamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/pagamentos")
@CrossOrigin(origins = "*")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping("/realizar/{contaId}")
    public Pagamento realizarPagamento(
            @PathVariable Long contaId,
            @RequestParam FormaPagamento formaPagamento,
            @RequestParam(required = false) BigDecimal valorPago) {

        return pagamentoService.realizarPagamento(contaId, formaPagamento, valorPago);
    }

    @GetMapping("/recibo/conta/{contaId}")
    public ResponseEntity<ReciboDTO> buscarRecibo(@PathVariable Long contaId) {
        // Chama o novo método do Service
        ReciboDTO reciboDTO = pagamentoService.buscarReciboDTOPorConta(contaId);

        if (reciboDTO == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(reciboDTO);
    }
}