package io.mars.lite.order.usecase;

import io.mars.lite.order.BusinessException;
import io.mars.lite.order.Order;
import io.mars.lite.order.OrderCreatedEvent;
import io.mars.lite.order.OrderEventPublisher;
import io.mars.lite.order.OrderItem;
import io.mars.lite.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CreateOrderUseCaseTest {

    private OrderRepository orderRepository;
    private OrderEventPublisher eventPublisher;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        eventPublisher = mock(OrderEventPublisher.class);
        useCase = CreateOrderUseCase.create(orderRepository, eventPublisher);
    }

    @Test
    void shouldSaveOrderAndPublishEvent() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("99.99")));
        var input = new CreateOrderUseCase.Input(items, UUID.randomUUID());

        var orderId = useCase.execute(input);

        assertThat(orderId).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(any(OrderCreatedEvent.class));
    }

    @Test
    void shouldThrowWhenItemsEmpty() {
        var input = new CreateOrderUseCase.Input(Set.of(), UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class);

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
