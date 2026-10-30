package com.university.management.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() { super("Account not found"); }
}
