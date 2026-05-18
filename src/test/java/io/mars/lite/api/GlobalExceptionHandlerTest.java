package io.mars.lite.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn400WithDescriptiveMessageWhenPathVariableIsNotAValidUUID() {
        var cause = new TypeMismatchException("not-a-uuid", UUID.class);
        var ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", null, cause);

        var response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error"))
                .isEqualTo("Invalid UUID format for parameter 'id'");
    }
}
