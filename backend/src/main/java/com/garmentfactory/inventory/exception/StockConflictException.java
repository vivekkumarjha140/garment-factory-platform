package com.garmentfactory.inventory.exception;

import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 409 for any stock rule violation. */
public class StockConflictException extends ApiException {

    public StockConflictException(String message) {
        this("STOCK_CONFLICT", message);
    }

    protected StockConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}