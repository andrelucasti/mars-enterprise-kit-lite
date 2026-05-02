package io.mars.lite.api.chaos;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.Product;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import io.mars.lite.infrastructure.persistence.ProductEntity;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ActiveProfiles("chaos")
class ChaosControllerE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();

        // Create a test product for chaos tests
        testProduct = Product.create("Chaos Test Product", new BigDecimal("149.95"));
        productJpaRepository.save(ProductEntity.of(testProduct));
    }

    @Test
    @DisplayName("POST /chaos/phantom-event - should return report with existsInDb false")
    void shouldReturnPhantomEventReport() {
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
                """.formatted(testProduct.id()))
        .when()
            .post("/chaos/phantom-event")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("orderId", notNullValue())
            .body("existsInDb", equalTo(false))
            .body("eventSentToKafka", equalTo(true))
            .body("dbRolledBack", equalTo(true))
            .body("explanation", containsString("PHANTOM EVENT"));
    }

    @Test
    @DisplayName("POST /chaos/phantom-event - order should NOT exist in database after call")
    void shouldNotPersistOrderInDatabase() {
        var orderId = given()
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
                """.formatted(testProduct.id()))
        .when()
            .post("/chaos/phantom-event")
        .then()
            .statusCode(200)
            .extract().jsonPath().getString("orderId");

        assertThat(orderJpaRepository.findById(UUID.fromString(orderId))).isEmpty();
    }

    @Test
    @DisplayName("POST /orders - normal endpoint should still work (chaos is isolated)")
    void shouldNotAffectNormalOrderCreation() {
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
                """.formatted(testProduct.id()))
        .when()
            .post("/orders")
        .then()
            .statusCode(201)
            .body("orderId", notNullValue());

        assertThat(orderJpaRepository.count()).isEqualTo(1);
    }
}
