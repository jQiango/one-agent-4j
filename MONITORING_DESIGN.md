# 系统监控和告警规则设计文档

## 1. 概述

完善的监控体系是保障智能服务治理平台稳定运行的基础。本文档设计了多维度监控指标、告警规则、可观测性方案,确保系统健康运行和问题快速定位。

### 监控目标

- **系统健康**:实时监控系统各组件运行状态
- **性能指标**:跟踪响应时间、吞吐量、资源使用率
- **业务指标**:监控告警处理效率、工单状态、AI 分析质量
- **成本优化**:监控 LLM Token 消耗、存储使用量
- **故障预警**:提前发现潜在问题,主动预防

---

## 2. 监控架构

```
┌─────────────────────────────────────────────────────────┐
│                    应用层                                 │
│  Spring Boot Actuator + Micrometer + Custom Metrics    │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   采集层                                  │
│  Prometheus (指标)  │  ELK (日志)  │  Jaeger (链路)     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   存储层                                  │
│  Prometheus TSDB  │  Elasticsearch  │  Jaeger Storage  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   展示层                                  │
│  Grafana (可视化)  │  Kibana (日志)  │  Jaeger UI       │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   告警层                                  │
│  Alertmanager  │  DingTalk  │  Email  │  SMS           │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 指标体系设计

### 3.1 系统基础指标

#### JVM 指标

```java
@Configuration
public class JvmMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            "application", "one-agent-4j",
            "environment", System.getenv("ENV")
        );
    }

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }
}
```

**关键指标**:
- `jvm_memory_used_bytes`: JVM 内存使用量
- `jvm_memory_max_bytes`: JVM 最大内存
- `jvm_gc_pause_seconds`: GC 停顿时间
- `jvm_threads_live_threads`: 活跃线程数
- `jvm_threads_daemon_threads`: 守护线程数
- `jvm_classes_loaded_classes`: 已加载类数量

#### HTTP 请求指标

```yaml
management:
  metrics:
    enable:
      http: true
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
```

**关键指标**:
- `http_server_requests_seconds`: HTTP 请求响应时间分布
- `http_server_requests_seconds_count`: HTTP 请求总数
- `http_server_requests_seconds_sum`: HTTP 请求总耗时

**维度**:
- `method`: GET/POST/PUT/DELETE
- `status`: 200/400/500
- `uri`: /api/tickets, /api/agent/chat
- `exception`: 异常类型

#### 数据库连接池指标

```java
@Configuration
public class DataSourceMetricsConfig {

    @Bean
    public DataSourcePoolMetrics dataSourcePoolMetrics(DataSource dataSource) {
        return new DataSourcePoolMetrics(dataSource, "hikari", Tags.empty());
    }
}
```

**关键指标**:
- `hikaricp_connections_active`: 活跃连接数
- `hikaricp_connections_idle`: 空闲连接数
- `hikaricp_connections_pending`: 等待连接数
- `hikaricp_connections_timeout_total`: 连接超时次数
- `hikaricp_connections_creation_seconds`: 连接创建耗时

---

### 3.2 业务指标

#### 告警处理指标

```java
@Service
public class AlertMetricsService {

    private final Counter alertReceived;
    private final Counter alertFiltered;
    private final Counter alertDeduped;
    private final Timer alertProcessTime;
    private final Gauge alertPendingCount;

    public AlertMetricsService(MeterRegistry registry) {
        this.alertReceived = Counter.builder("alert.received")
            .description("告警接收总数")
            .tag("service", "all")
            .register(registry);

        this.alertFiltered = Counter.builder("alert.filtered")
            .description("告警过滤数")
            .tag("filter_type", "environment")
            .register(registry);

        this.alertDeduped = Counter.builder("alert.deduped")
            .description("告警去重数")
            .register(registry);

        this.alertProcessTime = Timer.builder("alert.process.time")
            .description("告警处理耗时")
            .register(registry);

        this.alertPendingCount = Gauge.builder("alert.pending.count", this::getPendingCount)
            .description("待处理告警数")
            .register(registry);
    }

    public void recordAlertReceived(String service) {
        alertReceived.increment();
    }

    public void recordAlertFiltered(String filterType) {
        alertFiltered.increment();
    }

    public void recordAlertDeduped() {
        alertDeduped.increment();
    }

    public void recordAlertProcessTime(Runnable task) {
        alertProcessTime.record(task);
    }

    private int getPendingCount() {
        // 查询待处理告警数
        return 0;
    }
}
```

**关键指标**:
- `alert.received`: 接收告警总数
- `alert.filtered`: 过滤告警数(按过滤类型分组)
- `alert.deduped`: 去重告警数
- `alert.process.time`: 告警处理耗时
- `alert.pending.count`: 待处理告警数
- `alert.severity.distribution`: 告警严重级别分布(P0-P4)

**Prometheus 查询示例**:
```promql
# 告警降噪率
(1 - alert_filtered_total / alert_received_total) * 100

# 告警处理 P99 延迟
histogram_quantile(0.99, rate(alert_process_time_seconds_bucket[5m]))

# 告警接收速率(每分钟)
rate(alert_received_total[1m]) * 60
```

#### 工单管理指标

```java
@Service
public class TicketMetricsService {

    private final Counter ticketCreated;
    private final Counter ticketResolved;
    private final Counter ticketClosed;
    private final Timer ticketResolveTime;
    private final Gauge ticketByStatus;
    private final Counter ticketSlaBreached;

    public TicketMetricsService(MeterRegistry registry) {
        this.ticketCreated = Counter.builder("ticket.created")
            .description("工单创建总数")
            .tag("service", "all")
            .register(registry);

        this.ticketResolved = Counter.builder("ticket.resolved")
            .description("工单解决总数")
            .tag("solution_type", "code_fix")
            .register(registry);

        this.ticketClosed = Counter.builder("ticket.closed")
            .description("工单关闭总数")
            .register(registry);

        this.ticketResolveTime = Timer.builder("ticket.resolve.time")
            .description("工单解决耗时")
            .tag("severity", "P1")
            .register(registry);

        this.ticketByStatus = Gauge.builder("ticket.by.status",
                Tags.of("status", "PENDING"), this::getTicketCount)
            .description("各状态工单数")
            .register(registry);

        this.ticketSlaBreached = Counter.builder("ticket.sla.breached")
            .description("SLA 超时工单数")
            .tag("severity", "P1")
            .register(registry);
    }

    public void recordTicketCreated(String service, String severity) {
        ticketCreated.increment();
    }

    public void recordTicketResolved(String solutionType, long durationMinutes) {
        ticketResolved.increment();
        ticketResolveTime.record(Duration.ofMinutes(durationMinutes));
    }

    public void recordTicketClosed() {
        ticketClosed.increment();
    }

    public void recordSlaBreached(String severity) {
        ticketSlaBreached.increment();
    }

    private double getTicketCount(Tags tags) {
        // 查询指定状态的工单数
        return 0.0;
    }
}
```

**关键指标**:
- `ticket.created`: 工单创建数
- `ticket.resolved`: 工单解决数
- `ticket.closed`: 工单关闭数
- `ticket.resolve.time`: 工单解决耗时(按严重级别)
- `ticket.by.status`: 各状态工单数
- `ticket.sla.breached`: SLA 超时工单数
- `ticket.resolution.rate`: 工单解决率

**Prometheus 查询示例**:
```promql
# 工单解决率
ticket_resolved_total / ticket_created_total * 100

# SLA 达成率
(1 - ticket_sla_breached_total / ticket_closed_total) * 100

# 平均解决时间
rate(ticket_resolve_time_seconds_sum[1h]) / rate(ticket_resolve_time_seconds_count[1h]) / 60
```

#### AI Agent 指标

```java
@Service
public class AgentMetricsService {

    private final Counter conversationCreated;
    private final Counter messageProcessed;
    private final Timer messageProcessTime;
    private final Counter toolCalled;
    private final Counter llmCalled;
    private final Timer llmResponseTime;
    private final Counter llmTokens;
    private final Gauge activeConversations;

    public AgentMetricsService(MeterRegistry registry) {
        this.conversationCreated = Counter.builder("agent.conversation.created")
            .description("会话创建数")
            .tag("channel", "web")
            .register(registry);

        this.messageProcessed = Counter.builder("agent.message.processed")
            .description("消息处理数")
            .tag("intent", "query_ticket")
            .register(registry);

        this.messageProcessTime = Timer.builder("agent.message.process.time")
            .description("消息处理耗时")
            .register(registry);

        this.toolCalled = Counter.builder("agent.tool.called")
            .description("工具调用次数")
            .tag("tool", "query_ticket")
            .register(registry);

        this.llmCalled = Counter.builder("agent.llm.called")
            .description("LLM 调用次数")
            .tag("model", "deepseek-v3")
            .register(registry);

        this.llmResponseTime = Timer.builder("agent.llm.response.time")
            .description("LLM 响应时间")
            .register(registry);

        this.llmTokens = Counter.builder("agent.llm.tokens")
            .description("LLM Token 消耗")
            .tag("type", "input")
            .register(registry);

        this.activeConversations = Gauge.builder("agent.conversations.active",
                this::getActiveConversationCount)
            .description("活跃会话数")
            .register(registry);
    }

    public void recordConversationCreated(String channel) {
        conversationCreated.increment();
    }

    public void recordMessageProcessed(String intent, long durationMs) {
        messageProcessed.increment();
        messageProcessTime.record(Duration.ofMillis(durationMs));
    }

    public void recordToolCalled(String toolName) {
        toolCalled.increment();
    }

    public void recordLlmCall(String model, long responseTimeMs, int inputTokens, int outputTokens) {
        llmCalled.increment();
        llmResponseTime.record(Duration.ofMillis(responseTimeMs));
        llmTokens.increment(inputTokens);
        Counter.builder("agent.llm.tokens")
            .tag("type", "output")
            .register(registry)
            .increment(outputTokens);
    }

    private double getActiveConversationCount() {
        // 查询活跃会话数
        return 0.0;
    }
}
```

**关键指标**:
- `agent.conversation.created`: 会话创建数
- `agent.message.processed`: 消息处理数(按意图分组)
- `agent.message.process.time`: 消息处理耗时
- `agent.tool.called`: 工具调用次数(按工具分组)
- `agent.llm.called`: LLM 调用次数
- `agent.llm.response.time`: LLM 响应时间
- `agent.llm.tokens`: Token 消耗(输入/输出)
- `agent.conversations.active`: 活跃会话数

**Prometheus 查询示例**:
```promql
# LLM 调用成功率
rate(agent_llm_called_total{status="success"}[5m]) / rate(agent_llm_called_total[5m]) * 100

# 每日 Token 消耗
sum(increase(agent_llm_tokens_total[1d]))

# LLM P95 响应时间
histogram_quantile(0.95, rate(agent_llm_response_time_seconds_bucket[5m]))
```

#### RAG 检索指标

```java
@Service
public class RagMetricsService {

    private final Counter retrievalPerformed;
    private final Timer retrievalTime;
    private final Gauge retrievalResultCount;
    private final Counter retrievalCacheHit;
    private final Timer embeddingTime;

    public RagMetricsService(MeterRegistry registry) {
        this.retrievalPerformed = Counter.builder("rag.retrieval.performed")
            .description("检索次数")
            .tag("type", "vector")
            .register(registry);

        this.retrievalTime = Timer.builder("rag.retrieval.time")
            .description("检索耗时")
            .register(registry);

        this.retrievalResultCount = Gauge.builder("rag.retrieval.result.count",
                Tags.empty(), this::getAverageResultCount)
            .description("平均检索结果数")
            .register(registry);

        this.retrievalCacheHit = Counter.builder("rag.retrieval.cache.hit")
            .description("检索缓存命中数")
            .register(registry);

        this.embeddingTime = Timer.builder("rag.embedding.time")
            .description("向量化耗时")
            .register(registry);
    }

    public void recordRetrieval(String type, long durationMs, int resultCount) {
        retrievalPerformed.increment();
        retrievalTime.record(Duration.ofMillis(durationMs));
    }

    public void recordCacheHit() {
        retrievalCacheHit.increment();
    }

    public void recordEmbedding(long durationMs) {
        embeddingTime.record(Duration.ofMillis(durationMs));
    }

    private double getAverageResultCount() {
        return 0.0;
    }
}
```

**关键指标**:
- `rag.retrieval.performed`: 检索次数(向量/关键词)
- `rag.retrieval.time`: 检索耗时
- `rag.retrieval.result.count`: 平均检索结果数
- `rag.retrieval.cache.hit`: 缓存命中数
- `rag.embedding.time`: 向量化耗时

---

### 3.3 依赖服务指标

#### Redis 指标

```yaml
management:
  metrics:
    export:
      redis:
        enabled: true
```

**关键指标**:
- `redis_commands_total`: Redis 命令执行次数
- `redis_commands_duration_seconds`: Redis 命令耗时
- `redis_connection_pool_active`: 活跃连接数
- `redis_connection_pool_idle`: 空闲连接数

#### MQ 指标

```java
@Configuration
public class RabbitMqMetricsConfig {

    @Bean
    public RabbitMqMetrics rabbitMqMetrics(RabbitTemplate rabbitTemplate) {
        return new RabbitMqMetrics(rabbitTemplate.getConnectionFactory(), Tags.empty());
    }
}
```

**关键指标**:
- `rabbitmq_published_total`: 消息发布数
- `rabbitmq_consumed_total`: 消息消费数
- `rabbitmq_unacked_messages`: 未确认消息数
- `rabbitmq_consumer_count`: 消费者数量

#### Milvus 向量数据库指标

```java
@Service
public class MilvusMetricsService {

    private final Counter vectorInserted;
    private final Counter vectorSearched;
    private final Timer vectorSearchTime;
    private final Gauge vectorCollectionSize;

    public MilvusMetricsService(MeterRegistry registry) {
        this.vectorInserted = Counter.builder("milvus.vector.inserted")
            .description("向量插入数")
            .register(registry);

        this.vectorSearched = Counter.builder("milvus.vector.searched")
            .description("向量搜索次数")
            .register(registry);

        this.vectorSearchTime = Timer.builder("milvus.vector.search.time")
            .description("向量搜索耗时")
            .register(registry);

        this.vectorCollectionSize = Gauge.builder("milvus.collection.size",
                this::getCollectionSize)
            .description("向量集合大小")
            .register(registry);
    }

    public void recordVectorInserted(int count) {
        vectorInserted.increment(count);
    }

    public void recordVectorSearch(long durationMs) {
        vectorSearched.increment();
        vectorSearchTime.record(Duration.ofMillis(durationMs));
    }

    private double getCollectionSize() {
        // 查询 Milvus 集合大小
        return 0.0;
    }
}
```

---

## 4. 告警规则设计

### 4.1 系统告警规则

#### JVM 内存告警

```yaml
# prometheus/alerts/jvm.yml
groups:
  - name: jvm_alerts
    interval: 30s
    rules:
      # JVM 堆内存使用率超过 85%
      - alert: JvmHeapMemoryHigh
        expr: |
          (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.85
        for: 5m
        labels:
          severity: warning
          component: jvm
        annotations:
          summary: "JVM 堆内存使用率过高"
          description: "{{ $labels.application }} 堆内存使用率 {{ $value | humanizePercentage }}"

      # JVM 堆内存使用率超过 95%
      - alert: JvmHeapMemoryCritical
        expr: |
          (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.95
        for: 2m
        labels:
          severity: critical
          component: jvm
        annotations:
          summary: "JVM 堆内存使用率严重"
          description: "{{ $labels.application }} 堆内存使用率 {{ $value | humanizePercentage }}"

      # GC 停顿时间过长
      - alert: JvmGcPauseTooLong
        expr: |
          rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m]) > 1.0
        for: 5m
        labels:
          severity: warning
          component: jvm
        annotations:
          summary: "GC 停顿时间过长"
          description: "{{ $labels.application }} 平均 GC 停顿时间 {{ $value }}s"
```

#### HTTP 请求告警

```yaml
# prometheus/alerts/http.yml
groups:
  - name: http_alerts
    interval: 30s
    rules:
      # HTTP 5xx 错误率超过 1%
      - alert: HighHttp5xxErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
          sum(rate(http_server_requests_seconds_count[5m])) > 0.01
        for: 5m
        labels:
          severity: warning
          component: http
        annotations:
          summary: "HTTP 5xx 错误率过高"
          description: "HTTP 5xx 错误率 {{ $value | humanizePercentage }}"

      # API 响应时间 P99 超过 3s
      - alert: HighApiLatency
        expr: |
          histogram_quantile(0.99,
            rate(http_server_requests_seconds_bucket[5m])) > 3.0
        for: 5m
        labels:
          severity: warning
          component: http
        annotations:
          summary: "API 响应时间过长"
          description: "API P99 延迟 {{ $value }}s"

      # HTTP 请求 QPS 异常低
      - alert: LowHttpRequestQps
        expr: |
          rate(http_server_requests_seconds_count[5m]) < 1
        for: 10m
        labels:
          severity: warning
          component: http
        annotations:
          summary: "HTTP 请求 QPS 异常低"
          description: "HTTP QPS {{ $value }}/s，可能服务异常"
```

#### 数据库连接池告警

```yaml
# prometheus/alerts/database.yml
groups:
  - name: database_alerts
    interval: 30s
    rules:
      # 数据库连接池耗尽
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_active >= hikaricp_connections_max * 0.9
        for: 2m
        labels:
          severity: critical
          component: database
        annotations:
          summary: "数据库连接池即将耗尽"
          description: "活跃连接 {{ $value }}"

      # 连接等待时间过长
      - alert: DatabaseConnectionWaitTooLong
        expr: |
          rate(hikaricp_connections_timeout_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          component: database
        annotations:
          summary: "数据库连接等待超时"
          description: "连接超时率 {{ $value }}/s"
```

---

### 4.2 业务告警规则

#### 告警处理告警

```yaml
# prometheus/alerts/alert_processing.yml
groups:
  - name: alert_processing_alerts
    interval: 30s
    rules:
      # 告警积压过多
      - alert: AlertBacklogTooHigh
        expr: |
          alert_pending_count > 100
        for: 5m
        labels:
          severity: warning
          component: alert_processing
        annotations:
          summary: "告警积压过多"
          description: "待处理告警 {{ $value }} 个"

      # 告警处理延迟过高
      - alert: AlertProcessingLatencyHigh
        expr: |
          histogram_quantile(0.95,
            rate(alert_process_time_seconds_bucket[5m])) > 10.0
        for: 5m
        labels:
          severity: warning
          component: alert_processing
        annotations:
          summary: "告警处理延迟过高"
          description: "告警处理 P95 延迟 {{ $value }}s"

      # 告警降噪率异常低
      - alert: AlertDenoiseRateLow
        expr: |
          (1 - alert_filtered_total / alert_received_total) < 0.5
        for: 10m
        labels:
          severity: info
          component: alert_processing
        annotations:
          summary: "告警降噪率异常低"
          description: "降噪率 {{ $value | humanizePercentage }}，可能配置问题"
```

#### 工单管理告警

```yaml
# prometheus/alerts/ticket.yml
groups:
  - name: ticket_alerts
    interval: 1m
    rules:
      # P0 工单未及时处理
      - alert: P0TicketUnhandled
        expr: |
          ticket_by_status{severity="P0", status="PENDING"} > 0
        for: 10m
        labels:
          severity: critical
          component: ticket
        annotations:
          summary: "P0 工单未及时处理"
          description: "有 {{ $value }} 个 P0 工单待处理"

      # SLA 超时率过高
      - alert: HighSlaBreachRate
        expr: |
          rate(ticket_sla_breached_total[1h]) /
          rate(ticket_closed_total[1h]) > 0.1
        for: 1h
        labels:
          severity: warning
          component: ticket
        annotations:
          summary: "SLA 超时率过高"
          description: "SLA 超时率 {{ $value | humanizePercentage }}"

      # 工单积压过多
      - alert: TicketBacklogTooHigh
        expr: |
          sum(ticket_by_status{status=~"PENDING|ASSIGNED|IN_PROGRESS"}) > 50
        for: 1h
        labels:
          severity: warning
          component: ticket
        annotations:
          summary: "工单积压过多"
          description: "待处理工单 {{ $value }} 个"
```

#### AI Agent 告警

```yaml
# prometheus/alerts/agent.yml
groups:
  - name: agent_alerts
    interval: 30s
    rules:
      # LLM 调用失败率过高
      - alert: HighLlmFailureRate
        expr: |
          rate(agent_llm_called_total{status="error"}[5m]) /
          rate(agent_llm_called_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
          component: agent
        annotations:
          summary: "LLM 调用失败率过高"
          description: "LLM 失败率 {{ $value | humanizePercentage }}"

      # LLM 响应时间过长
      - alert: LlmResponseTimeTooLong
        expr: |
          histogram_quantile(0.95,
            rate(agent_llm_response_time_seconds_bucket[5m])) > 10.0
        for: 5m
        labels:
          severity: warning
          component: agent
        annotations:
          summary: "LLM 响应时间过长"
          description: "LLM P95 响应时间 {{ $value }}s"

      # Token 消耗异常高
      - alert: HighTokenConsumption
        expr: |
          rate(agent_llm_tokens_total[1h]) > 1000000
        for: 1h
        labels:
          severity: info
          component: agent
        annotations:
          summary: "Token 消耗异常高"
          description: "每小时消耗 {{ $value }} tokens"

      # RAG 检索失败率过高
      - alert: HighRagRetrievalFailureRate
        expr: |
          rate(rag_retrieval_performed_total{status="error"}[5m]) /
          rate(rag_retrieval_performed_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          component: agent
        annotations:
          summary: "RAG 检索失败率过高"
          description: "检索失败率 {{ $value | humanizePercentage }}"
```

---

### 4.3 依赖服务告警

#### Redis 告警

```yaml
# prometheus/alerts/redis.yml
groups:
  - name: redis_alerts
    interval: 30s
    rules:
      # Redis 连接失败
      - alert: RedisConnectionFailed
        expr: |
          redis_up == 0
        for: 2m
        labels:
          severity: critical
          component: redis
        annotations:
          summary: "Redis 连接失败"
          description: "Redis 不可用"

      # Redis 命令执行延迟过高
      - alert: RedisHighLatency
        expr: |
          rate(redis_commands_duration_seconds_sum[5m]) /
          rate(redis_commands_duration_seconds_count[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          component: redis
        annotations:
          summary: "Redis 命令延迟过高"
          description: "平均延迟 {{ $value }}s"
```

#### MQ 告警

```yaml
# prometheus/alerts/rabbitmq.yml
groups:
  - name: rabbitmq_alerts
    interval: 30s
    rules:
      # 消息积压过多
      - alert: RabbitMqMessageBacklog
        expr: |
          rabbitmq_unacked_messages > 1000
        for: 10m
        labels:
          severity: warning
          component: rabbitmq
        annotations:
          summary: "RabbitMQ 消息积压"
          description: "未确认消息 {{ $value }} 个"

      # 没有消费者
      - alert: RabbitMqNoConsumers
        expr: |
          rabbitmq_consumer_count == 0
        for: 5m
        labels:
          severity: critical
          component: rabbitmq
        annotations:
          summary: "RabbitMQ 没有消费者"
          description: "队列 {{ $labels.queue }} 没有消费者"
```

---

## 5. Grafana 仪表盘设计

### 5.1 系统概览仪表盘

```json
{
  "dashboard": {
    "title": "One Agent 4J - 系统概览",
    "panels": [
      {
        "title": "服务健康状态",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job='one-agent-4j'}",
            "legendFormat": "{{ instance }}"
          }
        ]
      },
      {
        "title": "HTTP QPS",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[1m])",
            "legendFormat": "{{ method }} {{ uri }}"
          }
        ]
      },
      {
        "title": "HTTP P95 延迟",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "{{ uri }}"
          }
        ]
      },
      {
        "title": "JVM 堆内存使用率",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{area='heap'} / jvm_memory_max_bytes{area='heap'} * 100"
          }
        ]
      }
    ]
  }
}
```

### 5.2 业务监控仪表盘

```json
{
  "dashboard": {
    "title": "One Agent 4J - 业务监控",
    "panels": [
      {
        "title": "告警接收速率",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(alert_received_total[5m]) * 60",
            "legendFormat": "{{ service }}"
          }
        ]
      },
      {
        "title": "告警降噪率",
        "type": "stat",
        "targets": [
          {
            "expr": "(1 - sum(rate(alert_filtered_total[1h])) / sum(rate(alert_received_total[1h]))) * 100"
          }
        ]
      },
      {
        "title": "工单状态分布",
        "type": "pie",
        "targets": [
          {
            "expr": "ticket_by_status",
            "legendFormat": "{{ status }}"
          }
        ]
      },
      {
        "title": "工单解决率",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(rate(ticket_resolved_total[24h])) / sum(rate(ticket_created_total[24h])) * 100"
          }
        ]
      },
      {
        "title": "SLA 达成率",
        "type": "gauge",
        "targets": [
          {
            "expr": "(1 - sum(rate(ticket_sla_breached_total[24h])) / sum(rate(ticket_closed_total[24h]))) * 100"
          }
        ]
      }
    ]
  }
}
```

### 5.3 AI Agent 监控仪表盘

```json
{
  "dashboard": {
    "title": "One Agent 4J - AI Agent",
    "panels": [
      {
        "title": "LLM 调用 QPS",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(agent_llm_called_total[1m]) * 60",
            "legendFormat": "{{ model }}"
          }
        ]
      },
      {
        "title": "LLM 响应时间分布",
        "type": "heatmap",
        "targets": [
          {
            "expr": "rate(agent_llm_response_time_seconds_bucket[5m])"
          }
        ]
      },
      {
        "title": "Token 消耗趋势",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(agent_llm_tokens_total[1h]) * 3600",
            "legendFormat": "{{ type }}"
          }
        ]
      },
      {
        "title": "工具调用统计",
        "type": "bar",
        "targets": [
          {
            "expr": "sum by(tool) (rate(agent_tool_called_total[1h]))",
            "legendFormat": "{{ tool }}"
          }
        ]
      },
      {
        "title": "RAG 检索性能",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(rag_retrieval_time_seconds_bucket[5m]))",
            "legendFormat": "P95"
          }
        ]
      }
    ]
  }
}
```

---

## 6. 日志规范

### 6.1 日志级别

- **ERROR**: 系统错误,需要立即处理
- **WARN**: 警告信息,需要关注
- **INFO**: 关键业务流程
- **DEBUG**: 详细调试信息
- **TRACE**: 最详细的跟踪信息

### 6.2 日志格式

```java
@Slf4j
@Service
public class AlertProcessingService {

    public void processAlert(AlertEvent alert) {
        log.info("开始处理告警 - alertId={}, service={}, type={}",
            alert.getId(),
            alert.getServiceName(),
            alert.getExceptionType()
        );

        try {
            // 处理逻辑
            log.debug("告警降噪完成 - alertId={}, filtered={}, deduped={}",
                alert.getId(),
                alert.isFiltered(),
                alert.isDeduped()
            );

        } catch (Exception e) {
            log.error("告警处理失败 - alertId={}, error={}",
                alert.getId(),
                e.getMessage(),
                e
            );
            throw e;
        }

        log.info("告警处理完成 - alertId={}, ticketNo={}, duration={}ms",
            alert.getId(),
            alert.getTicketNo(),
            alert.getProcessDuration()
        );
    }
}
```

### 6.3 结构化日志 (JSON)

```yaml
logging:
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%level","thread":"%thread","logger":"%logger{40}","message":"%message","exception":"%exception{5}"}%n'
```

---

## 7. 链路追踪

### 7.1 集成 Spring Cloud Sleuth

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

```yaml
spring:
  sleuth:
    sampler:
      probability: 1.0  # 采样率 100%
  zipkin:
    base-url: http://localhost:9411
    sender:
      type: web
```

### 7.2 自定义 Span

```java
@Service
public class TicketService {

    @Autowired
    private Tracer tracer;

    public Ticket createTicket(AlertEvent alert) {
        Span span = tracer.nextSpan().name("create-ticket").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("service", alert.getServiceName());
            span.tag("severity", alert.getSeverity());

            Ticket ticket = doCreateTicket(alert);

            span.tag("ticket_no", ticket.getTicketNo());
            span.event("ticket-created");

            return ticket;
        } finally {
            span.end();
        }
    }
}
```

---

## 8. 配置示例

### 8.1 Prometheus 配置

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'one-agent-cluster'
    env: 'production'

scrape_configs:
  - job_name: 'one-agent-4j'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
        labels:
          application: 'one-agent-4j'

  - job_name: 'redis'
    static_configs:
      - targets: ['localhost:9121']

  - job_name: 'rabbitmq'
    static_configs:
      - targets: ['localhost:15692']

rule_files:
  - 'alerts/jvm.yml'
  - 'alerts/http.yml'
  - 'alerts/database.yml'
  - 'alerts/alert_processing.yml'
  - 'alerts/ticket.yml'
  - 'alerts/agent.yml'

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['localhost:9093']
```

### 8.2 Alertmanager 配置

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'dingtalk-critical'
    - match:
        severity: warning
      receiver: 'dingtalk-warning'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:8080/api/alertmanager/webhook'

  - name: 'dingtalk-critical'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=xxx'
        send_resolved: true

  - name: 'dingtalk-warning'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=yyy'
        send_resolved: true
```

---

## 9. 总结

### 监控指标体系

- **系统指标**: JVM、HTTP、数据库、缓存、MQ
- **业务指标**: 告警处理、工单管理、AI Agent、RAG 检索
- **依赖指标**: Redis、RabbitMQ、Milvus、Elasticsearch

### 告警规则体系

- **系统告警**: 内存、GC、HTTP 错误、数据库连接池
- **业务告警**: 告警积压、工单 SLA、LLM 失败率
- **依赖告警**: Redis 连接、MQ 积压

### 可观测性方案

- **指标**: Prometheus + Grafana
- **日志**: ELK Stack
- **链路**: Jaeger/Zipkin
- **告警**: Alertmanager + DingTalk

### 预期效果

- **故障发现时间 < 1min**: 实时告警
- **问题定位时间 < 5min**: 链路追踪 + 日志
- **系统可用性 > 99.9%**: 完善的监控和告警
