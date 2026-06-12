package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import com.example.slagalica.model.mynumber.MyNumberExpressionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MyNumberExpressionEvaluator {

    private static final double EPSILON = 0.000001d;

    private MyNumberExpressionEvaluator() {
    }

    @NonNull
    public static MyNumberExpressionResult evaluate(
            @NonNull String expression,
            @NonNull List<Integer> availableNumbers
    ) {
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return MyNumberExpressionResult.invalid("Unesi izraz.");
        }

        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            return MyNumberExpressionResult.invalid("Izraz nije ispravan.");
        }

        String validationError = validateNumbers(tokens, availableNumbers);
        if (!validationError.isEmpty()) {
            return MyNumberExpressionResult.invalid(validationError);
        }

        List<String> postfix = toPostfix(tokens);
        if (postfix.isEmpty()) {
            return MyNumberExpressionResult.invalid("Izraz nije ispravan.");
        }

        return evaluatePostfix(postfix);
    }

    @NonNull
    private static List<String> tokenize(@NonNull String expression) {
        List<String> tokens = new ArrayList<>();
        int index = 0;

        while (index < expression.length()) {
            char c = expression.charAt(index);

            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }

            if (Character.isDigit(c)) {
                StringBuilder number = new StringBuilder();
                while (index < expression.length()
                        && Character.isDigit(expression.charAt(index))) {
                    number.append(expression.charAt(index));
                    index++;
                }
                tokens.add(number.toString());
                continue;
            }

            if (isOperatorChar(c) || c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                index++;
                continue;
            }

            return new ArrayList<>();
        }

        return tokens;
    }

    @NonNull
    private static String validateNumbers(
            @NonNull List<String> tokens,
            @NonNull List<Integer> availableNumbers
    ) {
        Map<Integer, Integer> availableCounts = new HashMap<>();
        for (Integer number : availableNumbers) {
            if (number == null) {
                continue;
            }
            availableCounts.put(number, availableCounts.getOrDefault(number, 0) + 1);
        }

        Map<Integer, Integer> usedCounts = new HashMap<>();
        for (String token : tokens) {
            if (!isNumber(token)) {
                continue;
            }

            int value;
            try {
                value = Integer.parseInt(token);
            } catch (NumberFormatException e) {
                return "Broj nije ispravan.";
            }

            int available = availableCounts.getOrDefault(value, 0);
            if (available == 0) {
                return "Broj " + value + " nije ponudjen.";
            }

            int used = usedCounts.getOrDefault(value, 0) + 1;
            if (used > available) {
                return "Broj " + value + " je iskoriscen vise puta.";
            }

            usedCounts.put(value, used);
        }

        return "";
    }

    @NonNull
    private static List<String> toPostfix(@NonNull List<String> tokens) {
        List<String> output = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        boolean previousWasValue = false;

        for (String token : tokens) {
            if (isNumber(token)) {
                if (previousWasValue) {
                    return new ArrayList<>();
                }

                output.add(token);
                previousWasValue = true;
                continue;
            }

            if ("(".equals(token)) {
                if (previousWasValue) {
                    return new ArrayList<>();
                }

                operators.add(token);
                previousWasValue = false;
                continue;
            }

            if (")".equals(token)) {
                boolean foundOpening = false;

                while (!operators.isEmpty()) {
                    String op = operators.remove(operators.size() - 1);
                    if ("(".equals(op)) {
                        foundOpening = true;
                        break;
                    }
                    output.add(op);
                }

                if (!foundOpening) {
                    return new ArrayList<>();
                }

                previousWasValue = true;
                continue;
            }

            if (isOperator(token)) {
                if (!previousWasValue) {
                    return new ArrayList<>();
                }

                while (!operators.isEmpty()
                        && isOperator(operators.get(operators.size() - 1))
                        && precedence(operators.get(operators.size() - 1)) >= precedence(token)) {
                    output.add(operators.remove(operators.size() - 1));
                }

                operators.add(token);
                previousWasValue = false;
                continue;
            }

            return new ArrayList<>();
        }

        if (!previousWasValue) {
            return new ArrayList<>();
        }

        while (!operators.isEmpty()) {
            String op = operators.remove(operators.size() - 1);
            if ("(".equals(op) || ")".equals(op)) {
                return new ArrayList<>();
            }
            output.add(op);
        }

        return output;
    }

    @NonNull
    private static MyNumberExpressionResult evaluatePostfix(@NonNull List<String> postfix) {
        List<Double> stack = new ArrayList<>();

        for (String token : postfix) {
            if (isNumber(token)) {
                stack.add(Double.parseDouble(token));
                continue;
            }

            if (!isOperator(token) || stack.size() < 2) {
                return MyNumberExpressionResult.invalid("Izraz nije ispravan.");
            }

            double right = stack.remove(stack.size() - 1);
            double left = stack.remove(stack.size() - 1);

            switch (token) {
                case "+":
                    stack.add(left + right);
                    break;
                case "-":
                    stack.add(left - right);
                    break;
                case "*":
                    stack.add(left * right);
                    break;
                case "/":
                    if (Math.abs(right) < EPSILON) {
                        return MyNumberExpressionResult.invalid("Deljenje nulom nije dozvoljeno.");
                    }
                    stack.add(left / right);
                    break;
                default:
                    return MyNumberExpressionResult.invalid("Nepoznata operacija.");
            }
        }

        if (stack.size() != 1) {
            return MyNumberExpressionResult.invalid("Izraz nije ispravan.");
        }

        return MyNumberExpressionResult.valid(stack.get(0));
    }

    private static boolean isNumber(@NonNull String token) {
        if (token.isEmpty()) {
            return false;
        }

        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isOperator(@NonNull String token) {
        return "+".equals(token)
                || "-".equals(token)
                || "*".equals(token)
                || "/".equals(token);
    }

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static int precedence(@NonNull String operator) {
        if ("*".equals(operator) || "/".equals(operator)) {
            return 2;
        }

        if ("+".equals(operator) || "-".equals(operator)) {
            return 1;
        }

        return 0;
    }
}