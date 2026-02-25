package io.mars.lite.api.chaos;

import java.util.UUID;

public record PhantomEventReport(
    UUID orderId,
    boolean existsInDb,
    boolean eventSentToKafka,
    boolean dbRolledBack,
    String explanation
) {}
