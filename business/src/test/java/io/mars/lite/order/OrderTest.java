package io.mars.lite.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void shouldCreateOrderWithCreatedStatus() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        var result = Order.create(UUID.randomUUID(), items);

        assertThat(result.domain().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.domain().id()).isNotNull();
        assertThat(result.event()).isNotNull();
        assertThat(result.event().orderId()).isEqualTo(result.domain().id());
    }

    @Test
    void shouldCalculateTotalFromItems() {
        var items = Set.of(
            new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")),
            new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))
        );
        var result = Order.create(UUID.randomUUID(), items);

        assertThat(result.domain().total()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    void shouldThrowWhenItemsAreEmpty() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), Set.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("items cannot be empty");
    }

    @Test
    void shouldThrowWhenItemsAreNull() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("items cannot be empty");
    }

    @Test
    void shouldThrowWhenCustomerIdIsNull() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
        assertThatThrownBy(() -> Order.create(null, items))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("customerId cannot be null");
    }

    @Test
    void shouldCancelOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var cancelled = result.domain().cancel();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldThrowWhenCancellingAlreadyCancelledOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var cancelled = result.domain().cancel();

        assertThatThrownBy(cancelled::cancel)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already cancelled");
    }

    @Test
    void shouldReturnEventWithCorrectData() {
        var customerId = UUID.randomUUID();
        var items = Set.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        var result = Order.create(customerId, items);

        var event = result.event();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.orderId()).isEqualTo(result.domain().id());
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(event.items()).hasSize(1);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void shouldReconstituteOrderFromPersistedData() {
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var total = new BigDecimal("50.00");
        var now = java.time.Instant.now();

        var order = Order.reconstitute(id, customerId, OrderStatus.CREATED, items, total, now, now);

        assertThat(order.id()).isEqualTo(id);
        assertThat(order.customerId()).isEqualTo(customerId);
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.total()).isEqualByComparingTo(total);
    }
}
