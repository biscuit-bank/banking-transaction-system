package com.depi.bank.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter(autoApply = false)
public class PiasterAttributeConverter implements AttributeConverter<BigDecimal, Long> {

    @Override
    public Long convertToDatabaseColumn(BigDecimal attribute) {
        return attribute == null ? null : MoneyUtils.toPiasters(attribute);
    }

    @Override
    public BigDecimal convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : MoneyUtils.toEgp(dbData);
    }
}