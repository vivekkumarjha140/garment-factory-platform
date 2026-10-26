package com.garmentfactory.order.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An address copied into the order. Column names are set by the owning Order (ship_* / bill_*). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {

    private String recipientName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
}