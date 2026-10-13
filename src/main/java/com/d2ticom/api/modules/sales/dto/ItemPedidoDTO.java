package com.d2ticom.api.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ItemPedidoDTO(
    @NotNull(message = "ID do produto é obrigatório") Long produtoId,
    String descricaoProduto,
    @NotNull(message = "Quantidade é obrigatória") @Positive BigDecimal quantidade,
    @NotNull(message = "Preço unitário é obrigatório") @Positive BigDecimal precoUnitario,
    BigDecimal subtotal
) {}