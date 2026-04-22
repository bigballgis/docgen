package com.docgen.enums;

public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String value;

    UserStatus(String value) { this.value = value; }
    public String getValue() { return value; }
}
