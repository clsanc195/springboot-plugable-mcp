package com.mcp.springpluggablemcp.service;

import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    public record CalculationResult(String expression, double result) {}

    public CalculationResult calculate(double a, double b, String operator) {
        double result = switch (operator.toLowerCase()) {
            case "add", "+" -> a + b;
            case "subtract", "-" -> a - b;
            case "multiply", "*" -> a * b;
            case "divide", "/" -> {
                if (b == 0) throw new ArithmeticException("Division by zero");
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unknown operator: " + operator
                    + ". Supported: add, subtract, multiply, divide");
        };
        String expression = "%s %s %s = %s".formatted(a, operator, b, result);
        return new CalculationResult(expression, result);
    }
}
