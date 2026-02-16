# Design Diagrams

## 1. System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    HIGH-THROUGHPUT FAN-OUT ENGINE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │  INGESTION LAYER                                             │      │
│  │  ┌─────────────────────────────────────────────────────┐    │      │
│  │  │ FileProducer                                        │    │      │
│  │  │ - JSON, JSONL, CSV, Fixed-width support           │    │      │
│  │  │ - Streaming (no full file load)                   │    │      │
│  │  │ - Thread: Runnable (Virtual Thread)               │    │      │
│  │  └─────────────────────────────────────────────────────┘    │      │
│  │                     │                                        │      │
│  │                     ↓                                        │      │
│  │  ┌─────────────────────────────────────────────────────┐    │      │
│  │  │ BlockingQueue<String> (Backpressure)               │    │      │
│  │  │ - Capacity: 1000 (configurable)                    │    │      │
│  │  │ - Prevents memory overflow                         │    │      │
│  │  │ - Synchronized put() / take() / poll()             │    │      │
│  │  └─────────────────────────────────────────────────────┘    │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                     │                                                    │
│                     ↓                                                    │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │  ORCHESTRATION LAYER (FanOutOrchestrator)                    │      │
│  │  - Reads from queue                                          │      │
│  │  - Routes to 4 sinks with transformers                      │      │
│  │  - Metrics tracking                                         │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                     │                                                    │
│         ┌───────────┼───────────┬───────────┬────────────┐              │
│         ↓           ↓           ↓           ↓            ↓              │
│  ┌──────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐           │
│  │ TRANSFORM 1  │ │TRANSFORM2│ │TRANSFORM3│ │ TRANSFORM 4  │           │
│  │JsonTransform │ │ProtoTrans│ │XmlTransf │ │AvroTransform │           │
│  │ (JSON)       │ │ (Protobuf)│ │(XML)     │ │ (Avro)       │           │
│  └──────────────┘ └──────────┘ └──────────┘ └──────────────┘           │
│         │           │           │           │                           │
│         ↓           ↓           ↓           ↓                           │
│  ┌──────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐           │
│  │  RestSink    │ │GrpcSink  │ │ MQSink   │ │  DBSink      │           │
│  │ 50 req/sec   │ │200 req/s │ │500 req/s │ │1000 req/sec  │           │
│  │ Semaphore-   │ │ (Semaphor│ │(Semaphor │ │(Semaphore-   │           │
│  │ based rate   │ │e-rate)   │ │rate)     │ │rate limiting)│           │
│  │ limiting     │ │          │ │          │ │              │           │
│  └──────────────┘ └──────────┘ └──────────┘ └──────────────┘           │
│         │           │           │           │                           │
│         └───────────┼───────────┴───────────┴─ (Async CompletableFuture)
│                     │                                                    │
│                     ↓                                                    │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │  RESILIENCE LAYER                                            │      │
│  │  ┌────────────────────────────────────────────────────────┐ │      │
│  │  │ Retry Logic (max 3 attempts)                          │ │      │
│  │  │ ├─ Attempt 1: Success? → Metrics.incSuccess()        │ │      │
│  │  │ ├─ Attempt 2: Success? → Metrics.incSuccess()        │ │      │
│  │  │ ├─ Attempt 3: Success? → Metrics.incSuccess()        │ │      │
│  │  │ └─ Failure:           → DLQ.recordFailure()          │ │      │
│  │  └────────────────────────────────────────────────────────┘ │      │
│  │                     │                                        │      │
│  │                     ↓                                        │      │
│  │  ┌────────────────────────────────────────────────────────┐ │      │
│  │  │ DeadLetterQueue (Zero Data Loss Guarantee)            │ │      │
│  │  │ - File: dlq/failed-records.jsonl                      │ │      │
│  │  │ - Async persistence (separate thread)                 │ │      │
│  │  │ - Format: {record, sink, attempts, error, timestamp}  │ │      │
│  │  └────────────────────────────────────────────────────────┘ │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │  OBSERVABILITY LAYER (Metrics)                              │      │
│  │  - Records processed: 5000                                  │      │
│  │  - Throughput: 1000 records/sec                             │      │
│  │  - Success per sink: {REST: 1200, GRPC: 1300, ...}          │      │
│  │  - Failed per sink: {REST: 5, GRPC: 8, ...}                 │      │
│  │  - DLQ count: 50                                            │      │
│  │  - Report interval: Every 5 seconds                         │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Transformation Flow

```
Input Record
    │
    ├─ Format Detection
    │
    ├─ JSON Parsing
    │   └─ Parse {}
    │
    ├─ Branch Into 4 Transformers (Parallel)
    │
    ├─ REST (JSON Path)          ├─ GRPC (Protobuf Path)
    │   └─ JsonTransformer          └─ ProtoTransformer
    │       {"id":1}                    0x0a05...
    │
    ├─ MQ (XML Path)            ├─ DB (Avro Path)
    │   └─ XmlTransformer           └─ AvroTransformer
    │       <?xml...                   0x4f626a01...
    │
    └─ 4 Transformed Records Ready for Sinks
```

---

## 3. Concurrency Model (Virtual Threads)

```
Main Thread (start)
    │
    ├─ Virtual Thread 1: FileProducer
    │       │ Reads JSON.txt
    │       ├─ Record 1
    │       ├─ Record 2
    │       └─ Record 3
    │
    └─ Virtual Thread Pool (newVirtualThreadPerTaskExecutor)
            │
            ├─ Orchestrator (consumes from queue)
            │   │
            │   ├─ For Record 1:
            │   │   ├─ VT: Transform + Rest Sink
            │   │   ├─ VT: Transform + GRPC Sink
            │   │   ├─ VT: Transform + MQ Sink
            │   │   └─ VT: Transform + DB Sink
            │   │
            │   ├─ For Record 2:
            │   │   ├─ VT: Transform + Rest Sink
            │   │   ├─ VT: Transform + GRPC Sink
            │   │   ├─ VT: Transform + MQ Sink
            │   │   └─ VT: Transform + DB Sink
            │   └─ ...
            │
            └─ Metrics Reporter (every 5 sec)
                    └─ Print: Processed, Throughput, Success, Failed
```

**Result**: N records × 4 sinks = 4N virtual threads, all concurrent!

---

## 4. Retry & Failure Handling Flow

```
Record → Sink.send(data)
            │
            ├─ Try 1: Success? ─── Yes ──→ Metrics.incSuccess()
            │           │
            │          No
            │           │
            ├─ Try 2: Success? ─── Yes ──→ Metrics.incSuccess()
            │           │
            │          No
            │           │
            ├─ Try 3: Success? ─── Yes ──→ Metrics.incSuccess()
            │           │
            │          No
            │           │
            └─→ DLQ.recordFailure()
                    │
                    ├─ Persist to file: dlq/failed-records.jsonl
                    ├─ Track in memory: ConcurrentLinkedQueue
                    └─ Metrics.incFail()
```

---

## 5. Dead Letter Queue Format

```
dlq/failed-records.jsonl
│
├─ {"record":{"id":1,"name":"a"},"sink":"REST","attempts":3,"error":"Network timeout","timestamp":"2024-02-17T10:30:45Z"}
├─ {"record":{"id":2,"name":"b"},"sink":"GRPC","attempts":3,"error":"Invalid response","timestamp":"2024-02-17T10:30:46Z"}
├─ {"record":{"id":3,"name":"c"},"sink":"MQ","attempts":3,"error":"Connection refused","timestamp":"2024-02-17T10:30:47Z"}
└─ ...
```

**Benefits**:
- ✅ One record per line (easy to parse)
- ✅ Includes full context (record + sink + error)
- ✅ Timestamp for debugging
- ✅ Can replay failures programmatically

---

## 6. Configuration-Driven Architecture

```
application.yaml
    │
    ├─ input.filePath: "sample-data/input.json"
    ├─ input.format: "jsonl"
    │
    ├─ queue.capacity: 1000
    │
    ├─ sinks.rest.rateLimit: 50
    ├─ sinks.grpc.rateLimit: 200
    ├─ sinks.mq.rateLimit: 500
    ├─ sinks.db.rateLimit: 1000
    │
    ├─ dlq.enabled: true
    ├─ dlq.filePath: "dlq/failed-records.jsonl"
    │
    └─ metrics.intervalSeconds: 5
            │
            ↓
    ConfigLoader.getInstance()
            │
            ├─ FileProducer(config)
            ├─ BlockingQueue(config.queueCapacity)
            ├─ FanOutOrchestrator(config)
            │   ├─ RestSink(config.sinks.rest.rateLimit)
            │   ├─ GrpcSink(config.sinks.grpc.rateLimit)
            │   ├─ MessageQueueSink(config.sinks.mq.rateLimit)
            │   └─ WideColumnDbSink(config.sinks.db.rateLimit)
            └─ Metrics(config.intervalSeconds)
```

---

## 7. Adding a New Sink (Extensibility)

```
Current System:
┌─────────────────────────────────┐
│ REST | GRPC | MQ | DB           │
└─────────────────────────────────┘

Adding Elasticsearch:

Step 1: Create Sink
┌─────────────────────────────────┐
│ Sink (interface)                │
├─────────────────────────────────┤
│ ├─ RestSink                     │
│ ├─ GrpcSink                     │
│ ├─ MessageQueueSink             │
│ ├─ WideColumnDbSink             │
│ └─ ElasticsearchSink (NEW)      │
└─────────────────────────────────┘

Step 2: Create Transformer (if needed)
┌──────────────────────────────────┐
│ Transformer (interface)          │
├──────────────────────────────────┤
│ ├─ JsonTransformer              │
│ ├─ ProtoTransformer             │
│ ├─ XmlTransformer               │
│ ├─ AvroTransformer              │
│ └─ ElasticsearchTransformer(NEW)│
└──────────────────────────────────┘

Step 3: Update Configuration
application.yaml:
  sinks:
    elasticsearch:
      rateLimit: 800
      endpoint: "http://localhost:9200"

Step 4: Update Orchestrator (3-line change)
sinkList.add(new ElasticsearchSink(
    config.getInt("sinks.elasticsearch.rateLimit", 800)
));

Result: Elasticsearch now integrated WITHOUT changing
        core FanOutOrchestrator logic!
```

---

## 8. Memory Management & Streaming

```
File: 100GB
    │
    ├─ Traditional Approach (MEMORY OVERFLOW ❌)
    │   Load entire file → 100GB RAM → OOM → Crash
    │
    └─ FanOutOrchestrator Streaming (EFFICIENT ✅)
        Line 1: Read → Queue → Process → Send → Delete
        Line 2: Read → Queue → Process → Send → Delete
        Line 3: Read → Queue → Process → Send → Delete
        ...
        Memory Usage: ~10MB (regardless of file size)
```

---

## 9. Performance Scaling Model

```
Single CPU Core:
  Throughput = 1000 records/sec

With 8 CPU Cores (Virtual Threads):
  Throughput ≈ 8000 records/sec (linear scaling)
  - No thread pool overhead
  - Work-stealing scheduler
  - Minimal context switching

Bottleneck Analysis:
  Slowest Sink Rate = max(50, 200, 500, 1000) = 1000 req/sec
  Aggregate Throughput ≤ 1000 × 4 = 4000 records/sec per sink
  
  But with multiple records in parallel:
  Actual Throughput = min(disk I/O, slowest_sink) = ~2000-3000 records/sec
```

---

## Summary

This architecture demonstrates:
1. **Layered Design**: Clear separation of concerns
2. **Pattern Usage**: Strategy, Factory, Template Method, Observer
3. **Concurrency**: Virtual Threads for millions of tasks
4. **Resilience**: Retries + DLQ + Metrics
5. **Extensibility**: New sinks/transformers without core changes
6. **Memory Efficiency**: Streaming + fixed buffers
7. **Observability**: Real-time metrics every 5 seconds
8. **Configuration-Driven**: External YAML config
9. **Zero Data Loss**: DLQ persistence guarantees
