package com.example.zavobd;

import java.io.Serializable;

// Serializable allows us to pass this object between activities
public class PID implements Serializable {
    private String command;
    private String description;
    private boolean isSelected;

    public PID(String command, String description) {
        this.command = command;
        this.description = description;
        this.isSelected = false; // Default to not selected
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    // This is important for displaying it in a list
    @Override
    public String toString() {
        return description;
    }
}