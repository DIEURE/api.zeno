package com.d2ticom.api.modules.sales.repository;

import com.d2ticom.api.modules.sales.domain.Pedido;
import com.d2ticom.api.modules.sales.domain.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByNumero(String numero);
    List<Pedido> findByStatusOrderByCreatedAtDesc(StatusPedido status);
}