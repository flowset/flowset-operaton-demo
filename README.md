# Flowset Demo — Loan Scoring with Operaton & AI Fraud Check

A Spring Boot application that demonstrates end-to-end BPMN process automation using **Operaton 7**.

The process models a **loan application review** pipeline: it calculates a credit score, evaluates it against a DMN decision table, runs a parallel AI-powered fraud check via OpenAI, and routes the result to a human reviewer in Operaton Tasklist.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| BPM Engine | Operaton Platform 1.1.1 |
| Decision Engine | Operaton DMN |
| Database | H2 (file-based, auto-created) |
| AI | OpenAI API (`gpt-4o-mini`) |
| Build | Gradle 8 (wrapper included) |

---

## Prerequisites

- **Java 17+** installed and on `PATH`
- An **OpenAI API key** (required for the AI fraud-check step)

No other installations needed — Operaton runs embedded inside the Spring Boot app, and H2 is created automatically on first run.

---

## Quick Start

**1. Clone the repository**

```bash
git clone https://github.com/your-org/flowset-operaton-demo.git
cd flowset-operaton-demo
```

**2. Set your OpenAI API key**

Open `src/main/resources/application.properties` and replace the placeholder:

```properties
ai.openai.api-key=<your-key-here>
```

> If you skip this step the application will still start, but the AI Fraud Check step will fail and create a Operaton incident.

**3. Run the application**

```bash
./gradlew bootRun
```

The application starts on **http://localhost:8080**.

---

## Operaton UI

| URL | Purpose |
|---|---|
| http://localhost:8080/operaton/app/tasklist | Start and review loan applications |
| http://localhost:8080/operaton/app/cockpit | Monitor active process instances |
| http://localhost:8080/operaton/app/admin | Manage users and authorizations |

**Login:** `admin` / **Password:** `admin`

---

## How to Run the Process

1. Open **Tasklist** → click **Start process** → choose `loan-scoring-v1`
2. A start form appears — select one of the pre-loaded applicants from the dropdown (see table below) → click **Submit**
3. The process runs automatically:
   - loads applicant data
   - calculates a credit score
   - in parallel: evaluates the DMN rule + calls OpenAI for fraud analysis
4. Once both parallel branches complete, a **Review Application** task appears in your Tasklist
5. Open the task — you see all applicant data, the credit score, the rule decision, and the AI recommendation
6. Choose **Approve** or **Reject** → Submit
7. The process completes (approval or rejection is logged to the console)

---

## Demo Applicants

Six applicants are pre-loaded in `ApplicantRepository`. Each is designed to produce a different outcome:

| Applicant ID | Name | Age | Income | Loan | Credit History | Expected outcome |
|---|---|---|---|---|---|---|
| `app_low_risk_01` | Emma Thompson | 36 | $210,000 | $140,000 | GOOD | High score → Approve |
| `app_premium_01` | Olivia Chen | 48 | $260,000 | $150,000 | EXCELLENT | Max score → Approve |
| `app_borderline_01` | Sofia Martinez | 33 | $110,000 | $90,000 | GOOD | Mid score → borderline |
| `app_no_history_01` | Liam Johnson | 24 | $68,000 | $230,000 | NONE | Young + no history → low score |
| `app_senior_large_loan_01` | Michael Brown | 62 | $180,000 | $400,000 | GOOD | Large loan → low score |
| `app_high_risk_01` | Carlos Vega | 27 | $42,000 | $780,000 | DELINQUENT | Low score + delinquent → Reject |

---

## Process Architecture

```
Start Event (form)
    │
    ▼
Load Applicant Data       ← reads applicant from in-memory repository
    │
    ▼
Calculate Score           ← scoring formula based on income/loan ratio, age, credit history
    │
    ▼
Parallel Gateway ─────────────────────────────────┐
    │                                             │
    ▼                                             ▼
Evaluate Rule Decision                    AI Fraud Check
(DMN: loan-decision.dmn)                  (OpenAI gpt-4o-mini)
ruleDecision = Approve / Reject           aiRecommendation + aiRiskLevel
    │                                             │
    └──────────────────┬───────────────────────────┘
                       │
                       ▼
             Review Application       ← human task in Tasklist
             (review-form-v1.form)
                       │
                       ▼
             Exclusive Gateway
             ┌─────────┴─────────┐
             ▼                   ▼
       Notify Approval     Notify Rejection
       (log)               (log)
             │                   │
             ▼                   ▼
          End Event           End Event
```

### Scoring Formula

```
score = (income / loanAmount) × 50
      + ageFactor   (age 25–60 → +10,  age < 25 → +0,  age > 60 → +5)
      − penalty     (EXCELLENT → 0,  GOOD → −5,  NONE → −10,  DELINQUENT → −20)

Clamped to [0, 100].  Score ≥ 70  →  DMN outputs "Approve".
```

### DMN Decision Table (`loan-decision.dmn`)

| Credit Score | Rule Decision |
|---|---|
| ≥ 70 | Approve |
| < 70 | Reject |

---

## Project Structure

```
src/main/java/io/flowset/demo/
├── FlowsetDemoApplication.java          # Spring Boot entry point
├── delegate/
│   ├── LoadApplicantDataDelegate.java   # Loads applicant, sets process variables
│   ├── ScoringDelegate.java             # Computes credit score
│   ├── AIFraudCheckDelegate.java        # Calls OpenAI, writes recommendation + risk level
│   ├── ApprovalNotifyDelegate.java      # Logs approval
│   └── RefusalNotifyDelegate.java       # Logs rejection
├── model/
│   └── Applicant.java                   # Immutable record
├── repository/
│   └── ApplicantRepository.java         # In-memory store with 6 demo applicants
├── security/
│   └── WebSecurityConfiguration.java    # Security + CORS (open for demo)
└── variable/
    └── VariableConstants.java           # All process variable name constants

src/main/resources/
├── application.properties               # App + Operaton + OpenAI config
├── processes/
│   ├── loan-scoring-v1.bpmn             # Main loan scoring process
│   └── simple-process.bpmn             # Minimal Hello World process (for reference)
├── decisions/
│   └── loan-decision.dmn               # DMN rule: score → Approve / Reject
└── process-forms/
    ├── start-form-v1.form              # Applicant selector (start event form)
    └── review-form-v1.form             # Human review form with all computed variables
```

---

## Configuration Reference

All configuration lives in `src/main/resources/application.properties`:

```properties
# H2 file-based database (auto-created in ./h2/)
spring.datasource.url=jdbc:h2:file:./h2/operaton-h2-database

# Operaton admin credentials
operaton.bpm.admin-user.id=admin
operaton.bpm.admin-user.password=admin

# OpenAI integration
ai.openai.api-url=https://api.openai.com/v1/chat/completions
ai.openai.model=gpt-4o-mini
ai.openai.timeout-seconds=15
ai.openai.api-key=<your-key-here>
```

The H2 database file is created automatically at `./h2/operaton-h2-database.mv.db` and is excluded from git.
