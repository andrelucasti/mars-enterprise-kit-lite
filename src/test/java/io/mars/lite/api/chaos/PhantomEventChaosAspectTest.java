package io.mars.lite.api.chaos;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhantomEventChaosAspectTest {

    private final PhantomEventChaosAspect aspect = new PhantomEventChaosAspect();

    @Test
    @DisplayName("should throw PhantomEventSimulationException after proceed returns")
    void shouldThrowAfterProceedReturns() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        var orderId = UUID.randomUUID();
        when(joinPoint.proceed()).thenReturn(orderId);

        assertThatThrownBy(() -> aspect.forceRollbackAfterPublish(joinPoint))
            .isInstanceOf(PhantomEventSimulationException.class);

        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("should carry the correct orderId in the exception")
    void shouldCarryCorrectOrderIdInException() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        var orderId = UUID.randomUUID();
        when(joinPoint.proceed()).thenReturn(orderId);

        try {
            aspect.forceRollbackAfterPublish(joinPoint);
        } catch (PhantomEventSimulationException e) {
            assertThat(e.getOrderId()).isEqualTo(orderId);
            return;
        }

        throw new AssertionError("Expected PhantomEventSimulationException was not thrown");
    }

    @Test
    @DisplayName("should let proceed execute before throwing")
    void shouldLetProceedExecuteBeforeThrowing() throws Throwable {
        var joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(UUID.randomUUID());

        try {
            aspect.forceRollbackAfterPublish(joinPoint);
        } catch (PhantomEventSimulationException ignored) {
            // expected
        }

        verify(joinPoint).proceed();
    }
}
