package com.saibonthala.inventory.service;

public class InvalidMovementException extends RuntimeException {

    public InvalidMovementException(String message) {
        super(message);
    }
}
