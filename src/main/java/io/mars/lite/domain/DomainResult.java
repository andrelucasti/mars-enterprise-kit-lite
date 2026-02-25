package io.mars.lite.domain;

public record DomainResult<D, E>(D domain, E event) {
}
