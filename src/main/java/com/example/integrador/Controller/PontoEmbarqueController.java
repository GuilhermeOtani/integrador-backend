package com.example.integrador.Controller;

import com.example.integrador.Model.PontoEmbarque;
import com.example.integrador.Service.PontoEmbarqueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;


    @RestController
    @RequestMapping("/ponto-embarque")
    @CrossOrigin(origins = "*")
    public class PontoEmbarqueController {

        private final PontoEmbarqueService pontoEmbarqueService;

        public PontoEmbarqueController(PontoEmbarqueService pontoEmbarqueService) {
            this.pontoEmbarqueService = pontoEmbarqueService;
        }

        @GetMapping("/listar")
        public List<PontoEmbarque> listarTodasPontoEmbarques() {
            return pontoEmbarqueService.listarTodosPontoEmbarque();
        }

        @PostMapping("/salvar-pontoEmbarque")
        public PontoEmbarque salvarPontoEmbarque(@RequestBody PontoEmbarque pontoEmbarque) {
            return pontoEmbarqueService.salvarPontoEmbarque(pontoEmbarque);
        }

        @GetMapping("/buscar-pontoEmbarque/{id}")
        public PontoEmbarque buscarPontoEmbarquePorId(@PathVariable Long id) {
            return pontoEmbarqueService.buscarPontoEmbarquePorId(id);
        }

        @DeleteMapping("/deletar-pontoEmbarque/{id}")
        public void deletarPontoEmbarquePorId(@PathVariable Long id) {
            pontoEmbarqueService.deletarPontoEmbarquePorId(id);
        }

        @PutMapping("/atualizar-pontoEmbarque/{id}")
        public PontoEmbarque atualizarPontoEmbarquePorId(@PathVariable Long id, @RequestBody PontoEmbarque pontoEmbarque) {
            return pontoEmbarqueService.atualizarPontoEmbarque(id, pontoEmbarque);
        }
    }
