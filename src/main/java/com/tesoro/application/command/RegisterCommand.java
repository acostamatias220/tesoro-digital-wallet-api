package com.tesoro.application.command;

public record RegisterCommand(String email, String password, String fullName) {

    @Override
    public String toString() {
        return "RegisterCommand[email=" + email + ", fullName=" + fullName + "]";
    }
}
