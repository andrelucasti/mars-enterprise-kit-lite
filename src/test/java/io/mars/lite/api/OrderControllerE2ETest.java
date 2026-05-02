package io.mars.lite.api;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.Product;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import io.mars.lite.infrastructure.persistence.ProductEntity;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
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

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/orders";
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /orders - Create Order")
    class CreateOrderTests {

        @Test
        @DisplayName("should create order and return 201 when product exists")
        void shouldCreateOrderAndReturn201WhenProductExists() {
            // Arrange: Create an active product first
            var product = Product.create("Test Product", new BigDecimal("149.95"));
            productJpaRepository.save(ProductEntity.of(product));

            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "customerId": "550e8400-e29b-41d4-a716-446655440000",
                        "items": [
                            {
                                "productId": "%s",
                                "quantity": 2
                            }
                        ]
                    }
                    """.formatted(product.id()))
            .when()
                .post()
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("orderId", notNullValue())
                .body("orderId", matchesPattern(UUID_REGEX));
        }

        @Test
        @DisplayName("should return 400 when customerId is null")
        void shouldReturn400WhenCustomerIdIsNull() {
            var product = Product.create("Test Product", new BigDecimal("10.00"));
            productJpaRepository.save(ProductEntity.of(product));

            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "customerId": null,
                        "items": [{"productId": "%s", "quantity": 2}]
                    }
                    """.formatted(product.id()))
            .when()
                .post()
            .then()
                .statusCode(400);
        }

        @Test
        @DisplayName("should return 400 when items are empty")
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
        @DisplayName("should return 400 when product does not exist")
        void shouldReturn400WhenProductDoesNotExist() {
            var nonExistentProductId = UUID.randomUUID();

            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "customerId": "550e8400-e29b-41d4-a716-446655440000",
                        "items": [
                            {
                                "productId": "%s",
                                "quantity": 1
                            }
                        ]
                    }
                    """.formatted(nonExistentProductId))
            .when()
                .post()
            .then()
                .statusCode(400)
                .body("message", containsString("Product not found: " + nonExistentProductId));
        }

        @Test
        @DisplayName("should return 400 when product is inactive")
        void shouldReturn400WhenProductIsInactive() {
            // Create an inactive product
            var activeProduct = Product.create("Inactive Product", new BigDecimal("50.00"));
            var inactiveProduct = activeProduct.deactivate();
            productJpaRepository.save(ProductEntity.of(inactiveProduct));

            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "customerId": "550e8400-e29b-41d4-a716-446655440000",
                        "items": [
                            {
                                "productId": "%s",
                                "quantity": 1
                            }
                        ]
                    }
                    """.formatted(inactiveProduct.id()))
            .when()
                .post()
            .then()
                .statusCode(400)
                .body("message", containsString("Product is inactive: " + inactiveProduct.id()));
        }
    }

    @Nested
    @DisplayName("GET /orders/{id} - Get Order")
    class GetOrderTests {

        @Test
        @DisplayName("should return order details")
        void shouldReturnOrderDetails() {
            // Create a product first
            var product = Product.create("Test Product", new BigDecimal("99.99"));
            productJpaRepository.save(ProductEntity.of(product));

            var orderId = given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "customerId": "550e8400-e29b-41d4-a716-446655440000",
                        "items": [
                            {"productId": "%s", "quantity": 1}
                        ]
                    }
                    """.formatted(product.id()))
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
        @DisplayName("should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() {
            given()
            .when()
                .get("/{id}", UUID.randomUUID().toString())
            .then()
                .statusCode(404);
        }
    }
}
