package com.miniapi.router.saas.dto.response;

/** A stable, localizable field validation problem. */
public record ApiFieldError(String field, String reason) {
}
