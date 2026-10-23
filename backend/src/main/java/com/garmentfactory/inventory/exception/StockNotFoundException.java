package com.garmentfactory.inventory.exception;

import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StockNotFoundException extends ApiException {

    public StockNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", message);
    }
}