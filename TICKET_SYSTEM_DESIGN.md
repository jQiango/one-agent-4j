# 工单管理系统设计文档

## 1. 概述

工单管理系统是告警处理的核心环节，负责将降噪后的告警自动转化为工单，跟踪处理流程，记录处理方案，形成知识沉淀。

### 核心功能

- **自动工单生成**：降噪后的告警自动生成工单
- **智能分派**：根据服务归属自动分配负责人和处理人
- **状态流转**：完整的工单生命周期管理
- **处理方案记录**：沉淀历史处理经验
- **统计分析**：工单趋势、处理效率分析

---

## 2. 数据模型设计

### 2.1 工单表 (ticket)

```sql
CREATE TABLE ticket (
    -- 基础信息
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
    ticket_no VARCHAR(64) UNIQUE NOT NULL COMMENT '工单编号，如TICK-20250124-001',
    title VARCHAR(255) NOT NULL COMMENT '工单标题',

    -- 告警关联
    alert_event_id BIGINT NOT NULL COMMENT '关联的告警事件ID',
    exception_fingerprint VARCHAR(64) NOT NULL COMMENT '异常指纹，用于关联历史方案',

    -- 服务信息
    service_name VARCHAR(128) NOT NULL COMMENT '服务名称',
    environment VARCHAR(32) NOT NULL COMMENT '环境：prod/uat/test',

    -- 问题分类
    problem_type VARCHAR(64) NOT NULL COMMENT '问题类型：NULL_POINTER/OOM/TIMEOUT/DB_ERROR等',
    problem_category VARCHAR(32) NOT NULL COMMENT '问题分类：BUG/PERFORMANCE/CONFIG/DEPLOY',
    severity VARCHAR(16) NOT NULL COMMENT '严重级别：P0/P1/P2/P3/P4',

    -- 责任人
    service_owner VARCHAR(64) COMMENT '项目负责人',
    assignee VARCHAR(64) COMMENT '处理人',
    reporter VARCHAR(64) DEFAULT 'AI-Agent' COMMENT '报告人',

    -- 告警内容
    alert_content TEXT NOT NULL COMMENT '告警内容（包含堆栈、上下文等）',
    stack_trace TEXT COMMENT '完整堆栈信息',
    error_location VARCHAR(255) COMMENT '错误位置：类名.方法名:行号',
    occurrence_count INT DEFAULT 1 COMMENT '发生次数',
    first_occurred_at TIMESTAMP NOT NULL COMMENT '首次发生时间',
    last_occurred_at TIMESTAMP NOT NULL COMMENT '最后发生时间',

    -- AI 分析结果
    ai_root_cause TEXT COMMENT 'AI分析的根因',
    ai_suggested_solution TEXT COMMENT 'AI推荐的解决方案',
    similar_cases JSON COMMENT '相似历史案例',

    -- 处理状态
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：见状态机',
    progress INT DEFAULT 0 COMMENT '处理进度 0-100',

    -- 处理过程
    accepted_at TIMESTAMP NULL COMMENT '接受时间',
    started_at TIMESTAMP NULL COMMENT '开始处理时间',
    resolved_at TIMESTAMP NULL COMMENT '解决时间',
    closed_at TIMESTAMP NULL COMMENT '关闭时间',

    -- 处理方案
    solution TEXT COMMENT '实际处理方案',
    solution_type VARCHAR(32) COMMENT '方案类型：CODE_FIX/CONFIG_CHANGE/ROLLBACK/IGNORE',
    root_cause TEXT COMMENT '确认的根因（人工确认）',
    code_changes JSON COMMENT '代码变更记录',

    -- SLA
    expected_resolve_time TIMESTAMP COMMENT '期望解决时间（根据severity计算）',
    actual_resolve_duration INT COMMENT '实际解决耗时（分钟）',
    sla_breached BOOLEAN DEFAULT FALSE COMMENT '是否超时',

    -- 备注和标签
    remark TEXT COMMENT '备注',
    tags JSON COMMENT '标签',

    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_ticket_no (ticket_no),
    INDEX idx_alert_event (alert_event_id),
    INDEX idx_fingerprint (exception_fingerprint),
    INDEX idx_service (service_name, environment),
    INDEX idx_assignee (assignee, status),
    INDEX idx_status (status, severity),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';
```

### 2.2 工单状态流转表 (ticket_status_history)

```sql
CREATE TABLE ticket_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL COMMENT '工单ID',
    ticket_no VARCHAR(64) NOT NULL COMMENT '工单编号',

    from_status VARCHAR(32) COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '新状态',

    operator VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型：ASSIGN/ACCEPT/START/RESOLVE/CLOSE/REOPEN',
    comment TEXT COMMENT '操作备注',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ticket_id (ticket_id),
    INDEX idx_ticket_no (ticket_no),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单状态流转历史';
```

### 2.3 工单协作记录表 (ticket_collaboration)

```sql
CREATE TABLE ticket_collaboration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL COMMENT '工单ID',
    ticket_no VARCHAR(64) NOT NULL COMMENT '工单编号',

    participant VARCHAR(64) NOT NULL COMMENT '参与人',
    role VARCHAR(32) NOT NULL COMMENT '角色：ASSIGNEE/REVIEWER/OBSERVER/AI_AGENT',
    action VARCHAR(64) NOT NULL COMMENT '动作：COMMENT/ANALYZE/SUGGEST/REVIEW',

    content TEXT NOT NULL COMMENT '内容',
    attachments JSON COMMENT '附件',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ticket_id (ticket_id),
    INDEX idx_participant (participant),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单协作记录';
```

---

## 3. 工单状态机设计

### 3.1 状态定义

```java
public enum TicketStatus {
    PENDING("待分派", "告警已确认，等待分派处理人"),
    ASSIGNED("已分派", "已分派处理人，等待接受"),
    ACCEPTED("已接受", "处理人已接受，待开始处理"),
    IN_PROGRESS("处理中", "正在处理问题"),
    WAITING_VERIFY("待验证", "已部署修复，等待验证"),
    RESOLVED("已解决", "问题已解决，待关闭"),
    CLOSED("已关闭", "工单已关闭"),
    REJECTED("已拒绝", "处理人拒绝处理"),
    REOPENED("已重开", "问题重现，重新打开工单");

    private final String label;
    private final String description;
}
```

### 3.2 状态流转规则

```
                    ┌─────────────┐
                    │   PENDING   │ (待分派)
                    └──────┬──────┘
                           │ assign()
                           ↓
                    ┌─────────────┐
          ┌─────────┤  ASSIGNED   │ (已分派)
          │         └──────┬──────┘
          │                │ accept()     reject()
          │                ↓                 ↓
          │         ┌─────────────┐   ┌────────────┐
          │         │  ACCEPTED   │   │  REJECTED  │
          │         └──────┬──────┘   └────────────┘
          │                │ start()
          │                ↓
          │         ┌─────────────┐
          │    ┌────┤ IN_PROGRESS │ (处理中)
          │    │    └──────┬──────┘
          │    │           │ waitVerify()
          │    │           ↓
          │    │    ┌─────────────────┐
          │    │    │ WAITING_VERIFY  │ (待验证)
          │    │    └──────┬──────────┘
          │    │           │ resolve()
          │    │           ↓
          │    │    ┌─────────────┐
          │    │    │  RESOLVED   │ (已解决)
          │    │    └──────┬──────┘
          │    │           │ close()
          │    │           ↓
          │    │    ┌─────────────┐
          │    │    │   CLOSED    │ (已关闭)
          │    │    └─────────────┘
          │    │           │
          │    └───────────┘ reopen()
          │                ↓
          └────────→ [REOPENED] ──→ ASSIGNED
```

### 3.3 状态机实现

```java
@Component
public class TicketStateMachine {

    /**
     * 状态流转映射
     */
    private static final Map<TicketStatus, List<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
        TicketStatus.PENDING, List.of(TicketStatus.ASSIGNED),
        TicketStatus.ASSIGNED, List.of(TicketStatus.ACCEPTED, TicketStatus.REJECTED),
        TicketStatus.ACCEPTED, List.of(TicketStatus.IN_PROGRESS),
        TicketStatus.IN_PROGRESS, List.of(TicketStatus.WAITING_VERIFY, TicketStatus.RESOLVED),
        TicketStatus.WAITING_VERIFY, List.of(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS),
        TicketStatus.RESOLVED, List.of(TicketStatus.CLOSED, TicketStatus.REOPENED),
        TicketStatus.CLOSED, List.of(TicketStatus.REOPENED),
        TicketStatus.REJECTED, List.of(TicketStatus.ASSIGNED),
        TicketStatus.REOPENED, List.of(TicketStatus.ASSIGNED)
    );

    /**
     * 状态流转
     */
    public void transition(Ticket ticket, TicketStatus targetStatus, String operator, String comment) {
        TicketStatus currentStatus = ticket.getStatus();

        // 验证流转是否合法
        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                String.format("不允许从 %s 流转到 %s", currentStatus, targetStatus)
            );
        }

        // 更新工单状态
        ticket.setStatus(targetStatus);
        updateTicketTimestamp(ticket, targetStatus);

        // 记录状态流转历史
        recordStatusHistory(ticket, currentStatus, targetStatus, operator, comment);

        // 发送通知
        sendNotification(ticket, targetStatus, operator);
    }

    /**
     * 检查是否允许流转
     */
    public boolean canTransition(TicketStatus from, TicketStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptyList()).contains(to);
    }

    /**
     * 更新时间戳
     */
    private void updateTicketTimestamp(Ticket ticket, TicketStatus status) {
        Instant now = Instant.now();
        switch (status) {
            case ACCEPTED -> ticket.setAcceptedAt(now);
            case IN_PROGRESS -> ticket.setStartedAt(now);
            case RESOLVED -> ticket.setResolvedAt(now);
            case CLOSED -> {
                ticket.setClosedAt(now);
                // 计算实际处理耗时
                if (ticket.getStartedAt() != null) {
                    long duration = Duration.between(ticket.getStartedAt(), now).toMinutes();
                    ticket.setActualResolveDuration((int) duration);
                }
            }
        }
    }

    /**
     * 记录状态流转历史
     */
    private void recordStatusHistory(Ticket ticket, TicketStatus from, TicketStatus to,
                                     String operator, String comment) {
        TicketStatusHistory history = TicketStatusHistory.builder()
            .ticketId(ticket.getId())
            .ticketNo(ticket.getTicketNo())
            .fromStatus(from)
            .toStatus(to)
            .operator(operator)
            .operationType(determineOperationType(to))
            .comment(comment)
            .build();

        statusHistoryRepository.save(history);
    }

    /**
     * 确定操作类型
     */
    private String determineOperationType(TicketStatus status) {
        return switch (status) {
            case ASSIGNED -> "ASSIGN";
            case ACCEPTED -> "ACCEPT";
            case IN_PROGRESS -> "START";
            case RESOLVED -> "RESOLVE";
            case CLOSED -> "CLOSE";
            case REOPENED -> "REOPEN";
            default -> "UPDATE";
        };
    }
}
```

---

## 4. 工单自动生成服务

### 4.1 工单生成器

```java
@Service
@Slf4j
public class TicketGenerationService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ServiceOwnerResolver serviceOwnerResolver;

    @Autowired
    private AssigneeResolver assigneeResolver;

    @Autowired
    private TicketNumberGenerator ticketNumberGenerator;

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    private SimilarCaseRetriever similarCaseRetriever;

    /**
     * 从告警事件生成工单
     */
    public Ticket generateFromAlert(AlertEvent alertEvent) {
        log.info("开始为告警事件 {} 生成工单", alertEvent.getId());

        // 1. 生成工单编号
        String ticketNo = ticketNumberGenerator.generate();

        // 2. 解析问题类型
        ProblemClassification classification = classifyProblem(alertEvent);

        // 3. AI 分析根因和解决方案
        AiAnalysisResult aiAnalysis = aiAnalysisService.analyze(
            alertEvent.getStackTrace(),
            alertEvent.getServiceName()
        );

        // 4. 检索相似历史案例
        List<SimilarCase> similarCases = similarCaseRetriever.retrieve(
            alertEvent.getExceptionFingerprint()
        );

        // 5. 解析责任人
        String serviceOwner = serviceOwnerResolver.resolve(alertEvent.getServiceName());
        String assignee = assigneeResolver.resolve(alertEvent, classification);

        // 6. 计算期望解决时间（根据severity）
        Instant expectedResolveTime = calculateExpectedResolveTime(
            alertEvent.getSeverity()
        );

        // 7. 构建工单
        Ticket ticket = Ticket.builder()
            // 基础信息
            .ticketNo(ticketNo)
            .title(generateTitle(alertEvent))
            // 告警关联
            .alertEventId(alertEvent.getId())
            .exceptionFingerprint(alertEvent.getExceptionFingerprint())
            // 服务信息
            .serviceName(alertEvent.getServiceName())
            .environment(alertEvent.getEnvironment())
            // 问题分类
            .problemType(classification.getType())
            .problemCategory(classification.getCategory())
            .severity(alertEvent.getSeverity())
            // 责任人
            .serviceOwner(serviceOwner)
            .assignee(assignee)
            .reporter("AI-Agent")
            // 告警内容
            .alertContent(buildAlertContent(alertEvent))
            .stackTrace(alertEvent.getStackTrace())
            .errorLocation(alertEvent.getErrorLocation())
            .occurrenceCount(alertEvent.getOccurrenceCount())
            .firstOccurredAt(alertEvent.getFirstOccurredAt())
            .lastOccurredAt(alertEvent.getLastOccurredAt())
            // AI 分析
            .aiRootCause(aiAnalysis.getRootCause())
            .aiSuggestedSolution(aiAnalysis.getSuggestedSolution())
            .similarCases(toJson(similarCases))
            // 状态
            .status(TicketStatus.PENDING)
            .progress(0)
            // SLA
            .expectedResolveTime(expectedResolveTime)
            .slaBreached(false)
            .build();

        // 8. 保存工单
        ticket = ticketRepository.save(ticket);

        // 9. 自动分派
        if (assignee != null) {
            autoAssign(ticket, assignee);
        }

        log.info("工单 {} 生成完成，分派给 {}", ticketNo, assignee);

        return ticket;
    }

    /**
     * 问题分类
     */
    private ProblemClassification classifyProblem(AlertEvent alertEvent) {
        String exceptionType = alertEvent.getExceptionType();

        // 根据异常类型分类
        ProblemType type = switch (exceptionType) {
            case "NullPointerException" -> ProblemType.NULL_POINTER;
            case "OutOfMemoryError" -> ProblemType.OOM;
            case "TimeoutException" -> ProblemType.TIMEOUT;
            case "SQLException", "DataAccessException" -> ProblemType.DB_ERROR;
            case "HttpClientException" -> ProblemType.HTTP_ERROR;
            default -> ProblemType.UNKNOWN;
        };

        // 根据问题类型确定分类
        ProblemCategory category = switch (type) {
            case NULL_POINTER, UNKNOWN -> ProblemCategory.BUG;
            case OOM, TIMEOUT -> ProblemCategory.PERFORMANCE;
            case DB_ERROR, HTTP_ERROR -> ProblemCategory.CONFIG;
            default -> ProblemCategory.BUG;
        };

        return new ProblemClassification(type, category);
    }

    /**
     * 生成工单标题
     */
    private String generateTitle(AlertEvent alertEvent) {
        return String.format(
            "[%s][%s] %s - %s",
            alertEvent.getSeverity(),
            alertEvent.getServiceName(),
            alertEvent.getExceptionType(),
            alertEvent.getErrorLocation()
        );
    }

    /**
     * 构建告警内容
     */
    private String buildAlertContent(AlertEvent alertEvent) {
        return String.format("""
            服务: %s
            环境: %s
            异常类型: %s
            错误位置: %s
            发生次数: %d
            首次发生: %s
            最后发生: %s

            错误信息:
            %s

            堆栈信息:
            %s
            """,
            alertEvent.getServiceName(),
            alertEvent.getEnvironment(),
            alertEvent.getExceptionType(),
            alertEvent.getErrorLocation(),
            alertEvent.getOccurrenceCount(),
            alertEvent.getFirstOccurredAt(),
            alertEvent.getLastOccurredAt(),
            alertEvent.getExceptionMessage(),
            alertEvent.getStackTrace()
        );
    }

    /**
     * 计算期望解决时间
     */
    private Instant calculateExpectedResolveTime(String severity) {
        Instant now = Instant.now();
        return switch (severity) {
            case "P0" -> now.plus(Duration.ofMinutes(30));  // 30分钟
            case "P1" -> now.plus(Duration.ofHours(2));     // 2小时
            case "P2" -> now.plus(Duration.ofHours(8));     // 8小时
            case "P3" -> now.plus(Duration.ofHours(24));    // 1天
            case "P4" -> now.plus(Duration.ofDays(3));      // 3天
            default -> now.plus(Duration.ofDays(1));
        };
    }

    /**
     * 自动分派
     */
    private void autoAssign(Ticket ticket, String assignee) {
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticketRepository.save(ticket);

        // 发送通知
        notificationService.notifyAssignment(ticket, assignee);
    }
}
```

### 4.2 服务负责人解析器

```java
@Service
public class ServiceOwnerResolver {

    @Autowired
    private ServiceMetadataRepository serviceMetadataRepository;

    /**
     * 解析服务负责人
     */
    public String resolve(String serviceName) {
        // 从服务元数据中查询
        ServiceMetadata metadata = serviceMetadataRepository.findByServiceName(serviceName);
        if (metadata != null && metadata.getOwner() != null) {
            return metadata.getOwner();
        }

        // 从 Git 仓库查询（主要贡献者）
        String gitOwner = queryGitOwner(serviceName);
        if (gitOwner != null) {
            return gitOwner;
        }

        // 默认值
        return "unknown";
    }

    /**
     * 从 Git 查询主要贡献者
     */
    private String queryGitOwner(String serviceName) {
        // 查询最近3个月的提交记录，找到提交次数最多的人
        // 实现略
        return null;
    }
}
```

### 4.3 处理人分配器

```java
@Service
public class AssigneeResolver {

    @Autowired
    private ServiceOwnerResolver serviceOwnerResolver;

    @Autowired
    private TicketRepository ticketRepository;

    /**
     * 智能分配处理人
     */
    public String resolve(AlertEvent alertEvent, ProblemClassification classification) {
        // 策略1: 如果该异常指纹有历史处理记录，分配给上次处理人
        String lastAssignee = findLastAssignee(alertEvent.getExceptionFingerprint());
        if (lastAssignee != null) {
            return lastAssignee;
        }

        // 策略2: 根据错误位置，找到代码最后修改人
        String codeOwner = findCodeOwner(alertEvent.getErrorLocation());
        if (codeOwner != null) {
            return codeOwner;
        }

        // 策略3: 分配给服务负责人
        return serviceOwnerResolver.resolve(alertEvent.getServiceName());
    }

    /**
     * 查找上次处理人
     */
    private String findLastAssignee(String fingerprint) {
        List<Ticket> historicalTickets = ticketRepository
            .findByExceptionFingerprintAndStatusIn(
                fingerprint,
                List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED)
            );

        if (!historicalTickets.isEmpty()) {
            return historicalTickets.get(0).getAssignee();
        }

        return null;
    }

    /**
     * 查找代码责任人
     */
    private String findCodeOwner(String errorLocation) {
        // 解析错误位置：com.example.Service.method:123
        // 查询 Git blame 找到该行代码的最后修改人
        // 实现略
        return null;
    }
}
```

---

## 5. 工单处理服务

### 5.1 工单操作服务

```java
@Service
public class TicketOperationService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketStateMachine stateMachine;

    @Autowired
    private TicketCollaborationRepository collaborationRepository;

    /**
     * 接受工单
     */
    @Transactional
    public void accept(Long ticketId, String operator) {
        Ticket ticket = getTicket(ticketId);
        stateMachine.transition(ticket, TicketStatus.ACCEPTED, operator, "接受工单");
        ticketRepository.save(ticket);
    }

    /**
     * 开始处理
     */
    @Transactional
    public void start(Long ticketId, String operator) {
        Ticket ticket = getTicket(ticketId);
        stateMachine.transition(ticket, TicketStatus.IN_PROGRESS, operator, "开始处理");
        ticket.setProgress(10);
        ticketRepository.save(ticket);
    }

    /**
     * 更新进度
     */
    @Transactional
    public void updateProgress(Long ticketId, int progress, String comment) {
        Ticket ticket = getTicket(ticketId);
        ticket.setProgress(Math.min(progress, 100));
        ticket.setRemark(comment);
        ticketRepository.save(ticket);
    }

    /**
     * 提交解决方案
     */
    @Transactional
    public void submitSolution(Long ticketId, String operator, TicketSolution solution) {
        Ticket ticket = getTicket(ticketId);

        ticket.setSolution(solution.getSolution());
        ticket.setSolutionType(solution.getType());
        ticket.setRootCause(solution.getRootCause());
        ticket.setCodeChanges(solution.getCodeChanges());
        ticket.setProgress(100);

        stateMachine.transition(ticket, TicketStatus.RESOLVED, operator, "提交解决方案");
        ticketRepository.save(ticket);

        // 异步：将解决方案加入知识库
        knowledgeService.indexSolution(ticket);
    }

    /**
     * 关闭工单
     */
    @Transactional
    public void close(Long ticketId, String operator, String comment) {
        Ticket ticket = getTicket(ticketId);
        stateMachine.transition(ticket, TicketStatus.CLOSED, operator, comment);

        // 检查是否超时
        if (ticket.getExpectedResolveTime() != null
            && ticket.getResolvedAt().isAfter(ticket.getExpectedResolveTime())) {
            ticket.setSlaBreached(true);
        }

        ticketRepository.save(ticket);
    }

    /**
     * 重新打开工单
     */
    @Transactional
    public void reopen(Long ticketId, String operator, String reason) {
        Ticket ticket = getTicket(ticketId);
        stateMachine.transition(ticket, TicketStatus.REOPENED, operator, reason);
        ticket.setProgress(0);
        ticketRepository.save(ticket);
    }

    /**
     * 添加协作记录
     */
    public void addCollaboration(Long ticketId, String participant,
                                 String role, String action, String content) {
        Ticket ticket = getTicket(ticketId);

        TicketCollaboration collab = TicketCollaboration.builder()
            .ticketId(ticketId)
            .ticketNo(ticket.getTicketNo())
            .participant(participant)
            .role(role)
            .action(action)
            .content(content)
            .build();

        collaborationRepository.save(collab);
    }

    private Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("工单不存在: " + ticketId));
    }
}
```

### 5.2 解决方案模型

```java
@Data
@Builder
public class TicketSolution {
    /**
     * 解决方案描述
     */
    private String solution;

    /**
     * 方案类型
     */
    private SolutionType type;

    /**
     * 确认的根因
     */
    private String rootCause;

    /**
     * 代码变更
     */
    private List<CodeChange> codeChanges;

    /**
     * 配置变更
     */
    private Map<String, String> configChanges;

    /**
     * 验证步骤
     */
    private List<String> verificationSteps;
}

public enum SolutionType {
    CODE_FIX("代码修复"),
    CONFIG_CHANGE("配置调整"),
    ROLLBACK("版本回滚"),
    SCALE_UP("扩容"),
    IGNORE("忽略（非问题）"),
    WORKAROUND("临时方案");

    private final String label;
}

@Data
public class CodeChange {
    private String filePath;
    private String className;
    private String methodName;
    private String changeDescription;
    private String commitId;
    private String pullRequestUrl;
}
```

---

## 6. 工单查询和统计

### 6.1 查询服务

```java
@Service
public class TicketQueryService {

    @Autowired
    private TicketRepository ticketRepository;

    /**
     * 分页查询工单
     */
    public Page<Ticket> query(TicketQueryRequest request, Pageable pageable) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getServiceName() != null) {
                predicates.add(cb.equal(root.get("serviceName"), request.getServiceName()));
            }

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }

            if (request.getAssignee() != null) {
                predicates.add(cb.equal(root.get("assignee"), request.getAssignee()));
            }

            if (request.getSeverity() != null) {
                predicates.add(cb.equal(root.get("severity"), request.getSeverity()));
            }

            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"), request.getStartTime()
                ));
            }

            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("createdAt"), request.getEndTime()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec, pageable);
    }

    /**
     * 我的待处理工单
     */
    public List<Ticket> myPendingTickets(String assignee) {
        return ticketRepository.findByAssigneeAndStatusIn(
            assignee,
            List.of(TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, TicketStatus.IN_PROGRESS)
        );
    }

    /**
     * 超时工单
     */
    public List<Ticket> overdueTickets() {
        return ticketRepository.findByExpectedResolveTimeBeforeAndStatusIn(
            Instant.now(),
            List.of(TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, TicketStatus.IN_PROGRESS)
        );
    }
}
```

### 6.2 统计分析服务

```java
@Service
public class TicketStatisticsService {

    @Autowired
    private TicketRepository ticketRepository;

    /**
     * 工单统计概览
     */
    public TicketStatistics getStatistics(String serviceName, LocalDate startDate, LocalDate endDate) {
        List<Ticket> tickets = ticketRepository.findByServiceNameAndCreatedAtBetween(
            serviceName,
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        );

        return TicketStatistics.builder()
            .totalCount(tickets.size())
            .pendingCount(countByStatus(tickets, TicketStatus.PENDING))
            .inProgressCount(countByStatus(tickets, TicketStatus.IN_PROGRESS))
            .resolvedCount(countByStatus(tickets, TicketStatus.RESOLVED))
            .closedCount(countByStatus(tickets, TicketStatus.CLOSED))
            .overdueCount(countOverdue(tickets))
            .averageResolveDuration(calculateAverageResolveDuration(tickets))
            .topProblemTypes(getTopProblemTypes(tickets, 10))
            .resolutionRate(calculateResolutionRate(tickets))
            .slaComplianceRate(calculateSlaComplianceRate(tickets))
            .build();
    }

    /**
     * 按服务统计
     */
    public List<ServiceTicketStats> statsByService(LocalDate startDate, LocalDate endDate) {
        // 使用原生 SQL 或 Spring Data JPA 聚合查询
        return ticketRepository.countByServiceGrouped(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        );
    }

    /**
     * 按处理人统计
     */
    public List<AssigneeStats> statsByAssignee(LocalDate startDate, LocalDate endDate) {
        return ticketRepository.countByAssigneeGrouped(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        );
    }

    /**
     * 趋势分析（按天统计）
     */
    public List<DailyTicketTrend> trendAnalysis(String serviceName, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<DailyTicketTrend> trends = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            long count = ticketRepository.countByServiceNameAndCreatedAtBetween(
                serviceName,
                date.atStartOfDay(),
                date.atTime(23, 59, 59)
            );
            trends.add(new DailyTicketTrend(date, count));
        }

        return trends;
    }

    private long countByStatus(List<Ticket> tickets, TicketStatus status) {
        return tickets.stream()
            .filter(t -> t.getStatus() == status)
            .count();
    }

    private long countOverdue(List<Ticket> tickets) {
        Instant now = Instant.now();
        return tickets.stream()
            .filter(t -> t.getExpectedResolveTime() != null)
            .filter(t -> !t.getStatus().equals(TicketStatus.CLOSED))
            .filter(t -> t.getExpectedResolveTime().isBefore(now))
            .count();
    }

    private double calculateAverageResolveDuration(List<Ticket> tickets) {
        return tickets.stream()
            .filter(t -> t.getActualResolveDuration() != null)
            .mapToInt(Ticket::getActualResolveDuration)
            .average()
            .orElse(0.0);
    }

    private List<ProblemTypeCount> getTopProblemTypes(List<Ticket> tickets, int limit) {
        return tickets.stream()
            .collect(Collectors.groupingBy(Ticket::getProblemType, Collectors.counting()))
            .entrySet().stream()
            .map(e -> new ProblemTypeCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(ProblemTypeCount::getCount).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    private double calculateResolutionRate(List<Ticket> tickets) {
        long totalCount = tickets.size();
        if (totalCount == 0) return 0.0;

        long resolvedCount = tickets.stream()
            .filter(t -> t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED)
            .count();

        return (double) resolvedCount / totalCount * 100;
    }

    private double calculateSlaComplianceRate(List<Ticket> tickets) {
        List<Ticket> closedTickets = tickets.stream()
            .filter(t -> t.getStatus() == TicketStatus.CLOSED)
            .toList();

        if (closedTickets.isEmpty()) return 100.0;

        long compliantCount = closedTickets.stream()
            .filter(t -> !t.isSlaBreached())
            .count();

        return (double) compliantCount / closedTickets.size() * 100;
    }
}
```

---

## 7. SLA 监控和告警

### 7.1 SLA 监控任务

```java
@Component
public class SlaMonitorTask {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * 每5分钟检查一次超时工单
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkOverdueTickets() {
        List<Ticket> overdueTickets = ticketRepository.findByExpectedResolveTimeBeforeAndStatusIn(
            Instant.now(),
            List.of(TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, TicketStatus.IN_PROGRESS)
        );

        for (Ticket ticket : overdueTickets) {
            if (!ticket.isSlaBreached()) {
                ticket.setSlaBreached(true);
                ticketRepository.save(ticket);

                // 发送超时告警
                notificationService.notifySlaBreached(ticket);
            }
        }
    }

    /**
     * 每小时检查即将超时的工单（提前预警）
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkUpcomingOverdue() {
        Instant oneHourLater = Instant.now().plus(Duration.ofHours(1));

        List<Ticket> upcomingTickets = ticketRepository.findByExpectedResolveTimeBetweenAndStatusIn(
            Instant.now(),
            oneHourLater,
            List.of(TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, TicketStatus.IN_PROGRESS)
        );

        for (Ticket ticket : upcomingTickets) {
            notificationService.notifyUpcomingOverdue(ticket);
        }
    }
}
```

---

## 8. 工单 API 设计

### 8.1 REST API

```java
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketQueryService queryService;

    @Autowired
    private TicketOperationService operationService;

    @Autowired
    private TicketStatisticsService statisticsService;

    /**
     * 查询工单列表
     */
    @GetMapping
    public Page<TicketVO> query(TicketQueryRequest request, Pageable pageable) {
        return queryService.query(request, pageable)
            .map(this::toVO);
    }

    /**
     * 获取工单详情
     */
    @GetMapping("/{id}")
    public TicketDetailVO get(@PathVariable Long id) {
        return operationService.getTicketDetail(id);
    }

    /**
     * 我的待处理工单
     */
    @GetMapping("/my-pending")
    public List<TicketVO> myPending(@RequestParam String assignee) {
        return queryService.myPendingTickets(assignee).stream()
            .map(this::toVO)
            .toList();
    }

    /**
     * 接受工单
     */
    @PostMapping("/{id}/accept")
    public void accept(@PathVariable Long id, @RequestParam String operator) {
        operationService.accept(id, operator);
    }

    /**
     * 开始处理
     */
    @PostMapping("/{id}/start")
    public void start(@PathVariable Long id, @RequestParam String operator) {
        operationService.start(id, operator);
    }

    /**
     * 更新进度
     */
    @PostMapping("/{id}/progress")
    public void updateProgress(@PathVariable Long id,
                               @RequestBody ProgressUpdateRequest request) {
        operationService.updateProgress(id, request.getProgress(), request.getComment());
    }

    /**
     * 提交解决方案
     */
    @PostMapping("/{id}/solution")
    public void submitSolution(@PathVariable Long id,
                               @RequestParam String operator,
                               @RequestBody TicketSolution solution) {
        operationService.submitSolution(id, operator, solution);
    }

    /**
     * 关闭工单
     */
    @PostMapping("/{id}/close")
    public void close(@PathVariable Long id,
                      @RequestParam String operator,
                      @RequestParam String comment) {
        operationService.close(id, operator, comment);
    }

    /**
     * 重新打开
     */
    @PostMapping("/{id}/reopen")
    public void reopen(@PathVariable Long id,
                       @RequestParam String operator,
                       @RequestParam String reason) {
        operationService.reopen(id, operator, reason);
    }

    /**
     * 工单统计
     */
    @GetMapping("/statistics")
    public TicketStatistics statistics(@RequestParam String serviceName,
                                       @RequestParam LocalDate startDate,
                                       @RequestParam LocalDate endDate) {
        return statisticsService.getStatistics(serviceName, startDate, endDate);
    }

    /**
     * 趋势分析
     */
    @GetMapping("/trend")
    public List<DailyTicketTrend> trend(@RequestParam String serviceName,
                                       @RequestParam(defaultValue = "30") int days) {
        return statisticsService.trendAnalysis(serviceName, days);
    }
}
```

---

## 9. 通知集成

### 9.1 通知服务

```java
@Service
public class NotificationService {

    @Autowired
    private DingTalkNotifier dingTalkNotifier;

    @Autowired
    private EmailNotifier emailNotifier;

    @Autowired
    private WebSocketNotifier webSocketNotifier;

    /**
     * 工单分派通知
     */
    public void notifyAssignment(Ticket ticket, String assignee) {
        String message = String.format(
            "您有新的工单待处理：\n" +
            "工单编号：%s\n" +
            "严重级别：%s\n" +
            "服务名称：%s\n" +
            "问题类型：%s\n" +
            "期望解决：%s",
            ticket.getTicketNo(),
            ticket.getSeverity(),
            ticket.getServiceName(),
            ticket.getProblemType(),
            ticket.getExpectedResolveTime()
        );

        dingTalkNotifier.sendToUser(assignee, message, ticket.getId());
        emailNotifier.send(assignee, "新工单分派", message);
        webSocketNotifier.push(assignee, "ticket.assigned", ticket);
    }

    /**
     * SLA 超时通知
     */
    public void notifySlaBreached(Ticket ticket) {
        String message = String.format(
            "工单已超时：\n" +
            "工单编号：%s\n" +
            "严重级别：%s\n" +
            "处理人：%s\n" +
            "期望解决：%s\n" +
            "当前时间：%s",
            ticket.getTicketNo(),
            ticket.getSeverity(),
            ticket.getAssignee(),
            ticket.getExpectedResolveTime(),
            Instant.now()
        );

        // 通知处理人和负责人
        dingTalkNotifier.sendToUser(ticket.getAssignee(), message, ticket.getId());
        dingTalkNotifier.sendToUser(ticket.getServiceOwner(), message, ticket.getId());
    }

    /**
     * 即将超时预警
     */
    public void notifyUpcomingOverdue(Ticket ticket) {
        String message = String.format(
            "工单即将超时（1小时内）：\n" +
            "工单编号：%s\n" +
            "期望解决：%s",
            ticket.getTicketNo(),
            ticket.getExpectedResolveTime()
        );

        dingTalkNotifier.sendToUser(ticket.getAssignee(), message, ticket.getId());
    }
}
```

---

## 10. 配置示例

```yaml
# application.yml
one-agent:
  ticket:
    # 工单编号前缀
    number-prefix: TICK

    # SLA 配置（分钟）
    sla:
      p0: 30
      p1: 120
      p2: 480
      p3: 1440
      p4: 4320

    # 自动分派
    auto-assign:
      enabled: true
      # 分派策略：LAST_ASSIGNEE / CODE_OWNER / SERVICE_OWNER
      strategy: LAST_ASSIGNEE

    # 通知配置
    notification:
      enabled: true
      channels:
        - dingtalk
        - email
        - websocket

      # 通知时机
      events:
        - ASSIGNED
        - SLA_BREACHED
        - UPCOMING_OVERDUE
        - RESOLVED
```

---

## 11. 总结

### 核心特性

1. **自动化工单生成**：告警降噪后自动生成结构化工单
2. **智能分派**：基于历史记录、代码归属、服务负责人的智能分派
3. **完整状态机**：严格的工单生命周期管理
4. **AI 辅助**：根因分析、解决方案推荐、相似案例检索
5. **SLA 监控**：自动超时检测和预警
6. **知识沉淀**：处理方案自动入库，形成知识积累
7. **多维统计**：服务、人员、趋势等多维度分析

### 预期效果

- **工单处理效率提升 60%**：通过 AI 分析和历史案例推荐
- **SLA 达成率 > 95%**：通过自动监控和预警
- **知识复用率 > 80%**：相似问题直接复用历史方案
- **人工介入减少 70%**：自动分派、自动分析、自动通知
