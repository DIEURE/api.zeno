package com.d2ticom.api.modules.sales.dto;

import com.d2ticom.api.modules.sales.domain.FormaPagamento;
import com.d2ticom.api.modules.sales.domain.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
    Long id,
    String numero,
    Long clienteId,
    StatusPedido status,
    FormaPagamento formaPagamento,
    BigDecimal valorTotal,
    BigDecimal valorDesconto,
    LocalDateTime createdAt,
    LocalDateTime finalizadoAt,
    List<ItemPedidoDTO> itens
) {}