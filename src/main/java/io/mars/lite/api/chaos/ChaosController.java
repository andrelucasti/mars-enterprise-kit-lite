package io.mars.lite.api.chaos;

import io.mars.lite.api.CreateOrderRequest;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.usecase.CreateOrderUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chaos")
@Profile("chaos")
public class ChaosController {

    private final ChaosService chaosService;

    public ChaosController(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @PostMapping("/phantom-event")
    public ResponseEntity<PhantomEventReport> simulatePhantomEvent(
            @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());
        var input = new CreateOrderUseCase.Input(items, request.customerId());

        UUID orderId = null;
        boolean dbRolledBack = false;

        try {
            chaosService.attemptPhantomOrder(input);
        } catch (PhantomEventSimulationException e) {
            orderId = e.getOrderId();
            dbRolledBack = true;
        }

        boolean existsInDb = chaosService.orderExists(orderId);

        var report = new PhantomEventReport(
            orderId,
            existsInDb,
            true,
            dbRolledBack,
            "PHANTOM EVENT: The order.created event was published to Kafka, "
            + "but the order does NOT exist in PostgreSQL. "
            + "Any consumer processing this event will reference a non-existent order."
        );

        return ResponseEntity.ok(report);
    }
}
