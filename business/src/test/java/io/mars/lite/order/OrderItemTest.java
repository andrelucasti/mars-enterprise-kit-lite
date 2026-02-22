package io.mars.lite.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Test
    void shouldCalculateSubtotalCorrectly() {
        var item = new OrderItem(UUID.randomUUID(), 3, new BigDecimal("10.00"));
        assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), 0, new BigDecimal("10.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity must be positive");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), -1, new BigDecimal("10.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity must be positive");
    }

    @Test
    void shouldRejectNullProductId() {
        assertThatThrownBy(() -> new OrderItem(null, 1, new BigDecimal("10.00")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullUnitPrice() {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), 1, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectZeroUnitPrice() {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), 1, BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unitPrice must be positive");
    }
}
