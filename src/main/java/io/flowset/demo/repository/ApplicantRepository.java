package io.flowset.demo.repository;

import io.flowset.demo.model.Applicant;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ApplicantRepository {

    private final Map<String, Applicant> applicants = new ConcurrentHashMap<>();

    public ApplicantRepository() {
        // Demo-friendly dataset for international audience.
        applicants.put(
                "app_low_risk_01",
                new Applicant("app_low_risk_01", "Emma", "Thompson", 36, 210_000, 140_000, "GOOD")
        );
        applicants.put(
                "app_high_risk_01",
                new Applicant("app_high_risk_01", "Carlos", "Vega", 27, 42_000, 780_000, "DELINQUENT")
        );
        applicants.put(
                "app_premium_01",
                new Applicant("app_premium_01", "Olivia", "Chen", 48, 260_000, 150_000, "EXCELLENT")
        );
        applicants.put(
                "app_no_history_01",
                new Applicant("app_no_history_01", "Liam", "Johnson", 24, 68_000, 230_000, "NONE")
        );
        applicants.put(
                "app_borderline_01",
                new Applicant("app_borderline_01", "Sofia", "Martinez", 33, 110_000, 90_000, "GOOD")
        );
        applicants.put(
                "app_senior_large_loan_01",
                new Applicant("app_senior_large_loan_01", "Michael", "Brown", 62, 180_000, 400_000, "GOOD")
        );
    }

    public Applicant findById(String id) {
        return applicants.get(id);
    }
}
