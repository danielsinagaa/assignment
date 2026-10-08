package com.finshot.remittance.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private String customerId;

    private String name;

    protected Customer() {
        // for JPA
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }
}
