package com.mcp.springpluggablemcp.controller;

import com.mcp.springpluggablemcp.service.CalculatorService;
import com.mcp.springpluggablemcp.service.CalculatorService.CalculationResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTools {

    private final CalculatorService calculatorService;

    public CalculatorTools(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @Tool(name = "calculate", description = "Perform a basic arithmetic calculation: add, subtract, multiply, or divide two numbers")
    public CalculationResult calculate(
            @ToolParam(description = "First operand") double a,
            @ToolParam(description = "Second operand") double b,
            @ToolParam(description = "Arithmetic operator: add, subtract, multiply, divide") String operator) {
        return calculatorService.calculate(a, b, operator);
    }
}
