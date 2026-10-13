package com.d2ticom.api.modules.sales.dto;

import com.d2ticom.api.modules.sales.domain.FormaPagamento;
import com.d2ticom.api.modules.sales.domain.StatusPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record PedidoRequestDTO(
    Long clienteId,
    StatusPedido status,
    FormaPagamento formaPagamento,
    BigDecimal valorDesconto,
    @NotEmpty(message = "O pedido precisa conter ao menos um item")
    @Valid
    List<ItemPedidoDTO> itens
) {}