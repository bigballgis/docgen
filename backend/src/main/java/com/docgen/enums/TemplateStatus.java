package com.docgen.enums;

public enum TemplateStatus {
    DRAFT("draft"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    PUBLISHED("published");

    private final String value;

    TemplateStatus(String value) { this.value = value; }
    public String getValue() { return value; }
}
