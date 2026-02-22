package io.mars.lite.order.usecase;

import io.mars.lite.order.BusinessException;
import io.mars.lite.order.Order;
import io.mars.lite.order.OrderItem;
import io.mars.lite.order.OrderRepository;
import io.mars.lite.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancelOrderUseCaseTest {

    private OrderRepository orderRepository;
    private CancelOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        useCase = CancelOrderUseCase.create(orderRepository);
    }

    @Test
    void shouldCancelExistingOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var order = result.domain();
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        useCase.execute(order.id());

        verify(orderRepository).update(argThat(o -> o.status() == OrderStatus.CANCELLED));
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        var orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId))
            .isInstanceOf(BusinessException.class);

        verify(orderRepository, never()).update(any());
    }
}
