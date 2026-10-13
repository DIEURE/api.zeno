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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        Pedido pedido = new Pedido();
        pedido.setNumero("PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pedido.setClienteId(dto.clienteId());
        pedido.setStatus(dto.status() != null ? dto.status() : StatusPedido.ORCAMENTO);
        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setValorDesconto(dto.valorDesconto() != null ? dto.valorDesconto() : BigDecimal.ZERO);

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

        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
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

    private PedidoResponseDTO toResponseDTO(Pedido p) {
        List<ItemPedidoDTO> itensDto = p.getItens().stream()
            .map(item -> new ItemPedidoDTO(
                item.getProduto().getId(),
                item.getProduto().getDescricao(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getSubtotal()
            )).toList();

        return new PedidoResponseDTO(
            p.getId(),
            p.getNumero(),
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