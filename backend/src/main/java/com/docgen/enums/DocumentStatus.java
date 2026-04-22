package com.docgen.enums;

public enum DocumentStatus {
    COMPLETED("completed"),
    GENERATING("generating"),
    FAILED("failed");

    private final String value;

    DocumentStatus(String value) { this.value = value; }
    public String getValue() { return value; }
}
