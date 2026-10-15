package com.d2ticom.api.modules.sales.service;

import com.d2ticom.api.common.exception.ResourceNotFoundException;
import com.d2ticom.api.modules.catalog.domain.Produto;
import com.d2ticom.api.modules.catalog.repository.ProdutoRepository;
import com.d2ticom.api.modules.sales.domain.ItemPedido;
import com.d2ticom.api.modules.sales.domain.Pedido;
import com.d2ticom.api.modules.sales.domain.StatusPedido;
import com.d2ticom.api.modules.sales.dto.ItemPedidoDTO;
import com.d2ticom.api.modules.sales.dto.PedidoRequestDTO;
import com.d2ticom.api.modules.sales.dto.PedidoResponseDTO;
import com.d2ticom.api.modules.sales.repository.PedidoRepository;
import com.d2ticom.api.modules.stock.service.EstoqueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueService estoqueService;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         EstoqueService estoqueService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueService = estoqueService;
    }

    @Transactional
    public PedidoResponseDTO criarPedido(PedidoRequestDTO dto) {
        if (dto.itens() == null || dto.itens().isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa conter ao menos um item.");
        }

        StatusPedido status = dto.status() != null ? dto.status() : StatusPedido.ORCAMENTO;

        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.clienteId());
        pedido.setStatus(status);
        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setValorDesconto(dto.valorDesconto() != null ? dto.valorDesconto() : BigDecimal.ZERO);

        if (status == StatusPedido.ORCAMENTO) {
            pedido.setNumeroOrcamento(gerarNumeroOrcamento());
        } else if (status == StatusPedido.FINALIZADO) {
            pedido.setNumeroVenda(gerarNumeroVendaDireta());
        }

        BigDecimal totalItens = BigDecimal.ZERO;
        List<ItemPedido> itens = new ArrayList<>();

        for (ItemPedidoDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.produtoId()));

            BigDecimal subtotal = itemDto.precoUnitario().multiply(itemDto.quantidade());
            totalItens = totalItens.add(subtotal);

            ItemPedido item = new ItemPedido(pedido, produto, itemDto.quantidade(), itemDto.precoUnitario(), subtotal);
            itens.add(item);
        }

        pedido.setItens(itens);
        pedido.setValorTotal(totalItens.subtract(pedido.getValorDesconto()));

        if (status == StatusPedido.FINALIZADO) {
            pedido.setFinalizadoAt(LocalDateTime.now());
            pedidoRepository.save(pedido);

            for (ItemPedido item : pedido.getItens()) {
                estoqueService.baixarEstoque(item.getProduto().getId(), item.getQuantidade(), pedido.getId());
            }
        } else {
            pedidoRepository.save(pedido);
        }

        return toResponseDTO(pedido);
    }

    @Transactional
    public PedidoResponseDTO finalizarOrcamento(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado ID: " + pedidoId));

        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
            throw new IllegalStateException("Pedido já se encontra finalizado.");
        }

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Não é possível finalizar um pedido cancelado.");
        }

        String numeroOrcamento = pedido.getNumeroOrcamento();
        if (numeroOrcamento != null) {
            pedido.setNumeroOrcamentoOrigem(numeroOrcamento);
            pedido.setNumeroVenda(numeroOrcamento.replaceFirst("^PED-", "VND-"));
        } else {
            pedido.setNumeroVenda(gerarNumeroVendaDireta());
        }

        for (ItemPedido item : pedido.getItens()) {
            estoqueService.baixarEstoque(item.getProduto().getId(), item.getQuantidade(), pedido.getId());
        }

        pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setFinalizadoAt(LocalDateTime.now());
        pedidoRepository.save(pedido);

        return toResponseDTO(pedido);
    }

    @Transactional
    public void cancelarPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado ID: " + pedidoId));

        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
            for (ItemPedido item : pedido.getItens()) {
                estoqueService.estornarEstoque(item.getProduto().getId(), item.getQuantidade(), pedido.getId());
            }
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        pedidoRepository.save(pedido);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado ID: " + id));
        return toResponseDTO(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarOrcamentosPendentes() {
        return pedidoRepository.findByStatusOrderByCreatedAtDesc(StatusPedido.ORCAMENTO)
            .stream()
            .map(this::toResponseDTO)
            .toList();
    }

    private String gerarNumeroOrcamento() {
        LocalDate hoje = LocalDate.now();
        int ano = hoje.getYear();
        int diaDoAno = hoje.getDayOfYear();

        long orcamentosHoje = pedidoRepository.countByStatusAndCreatedAtBetween(
            StatusPedido.ORCAMENTO,
            hoje.atStartOfDay(),
            hoje.plusDays(1).atStartOfDay()
        );

        int seq = (int) orcamentosHoje + 1;
        if (seq > 99) {
            throw new IllegalStateException("Limite diário de orçamentos atingido (99).");
        }

        return String.format("PED-%d-%03d%02d", ano, diaDoAno, seq);
    }

    private String gerarNumeroVendaDireta() {
        LocalDate hoje = LocalDate.now();
        long vendas = pedidoRepository.countByNumeroVendaStartingWith(String.format("VND-%d-", hoje.getYear()));
        return String.format("VND-%d-%05d", hoje.getYear(), vendas + 1);
    }

    private PedidoResponseDTO toResponseDTO(Pedido p) {
    	List<ItemPedidoDTO> itensDto = p.getItens().stream()
    		    .map(item -> new ItemPedidoDTO(
    		        item.getProduto().getId(),
    		        item.getProduto().getDescricao(),
    		        item.getProduto().getUnidade(),
    		        item.getQuantidade(),
    		        item.getPrecoUnitario(),
    		        item.getSubtotal()
    		    )).toList();

        return new PedidoResponseDTO(
            p.getId(),
            p.getNumeroExibicao(),
            p.getNumeroOrcamento(),
            p.getNumeroVenda(),
            p.getNumeroOrcamentoOrigem(),
            p.getClienteId(),
            p.getStatus(),
            p.getFormaPagamento(),
            p.getValorTotal(),
            p.getValorDesconto(),
            p.getCreatedAt(),
            p.getFinalizadoAt(),
            itensDto
        );
    }
}