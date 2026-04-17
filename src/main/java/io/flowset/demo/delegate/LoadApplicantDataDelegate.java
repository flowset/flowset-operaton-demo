package io.flowset.demo.delegate;

import io.flowset.demo.model.Applicant;
import io.flowset.demo.repository.ApplicantRepository;
import io.flowset.demo.variable.VariableConstants;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class LoadApplicantDataDelegate implements JavaDelegate {

    private final ApplicantRepository applicantRepository;

    public LoadApplicantDataDelegate(ApplicantRepository applicantRepository) {
        this.applicantRepository = applicantRepository;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Read applicant ID from process variable.
        String applicantId = (String) execution.getVariable(VariableConstants.APPLICANT_ID);
        if (applicantId == null) {
            throw new RuntimeException("applicantId is not set");
        }

        // Load applicant profile from in-memory repository.
        Applicant applicant = applicantRepository.findById(applicantId);
        if (applicant == null) {
            throw new RuntimeException("Applicant with ID " + applicantId + " was not found");
        }

        // Fill process variables.
        execution.setVariable(VariableConstants.FIRST_NAME, applicant.firstName());
        execution.setVariable(VariableConstants.LAST_NAME, applicant.lastName());
        execution.setVariable(VariableConstants.AGE, applicant.age());
        execution.setVariable(VariableConstants.INCOME, applicant.income());
        execution.setVariable(VariableConstants.LOAN_AMOUNT, applicant.loanAmount());
        execution.setVariable(VariableConstants.CREDIT_HISTORY, applicant.creditHistory());

        // Demo defaults for non-core fields.
        execution.setVariable(VariableConstants.EMAIL, applicant.firstName().toLowerCase() + "@example.com");
        execution.setVariable(VariableConstants.PHONE, "+1 555 010-1234");
        execution.setVariable(VariableConstants.ADDRESS, "100 Market Street, San Francisco, CA");
    }
}
