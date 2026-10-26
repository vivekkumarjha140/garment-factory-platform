package com.garmentfactory.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The selling business, as printed on invoices (app.business.*). Must be set to real values before go-live. */
@ConfigurationProperties(prefix = "app.business")
public record BusinessProperties(String name, String gstin, String addressLine1, String addressLine2, String city,
                                 String pincode, String state, String stateCode, String email, String phone) {
}