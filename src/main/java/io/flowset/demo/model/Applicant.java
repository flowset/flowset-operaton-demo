package io.flowset.demo.model;

public record Applicant(
        String id,
        String firstName,
        String lastName,
        int age,
        int income,
        int loanAmount,
        String creditHistory
) {}