package io.mars.lite.api;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

class ProductControllerE2ETest extends AbstractIntegrationTest {

    private static final String UUID_REGEX =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @LocalServerPort
    private int port;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/products";
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /products - should create product and return 201")
    void shouldCreateProductAndReturn201() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"name": "Mars Rover Kit", "unitPrice": 299.99}
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("productId", notNullValue())
            .body("productId", matchesPattern(UUID_REGEX));
    }

    @Test
    @DisplayName("POST /products - should return 400 when name is blank")
    void shouldReturn400WhenNameIsBlank() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"name": "", "unitPrice": 99.99}
                """)
        .when()
            .post()
        .then()
            .statusCode(400)
            .body("error", equalTo("name cannot be blank"));
    }

    @Test
    @DisplayName("POST /products - should return 400 when price is negative")
    void shouldReturn400WhenPriceIsNegative() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"name": "Test", "unitPrice": -10.00}
                """)
        .when()
            .post()
        .then()
            .statusCode(400)
            .body("error", equalTo("unitPrice must be positive"));
    }

    @Test
    @DisplayName("GET /products/{id} - should return product details")
    void shouldReturnProductDetails() {
        var productId = given()
            .contentType(ContentType.JSON)
            .body("""
                {"name": "Test Product", "unitPrice": 49.99}
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("productId");

        given()
        .when()
            .get("/{id}", productId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(productId))
            .body("name", equalTo("Test Product"))
            .body("status", equalTo("ACTIVE"));
    }

    @Test
    @DisplayName("GET /products/{id} - should return 404 when not found")
    void shouldReturn404WhenProductNotFound() {
        given()
        .when()
            .get("/{id}", UUID.randomUUID().toString())
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }

    @Test
    @DisplayName("GET /products - should return list of active products only")
    void shouldReturnListOfActiveProductsOnly() {
        // Create 2 products
        given().contentType(ContentType.JSON)
            .body("""{"name": "Product 1", "unitPrice": 10.00}""")
            .post()
            .then().statusCode(201);
        given().contentType(ContentType.JSON)
            .body("""{"name": "Product 2", "unitPrice": 20.00}""")
            .post()
            .then().statusCode(201);

        given()
        .when()
            .get()
        .then()
            .statusCode(200)
            .body("$", hasSize(2));
    }

    @Test
    @DisplayName("PUT /products/{id} - should update price and return 200")
    void shouldUpdatePriceAndReturn200() {
        var productId = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "Test", "unitPrice": 50.00}""")
            .post()
            .then().statusCode(201)
            .extract().jsonPath().getString("productId");

        given()
            .contentType(ContentType.JSON)
            .body("""{"unitPrice": 75.00}""")
        .when()
            .put("/{id}", productId)
        .then()
            .statusCode(200)
            .body("unitPrice", equalTo(75.00f));
    }

    @Test
    @DisplayName("PUT /products/{id} - should return 404 when not found")
    void shouldReturn404WhenUpdatingNonExistentProduct() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"unitPrice": 75.00}""")
        .when()
            .put("/{id}", UUID.randomUUID().toString())
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }

    @Test
    @DisplayName("DELETE /products/{id} - should deactivate and return 204")
    void shouldDeactivateAndReturn204() {
        var productId = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "Test", "unitPrice": 50.00}""")
            .post()
            .then().statusCode(201)
            .extract().jsonPath().getString("productId");

        given()
        .when()
            .delete("/{id}", productId)
        .then()
            .statusCode(204);

        // Verify product is no longer in active list
        given()
        .when()
            .get()
        .then()
            .body("$", hasSize(0));
    }

    @Test
    @DisplayName("DELETE /products/{id} - should return 404 when not found")
    void shouldReturn404WhenDeactivatingNonExistentProduct() {
        given()
        .when()
            .delete("/{id}", UUID.randomUUID().toString())
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }

    @Test
    @DisplayName("GET /products - should return empty list when no products exist")
    void shouldReturnEmptyListWhenNoProductsExist() {
        given()
        .when()
            .get()
        .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }
}
