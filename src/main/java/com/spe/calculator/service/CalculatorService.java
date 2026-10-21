package com.spe.calculator.service;

import org.springframework.stereotype.Service;

/**
 * Pure arithmetic logic. Invalid inputs raise {@link IllegalArgumentException},
 * which the web layer maps to HTTP 400.
 */
@Service
public class CalculatorService {

    public double add(double a, double b) {
        return checked(a, b, a + b);
    }

    public double subtract(double a, double b) {
        return checked(a, b, a - b);
    }

    public double multiply(double a, double b) {
        return checked(a, b, a * b);
    }

    public double divide(double a, double b) {
        requireFinite(a, b);
        if (b == 0) {
            throw new IllegalArgumentException("Division by zero is not allowed");
        }
        return checked(a, b, a / b);
    }

    private static double checked(double a, double b, double result) {
        requireFinite(a, b);
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Result is out of the representable range");
        }
        return result;
    }

    // Double.parseDouble accepts "NaN" and "Infinity", so reject them explicitly
    private static void requireFinite(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("Operands must be finite numbers");
        }
    }
}
