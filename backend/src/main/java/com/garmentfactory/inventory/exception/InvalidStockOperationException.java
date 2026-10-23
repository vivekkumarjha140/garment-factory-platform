package com.garmentfactory.inventory.exception;

import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidStockOperationException extends ApiException {

    public InvalidStockOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_STOCK_OPERATION", message);
    }
}