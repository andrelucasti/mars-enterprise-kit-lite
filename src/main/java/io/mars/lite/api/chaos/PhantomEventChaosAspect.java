package io.mars.lite.api.chaos;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Profile("chaos")
public class PhantomEventChaosAspect {

    private static final Logger log = LoggerFactory.getLogger(PhantomEventChaosAspect.class);

    @Around("execution(* io.mars.lite.api.chaos.ChaosOrderExecutor.execute(..))")
    public Object forceRollbackAfterPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID orderId = (UUID) joinPoint.proceed();
        log.warn("CHAOS: Forcing rollback after UseCase.execute(). "
                + "Order {} will be rolled back, but Kafka event is already sent.", orderId);
        throw new PhantomEventSimulationException(orderId);
    }
}
