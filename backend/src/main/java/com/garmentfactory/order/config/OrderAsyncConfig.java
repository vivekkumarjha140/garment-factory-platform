package com.garmentfactory.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Lets after-commit listeners (invoice, email) run off the request thread. */
@Configuration
@EnableAsync
public class OrderAsyncConfig {
}