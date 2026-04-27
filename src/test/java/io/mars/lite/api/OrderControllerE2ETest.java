package io.mars.lite.api;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

class OrderControllerE2ETest extends AbstractIntegrationTest {

    private static final String UUID_REGEX =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @LocalServerPort
    private int port;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/orders";
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /orders - should create order and return 201")
    void shouldCreateOrderAndReturn201() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {
                            "productId": "123e4567-e89b-12d3-a456-426614174000",
                            "quantity": 2,
                            "unitPrice": 149.95
                        }
                    ]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("orderId", notNullValue())
            .body("orderId", matchesPattern(UUID_REGEX));
    }

    @Test
    @DisplayName("POST /orders - should return 400 when customerId is null")
    void shouldReturn400WhenCustomerIdIsNull() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": null,
                    "items": [{"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 2, "unitPrice": 10.00}]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("POST /orders - should return 400 when items are empty")
    void shouldReturn400WhenItemsAreEmpty() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": []
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("GET /orders/{id} - should return order details")
    void shouldReturnOrderDetails() {
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 1, "unitPrice": 99.99}
                    ]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("orderId");

        given()
        .when()
            .get("/{id}", orderId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(orderId))
            .body("status", equalTo("CREATED"))
            .body("items", hasSize(1));
    }

    @Test
    @DisplayName("GET /orders/{id} - should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() {
        given()
        .when()
            .get("/{id}", UUID.randomUUID().toString())
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - should cancel a CREATED order and return 200 with status CANCELLED")
    void shouldCancelCreatedOrderAndReturn200() {
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [{"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 1, "unitPrice": 99.99}]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("orderId");

        given()
        .when()
            .post("/{id}/cancel", orderId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(orderId))
            .body("status", equalTo("CANCELLED"));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - should return 400 when order is already cancelled")
    void shouldReturn400WhenOrderIsAlreadyCancelled() {
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [{"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 1, "unitPrice": 99.99}]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("orderId");

        given()
        .when()
            .post("/{id}/cancel", orderId)
        .then()
            .statusCode(200);

        given()
        .when()
            .post("/{id}/cancel", orderId)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", notNullValue());
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - should return 400 when cancelling a non-existent order")
    void shouldReturn400WhenCancellingNonExistentOrder() {
        given()
        .when()
            .post("/{id}/cancel", UUID.randomUUID().toString())
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", containsString("Order not found"));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - should return 400 when order id is a malformed UUID")
    void shouldReturn400WhenOrderIdIsMalformedUUID() {
        given()
        .when()
            .post("/{id}/cancel", "not-a-uuid")
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Invalid UUID format for parameter 'id'"));
    }

    @Test
    @DisplayName("GET /orders/{id} - should return status CANCELLED after cancel")
    void shouldReturnCancelledStatusAfterCancel() {
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [{"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 1, "unitPrice": 99.99}]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("orderId");

        given()
        .when()
            .post("/{id}/cancel", orderId)
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/{id}", orderId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", equalTo("CANCELLED"));
    }
}
