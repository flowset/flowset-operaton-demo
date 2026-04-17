package io.flowset.demo.delegate;

import io.flowset.demo.variable.VariableConstants;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class ScoringDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Read variables
        int income = (int) execution.getVariable(VariableConstants.INCOME);
        int loanAmount = (int) execution.getVariable(VariableConstants.LOAN_AMOUNT);
        int age = (int) execution.getVariable(VariableConstants.AGE);
        String creditHistory = (String) execution.getVariable(VariableConstants.CREDIT_HISTORY);

        // Base formula: (income / loanAmount) * 50
        double base = ((double) income / loanAmount) * 50;

        // Age factor
        int ageFactor;
        if (age > 60) {
            ageFactor = 5;
        } else if (age < 25) {
            ageFactor = 0;
        } else {
            ageFactor = 10;
        }

        // Penalty by credit history quality.
        int penalty;
        switch (creditHistory) {
            case "EXCELLENT":
                penalty = 0;
                break;
            case "GOOD":
                penalty = 5;
                break;
            case "DELINQUENT":
                penalty = 20;
                break;
            case "NONE":
                penalty = 10;
                break;
            default:
                penalty = 10;
        }

        // Final score (normalize to 0-100)
        double score = base + ageFactor - penalty;
        if (score < 0) score = 0;
        if (score > 100) score = 100;

        // Save as integer
        execution.setVariable(VariableConstants.CREDIT_SCORE, (int) Math.round(score));
    }
}
