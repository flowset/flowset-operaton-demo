package io.flowset.demo.delegate;

import io.flowset.demo.variable.VariableConstants;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;

/**
 * Operaton service task delegate that calls OpenAI to perform an AI-assisted
 * fraud check on a loan application.
 *
 * The delegate is wired into the BPMN process as a service task and is invoked
 * automatically by the Operaton engine when the process reaches that step.
 *
 * It reads applicant data from the process context, sends it to OpenAI as a
 * structured prompt, and writes the recommendation and risk level back into
 * the process so downstream tasks (e.g. gateways, human review) can act on them.
 */
@Component
public class AIFraudCheckDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(AIFraudCheckDelegate.class);

    // Number of times to retry the OpenAI call before failing the process task.
    // A failed task creates a Operaton incident for operational visibility.
    private static final int MAX_RETRIES = 3;

    private final RestClient restClient;
    private final String openAiModel;
    private final String openAiApiKey;

    /**
     * All configuration is injected from application.properties (ai.openai.*).
     * RestClient is built once at startup with fixed connect/read timeouts.
     */
    public AIFraudCheckDelegate(
            @Value("${ai.openai.api-url:https://api.openai.com/v1/chat/completions}") String openAiApiUrl,
            @Value("${ai.openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${ai.openai.api-key:}") String openAiApiKey,
            @Value("${ai.openai.timeout-seconds:15}") int timeoutSeconds) {
        this.openAiModel = openAiModel;
        this.openAiApiKey = openAiApiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(timeoutSeconds * 1_000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(openAiApiUrl)
                .build();
    }

    /**
     * Entry point called by the Operaton engine.
     *
     * Reads applicant variables → calls OpenAI → writes results back to the process.
     */
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        AIFraudCheckDelegate.ApplicantContext context = readContext(execution);
        String recommendation = requestOpenAiRecommendation(context);
        String riskLevel = inferRiskLevel(recommendation);

        // Write results back so the BPMN gateway and human review task can read them
        execution.setVariable(VariableConstants.AI_RECOMMENDATION, recommendation);
        execution.setVariable(VariableConstants.AI_RISK_LEVEL, riskLevel);

        log.info("[AI_FRAUD_CHECK] processId={} applicantId={} riskLevel={}",
                execution.getProcessInstanceId(), context.applicantId(), riskLevel);
    }

    /**
     * Sends the applicant prompt to OpenAI and returns the raw recommendation text.
     * Retries up to MAX_RETRIES times on network or API errors.
     * Throws on exhaustion — the Operaton engine will create an incident.
     */
    private String requestOpenAiRecommendation(AIFraudCheckDelegate.ApplicantContext context) {
        List<AIFraudCheckDelegate.Message> messages = List.of(
                new AIFraudCheckDelegate.Message("system", "You are a credit fraud analyst. Be concise and practical."),
                new AIFraudCheckDelegate.Message("user", buildPrompt(context))
        );
        AIFraudCheckDelegate.ChatRequest body = new AIFraudCheckDelegate.ChatRequest(openAiModel, 0.2, 180, messages);

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AIFraudCheckDelegate.ChatResponse response = restClient.post()
                        .header("Authorization", "Bearer " + openAiApiKey.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(AIFraudCheckDelegate.ChatResponse.class);

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    throw new IllegalStateException("OpenAI response does not contain recommendation text");
                }
                String recommendation = response.choices().get(0).message().content().trim();
                if (recommendation.isEmpty()) {
                    throw new IllegalStateException("OpenAI response does not contain recommendation text");
                }
                return recommendation;
            } catch (RestClientException | IllegalStateException ex) {
                lastException = (RuntimeException) ex;
                log.warn("[AI_FRAUD_CHECK] Attempt {}/{} failed. Reason: {}", attempt, MAX_RETRIES, ex.getMessage());
            }
        }
        throw lastException;
    }

    /**
     * Reads all required process variables from the Operaton execution context
     * into a typed record for convenient access throughout this delegate.
     */
    private AIFraudCheckDelegate.ApplicantContext readContext(DelegateExecution execution) {
        return new AIFraudCheckDelegate.ApplicantContext(
                variable(execution, VariableConstants.APPLICANT_ID),
                variable(execution, VariableConstants.FIRST_NAME),
                variable(execution, VariableConstants.LAST_NAME),
                variable(execution, VariableConstants.AGE),
                variable(execution, VariableConstants.INCOME),
                variable(execution, VariableConstants.LOAN_AMOUNT),
                variable(execution, VariableConstants.CREDIT_HISTORY),
                variable(execution, VariableConstants.CREDIT_SCORE),
                variable(execution, VariableConstants.RULE_DECISION)
        );
    }

    /**
     * Builds the user prompt that describes the applicant to OpenAI.
     * Includes the upstream rule-based decision so the AI has full context.
     */
    private String buildPrompt(AIFraudCheckDelegate.ApplicantContext context) {
        return """
                Analyze this loan application for potential fraud and unusual risk patterns.
                Applicant: %s %s (ID: %s)
                Age: %s
                Income: %s
                Loan amount: %s
                Credit history: %s
                Credit score: %s
                Rule decision: %s

                Return:
                1) short recommendation (1-3 sentences)
                2) explicit risk level: LOW, MEDIUM, or HIGH
                """.formatted(
                context.firstName(),
                context.lastName(),
                context.applicantId(),
                context.age(),
                context.income(),
                context.loanAmount(),
                context.creditHistory(),
                context.creditScore(),
                context.ruleDecision() == null ? "N/A" : context.ruleDecision()
        );
    }

    /**
     * Derives a structured risk level by scanning the free-text recommendation
     * for key phrases. Falls back to MEDIUM when the text is ambiguous.
     */
    private String inferRiskLevel(String recommendation) {
        String text = recommendation.toLowerCase(Locale.ROOT);
        if (text.contains("high risk") || text.contains("reject") || text.contains("fraud")) {
            return "HIGH";
        }
        if (text.contains("low risk") || text.contains("approve")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    // Reads a single process variable and safely converts it to String.
    private String variable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value == null ? null : String.valueOf(value);
    }

    // --- Internal data structures ---

    // OpenAI chat message (role = "system" or "user")
    private record Message(String role, String content) {}

    // OpenAI /chat/completions request body
    private record ChatRequest(String model, double temperature, int max_tokens, List<AIFraudCheckDelegate.Message> messages) {}

    // OpenAI /chat/completions response — only the fields we need
    private record ChatChoice(AIFraudCheckDelegate.Message message) {}
    private record ChatResponse(List<AIFraudCheckDelegate.ChatChoice> choices) {}

    // Snapshot of the applicant's process variables for this execution
    private record ApplicantContext(
            String applicantId,
            String firstName,
            String lastName,
            String age,
            String income,
            String loanAmount,
            String creditHistory,
            String creditScore,
            String ruleDecision
    ) {}
}
