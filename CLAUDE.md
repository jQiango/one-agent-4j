# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

One Agent 4J (一体化智能异常监控系统) is an AI-driven exception monitoring system built with Spring Boot 3 and LangChain4J. It automatically captures exceptions, uses AI to intelligently filter noise/duplicates, persists actionable exceptions to MySQL, and generates tickets with severity levels.

**Core Stack:**
- Java 17, Spring Boot 3.4.8
- LangChain4J 1.7.1 (OpenAI-compatible API integration)
- MyBatis-Plus 3.5.5 (ORM)
- MySQL (persistence)

## Development Commands

### Build and Run
```bash
# Clean and build
mvn clean install

# Run application
mvn spring-boot:run

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=ApplicationTests

# View dependency tree
mvn dependency:tree
```

### Database Setup
```bash
# Initialize database schema
mysql -u root -p < sql/init.sql
```

The database `one_agent` will be created with tables: `exception_record`, `ticket`, `ticket_status_history`.

### Minimal Configuration (Quick Start)

**One Agent 4J follows the "convention over configuration" principle - all features are enabled by default upon dependency inclusion.**

Only **2 mandatory configurations** are required:

```properties
# 1. Database connection (required)
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your_password

# 2. AI API Key (required if using AI denoising)
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
```

That's it! All features are automatically enabled:
- ✅ Exception capture (AOP + Filter + ControllerAdvice)
- ✅ HTTP request logging (request + response)
- ✅ Basic filtering (Ignore List)
- ✅ AI intelligent denoising
- ✅ Exception persistence
- ✅ Ticket generation

See `application-minimal.properties` for a complete minimal configuration example.

### Environment Variables (Optional)
```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=https://api.siliconflow.cn  # Optional
export OPENAI_MODEL_NAME=deepseek-ai/DeepSeek-V3   # Optional
```

## Architecture: Exception Processing Pipeline

The system implements a **capture → collect → denoise → persist → ticket** pipeline:

**Internal Capture**: Filter/ControllerAdvice/AOP → ExceptionCollector → Denoising → Persistence → Ticket Generation

### 1. Exception Capture Layer (`starter.capture`)
Three parallel capture mechanisms feed exceptions into the collector:

- **`ExceptionCaptureFilter`**: Servlet filter (highest precedence) catches HTTP-level exceptions
  - **Smart Deduplication**: Detects Controller exceptions by checking for `DispatcherServlet.doDispatch` in stack trace
  - Only records Filter-layer exceptions (IO errors, etc.)
  - Skips Controller exceptions already handled by `GlobalExceptionHandler` to prevent duplicates
- **`GlobalExceptionHandler`**: `@ControllerAdvice` handles controller exceptions
  - Primary handler for Controller-layer exceptions
  - Records exceptions before Filter layer sees them
- **`ExceptionCaptureAspect`**: AOP intercepts service layer exceptions
  - Default pointcut: `execution(* com.*.*.*.service..*.*(..))`
  - Configurable via `one-agent.capture-config.aop-pointcut`

All three invoke `ExceptionCollector.collect(Throwable)`.

### 2. Exception Collection and Routing (`starter.collector`, `starter.reporter`)

**`ExceptionCollector`** is the central hub:
- Applies basic filters (ignored exceptions/packages from config)
- Builds `ExceptionInfo` with fingerprint (MD5 of type + location + message)
- **HTTP Reporting path**: Applies sampling rate → sends to `ExceptionReporter` (sync/async/batch modes)
- **Local Persistence path**: No sampling, notifies listeners → triggers `ExceptionProcessService`

**Key Design**: Sampling rate only affects HTTP reporting. Local persistence always gets all exceptions (AI does the denoising).

### 3. AI-Powered Denoising (`ai.service`)

When `one-agent.ai-denoise.enabled=true`, `AiDenoiseService` intercepts before persistence:

**Workflow:**
1. Query recent exceptions from DB (lookback window: 2 minutes by default)
2. Build prompt with new exception + historical context (`DenoisePrompt.buildPrompt()`)
3. Call LLM via `DenoiseAiService` interface (LangChain4J `AiServices` pattern)
4. Parse JSON response into `DenoiseDecision`:
   - `shouldAlert`: true/false
   - `isDuplicate`: whether it matches recent exceptions
   - `similarityScore`: 0.0-1.0
   - `suggestedSeverity`: P0/P1/P2/P3/P4
   - `reason`, `suggestion`: human-readable explanations
5. If `shouldAlert=false`, skip persistence and ticket generation

**Fallback**: On any failure, defaults to `shouldAlert=true` to avoid missing critical issues.

### 4. Persistence and Ticket Generation (`service`, `dao`)

**`ExceptionProcessService`** orchestrates:
1. AI denoising (if enabled) - filters noise
2. `ExceptionPersistenceService.saveException()` - writes to `exception_record` table
3. `TicketGenerationService.generateTicket()` - creates or updates ticket

**Ticket Logic:**
- Checks if an open ticket exists for the same fingerprint
- If exists: increment `occurrence_count`, update `last_occurred_at`
- If new: create ticket with AI-suggested severity (or auto-calculated)
- Severity impacts SLA: P0=30min, P1=2h, P2=24h, P3=3d, P4=7d

### Auto-Configuration (`starter.autoconfigure`)

`AgentAutoConfiguration` uses Spring Boot auto-configuration (registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):
- Enabled by default unless `one-agent.enabled=false`
- Conditionally creates beans based on `one-agent.capture-config.*` and `one-agent.ai-denoise.enabled`
- `LangChain4JConfig` only loads when AI denoising is enabled

## Key Architectural Patterns

### Exception Fingerprinting
`FingerprintGenerator` creates MD5 hashes for deduplication:
- Combines: exception type + error location + key part of message
- Used for ticket grouping and AI similarity analysis

### LangChain4J Integration
Uses interface-based AI service definition:
```java
// Define interface with annotations
public interface DenoiseAiService {
    @SystemMessage("...")
    String analyzeException(@UserMessage String prompt);
}

// Spring creates implementation
AiServices.create(DenoiseAiService.class, openAiChatModel)
```

Prompts are built in `DenoisePrompt.buildPrompt()` with structured requirements for JSON output.

### Listener Pattern
`ExceptionCollector` notifies listeners for extensibility. `ExceptionProcessService` registers as a listener via `ExceptionPersistenceConfig`.

## Configuration Reference

### Storage Strategy
- `one-agent.storage-strategy.enable-http-report`: Send to remote server (default: false)
- `one-agent.storage-strategy.enable-local-persistence`: Save to database (default: true)
- `one-agent.storage-strategy.enable-ticket-generation`: Auto-create tickets (default: true)

### AI Denoising
- `one-agent.ai-denoise.enabled`: Enable AI filtering (default: false)
- `one-agent.ai-denoise.lookback-minutes`: Historical context window (default: 2)
- `one-agent.ai-denoise.max-history-records`: Max records for context (default: 20)

### Capture Configuration
- `one-agent.capture-config.enable-filter/enable-controller-advice/enable-aop`: Toggle capture mechanisms
- `one-agent.capture-config.ignored-exceptions`: List of exception class names to skip
- `one-agent.capture-config.ignored-packages`: Package prefixes to skip

### Report Strategy
- `one-agent.report-strategy.mode`: sync/async/batch
- `one-agent.sampling-rate`: 0.0-1.0 (only affects HTTP reporting)

### API Configuration
- `one-agent.api.enabled`: Enable REST API for external platforms (default: true)

## Default Behavior (Convention over Configuration)

**One Agent 4J is designed to work out-of-the-box with minimal configuration.** Upon adding the dependency, all features are automatically enabled with sensible defaults.

### What's Enabled by Default

| Feature | Default Status | Can be Disabled |
|---------|---------------|-----------------|
| **Exception Capture** | ✅ Enabled | `one-agent.enabled=false` |
| - AOP (service layer) | ✅ Enabled | `one-agent.capture-config.enable-aop=false` |
| - Filter (HTTP layer) | ✅ Enabled | `one-agent.capture-config.enable-filter=false` |
| - ControllerAdvice | ✅ Enabled | `one-agent.capture-config.enable-controller-advice=false` |
| **HTTP Request Logging** | ✅ Enabled | `one-agent.http-log.enabled=false` |
| - Print request | ✅ Enabled | `one-agent.http-log.log-request=false` |
| - Print response | ✅ Enabled | `one-agent.http-log.log-response=false` |
| **Basic Filtering (Layer 0)** | ✅ Enabled | `one-agent.ignore-list.enabled=false` |
| **AI Denoising** | ✅ Enabled | `one-agent.ai-denoise.enabled=false` |
| **Exception Persistence** | ✅ Enabled | `one-agent.storage-strategy.enable-local-persistence=false` |
| **Ticket Generation** | ✅ Enabled | `one-agent.storage-strategy.enable-ticket-generation=false` |

### Zero-Configuration Example

Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>com.all.in.one.agent</groupId>
    <artifactId>one-agent-4j-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Configure only **2 required items** in `application.properties`:
```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
spring.datasource.username=root
spring.datasource.password=your_password

# AI API Key (if using AI denoising)
langchain4j.open-ai.chat-model.api-key=your-api-key
```

**That's all!** Start your application and all features work automatically.

### Selective Disabling

If you want to disable specific features:

```properties
# Disable HTTP request logging
one-agent.http-log.enabled=false

# Disable AI denoising (saves API costs)
one-agent.ai-denoise.enabled=false

# Disable ticket generation (only persist exceptions)
one-agent.storage-strategy.enable-ticket-generation=false
```

---

## Important Implementation Notes

### Recursive Exception Prevention

The system uses ThreadLocal to prevent recursive exception capture. When `ExceptionCollector` is processing an exception, any new exceptions thrown during processing are automatically ignored to avoid infinite loops.

**Implementation:** `ExceptionCollector.PROCESSING` ThreadLocal flag

### When Adding New Capture Points
Route through `ExceptionCollector.collect(Throwable)`:
```java
@Autowired
private ExceptionCollector collector;

try {
    // ...
} catch (Exception e) {
    collector.collect(e);
}
```

### When Modifying AI Denoising Logic
- Prompt template: `DenoisePrompt.buildPrompt()`
- Response parsing: `AiDenoiseService.parseAiResponse()` (handles markdown-wrapped JSON)
- Decision model: `DenoiseDecision` class

### When Changing Severity Calculation
Two places compute severity:
1. `TicketGenerationService.calculateSeverity()` - fallback logic
2. AI decision's `suggestedSeverity` - takes precedence if present

Production environment elevates severity by one level (P3→P2, etc.).

## Database Schema

**exception_record**: Stores all captured exceptions with fingerprints, stack traces, request context, and timestamps. Indexed on `app_name`, `environment`, `fingerprint`, `occurred_at`.

**ticket**: Work items for actionable exceptions. Tracks status lifecycle (PENDING→ASSIGNED→IN_PROGRESS→RESOLVED→CLOSED), occurrence counts, SLA, and AI suggestions.

**ticket_status_history**: Audit trail for ticket state transitions.

## Testing Notes

Current test coverage is minimal (`ApplicationTests` only verifies context loading). When adding tests:
- Use `@SpringBootTest` for integration tests
- Mock `DenoiseAiService` to avoid real LLM calls during testing
- Consider H2 in-memory database for DAO tests
