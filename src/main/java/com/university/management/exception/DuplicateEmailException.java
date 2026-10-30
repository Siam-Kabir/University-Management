package com.university.management.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() { super("Email is already in use"); }
}
