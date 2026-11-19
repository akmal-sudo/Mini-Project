package com.project.web.controller;

import com.project.dbms.entity.Order;
import com.project.dbms.repository.OrderRepository;
import com.project.os.ExecutionResult;
import com.project.os.OsEngine;
import com.project.os.StockEvent;
import com.project.web.dto.OrderDtos.CreateOrderRequest;
import com.project.web.dto.OrderDtos.OrderDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OsEngine osEngine;

    public OrderController(OrderRepository orderRepository, OsEngine osEngine) {
        this.orderRepository = orderRepository;
        this.osEngine = osEngine;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Order> orders;

        if (status != null && !status.isBlank()) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            orders = orderRepository.findAll(pageRequest).getContent();
        }

        List<OrderDto> dtos = orders.stream().map(o -> OrderDto.builder()
                .orderId(o.getOrderId())
                .eventId(o.getEventId())
                .sku(o.getSku())
                .quantity(o.getQuantity())
                .status(o.getStatus())
                .failureReason(o.getFailureReason())
                .createdAt(o.getCreatedAt())
                .processedAt(o.getProcessedAt())
                .processedBy(o.getProcessedBy())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ExecutionResult> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        StockEvent event = StockEvent.order(request.getSku(), request.getQuantity());
        ExecutionResult result = osEngine.processStockEventSync(event);
        return ResponseEntity.ok(result);
    }
}
