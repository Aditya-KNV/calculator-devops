package com.spe.calculator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorServiceTest {

    private static final double DELTA = 1e-9;

    private final CalculatorService service = new CalculatorService();

    @ParameterizedTest
    @CsvSource({"2, 3, 5", "-2, 3, 1", "0.1, 0.2, 0.3", "0, 0, 0"})
    void add(double a, double b, double expected) {
        assertEquals(expected, service.add(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"5, 3, 2", "3, 5, -2", "-1, -1, 0"})
    void subtract(double a, double b, double expected) {
        assertEquals(expected, service.subtract(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"4, 2.5, 10", "-3, 3, -9", "7, 0, 0"})
    void multiply(double a, double b, double expected) {
        assertEquals(expected, service.multiply(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"10, 4, 2.5", "-9, 3, -3", "0, 5, 0", "1, 3, 0.333333333333"})
    void divide(double a, double b, double expected) {
        assertEquals(expected, service.divide(a, b), 1e-6);
    }

    @Test
    void divideByZeroThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.divide(1, 0));
        assertEquals("Division by zero is not allowed", ex.getMessage());
    }

    @Test
    void divideZeroByZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.divide(0, 0));
    }

    @Test
    void nonFiniteOperandsThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.add(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> service.subtract(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> service.divide(Double.NEGATIVE_INFINITY, 2));
    }

    @Test
    void overflowThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.multiply(Double.MAX_VALUE, 2));
    }
}
