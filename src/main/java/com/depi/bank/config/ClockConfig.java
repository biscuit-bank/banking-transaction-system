package com.depi.bank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    public static final ZoneId CAIRO_ZONE = ZoneId.of("Africa/Cairo");

    @Bean
    public Clock systemClock() {
        return Clock.system(CAIRO_ZONE);
    }
}