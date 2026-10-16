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
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping("/realizar/{contaId}")
    public Pagamento realizarPagamento(
            @PathVariable Long contaId,
            @RequestParam FormaPagamento formaPagamento,
            @RequestParam(required = false) BigDecimal valorPago,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave) {

        return pagamentoService.realizarPagamento(contaId, formaPagamento, valorPago, chave);
    }

    @GetMapping("/conta/{contaId}")
    public java.util.List<ReciboDTO> listarPorConta(@PathVariable Long contaId) {
        return pagamentoService.listarPorConta(contaId);
    }

    @GetMapping("/recibo/conta/{contaId}")
    public ResponseEntity<ReciboDTO> buscarRecibo(@PathVariable Long contaId) {
        ReciboDTO reciboDTO = pagamentoService.buscarReciboDTOPorConta(contaId);

        if (reciboDTO == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(reciboDTO);
    }
}
