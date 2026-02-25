package io.mars.lite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationContextTest extends AbstractIntegrationTest {
    @Test
    @DisplayName("Application context should load successfully with all beans wired")
    void contextLoads() {
        // No assertions needed — Spring will fail to start if @Service wiring is broken
    }
}
