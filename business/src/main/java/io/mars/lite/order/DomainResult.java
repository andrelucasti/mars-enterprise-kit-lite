package io.mars.lite.order;

public record DomainResult<D, E>(D domain, E event) {}
