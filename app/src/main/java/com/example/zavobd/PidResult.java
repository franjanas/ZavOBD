package com.example.zavobd;

public class PidResult {
    private String description;
    private String value;

    public PidResult(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}