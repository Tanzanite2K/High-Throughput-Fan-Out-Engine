# Architecture & Design Documentation

## System Overview

The High-Throughput Fan-Out Engine is a distributed data processing system that reads records from various sources and distributes them to multiple downstream systems (sinks) with high throughput and reliability.

## 1. Architecture Layers

### A. Ingestion Layer
- **Component**: `FileProducer`
- **Responsibility**: Reads data from files in multiple formats
- **Supported Formats**:
  - **JSON**: Array of JSON objects `[{...}, {...}]`
  - **JSONL**: JSON Lines - one JSON object per line
  - **CSV**: Comma-separated values with headers
  - **Fixed-width**: Tab or pipe-delimited fixed-width columns

**Key Features**:
- Streaming ingestion (no full file load into memory)
- Cross-platform file path handling
- Graceful error handling

---

### B. Queue & Backpressure Layer
- **Component**: `BlockingQueue<String>` (configurable capacity)
- **Purpose**: Regulate system load and provide backpressure
- **Benefits**:
  - Prevents memory overflow when sinks are slow
  - Configurable capacity via `application.yaml`
  - Thread-safe producer-consumer model

**Data Flow**:
```
Producer → BlockingQueue → Orchestrator → Sinks
           (Regulate flow)
```

---

### C. Transformation Layer
- **Pattern**: **Strategy Pattern**
- **Components**:
  - `Transformer` interface
  - `JsonTransformer` - JSON validation
  - `XmlTransformer` - XML serialization with CDATA
  - `ProtoTransformer` - Protobuf encoding simulation
  - `AvroTransformer` - Avro binary encoding simulation

**Transformation Flow**:
```
Input Record → Format-specific Transformer → Transformed Data → Sink
(JSON)        (Based on sink type)         (XML/Proto/Avro/JSON)
```

---

### D. Distribution Layer (Sinks)
- **Pattern**: **Factory Pattern** + **Strategy Pattern**
- **Components**:
  - `Sink` interface (contract for all sinks)
  - `BaseSink` abstract class (rate limiting template)
  - `RestSink` (HTTP/2 simulation, 50 req/sec)
  - `GrpcSink` (gRPC streaming simulation, 200 req/sec)
  - `MessageQueueSink` (Kafka/RabbitMQ simulation, 500 req/sec)
  - `WideColumnDbSink` (Cassandra/Aerospike simulation, 1000 req/sec)

**Rate Limiting**:
- Uses `Semaphore` with fixed refill rate
- Prevents overwhelming downstream services
- Configurable per sink

---

### E. Throttling & Resilience
- **Rate Limiting**: `SimpleRateLimiter` using semaphore
- **Retry Logic**: Configurable max retries (default: 3)
- **Dead Letter Queue (DLQ)**: Persistent failure tracking
- **Error Handling**: Graceful degradation

**Retry Flow**:
```
Attempt 1 (fail) → Attempt 2 (fail) → Attempt 3 (fail) → DLQ
```

---

### F. Dead Letter Queue (DLQ)
- **Component**: `DeadLetterQueue`
- **Responsibility**: Guarantee zero data loss
- **Features**:
  - Asynchronous file persistence
  - Concurrent-safe failure tracking
  - Sink name + record + retry count + error reason
  - JSONLines format for easy processing

**DLQ Record Format**:
```json
{"record": {...}, "sink": "REST", "attempts": 3, "error": "...", "timestamp": "2024-02-17T..."}
```

---

### G. Metrics & Observability
- **Component**: `Metrics` class
- **Tracks**:
  - Total records processed
  - Success/failure count per sink
  - Throughput (records/sec)
  - DLQ failed record count

**Metrics Report** (every 5 seconds):
```
📊 === METRICS REPORT ===
   Processed: 1500
   Throughput: 300 records/sec
   Success: {REST=350, GRPC=400, MQ=450, DB=500}
   Failed: {REST=10, GRPC=15, MQ=20, DB=5}
   DLQ Records: 50
========================
```

---

## 2. Design Patterns Used

### Strategy Pattern
**Problem**: Different sinks require different data formats
**Solution**: Transform interface with multiple implementations
```java
Map<String, Transformer> transformers = Map.of(
    "REST", new JsonTransformer(),
    "GRPC", new ProtoTransformer(),
    "MQ", new XmlTransformer(),
    "DB", new AvroTransformer()
);
```

### Factory Pattern
**Problem**: Creating different sinks with varied configurations
**Solution**: `createSinks()` method encapsulates sink creation
```java
private List<Sink> createSinks() {
    List<Sink> sinkList = new ArrayList<>();
    sinkList.add(new RestSink(restRate));
    sinkList.add(new GrpcSink(grpcRate));
    // ... more sinks
    return sinkList;
}
```

### Template Method Pattern
**Problem**: All sinks have common rate limiting logic
**Solution**: `BaseSink` abstract class with rate limiting template
```java
public abstract class BaseSink implements Sink {
    protected void acquire() throws InterruptedException {
        rateLimiter.acquire();
    }
}
```

### Observer Pattern (Metrics)
**Problem**: Monitor system state without coupling to core logic
**Solution**: Scheduled metrics reporter observes metrics
```java
Executors.newSingleThreadScheduledExecutor()
    .scheduleAtFixedRate(this::printMetrics, 5, 5, TimeUnit.SECONDS);
```

---

## 3. Concurrency Model

### Virtual Threads (Java 21)
- **Technology**: Structured Concurrency with Virtual Threads
- **Benefit**: Millions of concurrent tasks with minimal overhead
- **Implementation**:
```java
ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
```

### Synchronization
- **Atomic Operations**: `AtomicLong` for metrics
- **Thread-safe Collections**: `ConcurrentHashMap`, `ConcurrentLinkedQueue`
- **Blocking Operations**:
  - `BlockingQueue.put()` - producer blocks if full
  - `BlockingQueue.take()` - consumer blocks if empty

### Data Flow
```
Producer Thread
    ↓
BlockingQueue (synchronization point)
    ↓
Multiple VirtualThreads (4 sinks × N records)
    ↓
DLQ + Metrics (thread-safe updates)
```

---

## 4. Configuration-Driven Design

### Config File: `application.yaml`
```yaml
input:
  filePath: "sample-data/input.json"
  format: "jsonl"

queue:
  capacity: 1000

sinks:
  rest:
    rateLimit: 50
  grpc:
    rateLimit: 200
  mq:
    rateLimit: 500
  db:
    rateLimit: 1000

dlq:
  enabled: true
  filePath: "dlq/failed-records.jsonl"
```

### Config Loader Pattern
```java
ConfigLoader config = ConfigLoader.getInstance();
int queueCapacity = config.getInt("queue.capacity", 1000);
String filePath = config.getString("input.filePath", "default.json");
```

---

## 5. Extensibility

### Adding a New Sink (e.g., Elasticsearch)

1. **Create Sink Implementation**:
```java
public class ElasticsearchSink extends BaseSink {
    public ElasticsearchSink(int rate) { super(rate); }
    
    public CompletableFuture<Boolean> send(String data) { ... }
    public String name() { return "ELASTICSEARCH"; }
}
```

2. **Add Transformer** (if needed):
```java
public class ElasticsearchTransformer implements Transformer {
    public String transform(String input) { ... }
}
```

3. **Update Configuration**:
```yaml
sinks:
  elasticsearch:
    rateLimit: 800
    endpoint: "http://localhost:9200"
```

4. **Update Orchestrator**:
```java
sinkList.add(new ElasticsearchSink(
    config.getInt("sinks.elasticsearch.rateLimit", 800)
));
```

**No changes needed** to core `FanOutOrchestrator` logic!

---

## 6. Memory Management

### Streaming Architecture
- **File Reader**: Reads line-by-line (not full file)
- **Queue**: Fixed capacity (configurable, default 1000)
- **Transformers**: String objects only
- **Metrics**: Atomic variables (minimal memory)

### Memory Usage Formula
```
Memory ≈ Queue Capacity × Avg Record Size + Sink Buffers
       ≈ 1000 × 10KB + 4 × 1MB
       ≈ ~14MB (for typical workloads)
```

### Running with Small Heap
```bash
java -Xmx512m -jar target/fanout-engine.jar
```

---

## 7. Error Handling & Resilience

### Failure Scenarios

1. **Transformation Failure**
   - Caught, logged, record sent to DLQ
   - Other sinks unaffected

2. **Sink Unavailable**
   - Retry up to 3 times with exponential backoff
   - After max retries → DLQ

3. **Network Timeout**
   - Exception caught
   - Retry logic triggered
   - Persistent record in DLQ

4. **Application Crash**
   - DLQ file offers recovery mechanism
   - Can be reprocessed manually

### Error Recovery
```python
Failed Records (DLQ)
        ↓
[Manual Review / Automated Reprocessing]
        ↓
[Fix Issues]
        ↓
[Resubmit to Producer]
```

---

## 8. Scalability Characteristics

### Linear Scaling with CPU Cores
- Virtual threads spawn N × 4 tasks (N records, 4 sinks)
- Workstealing scheduler distributes load
- No thread pool bottleneck

### Horizontal Scalability
- **Shared Queue**: Multi-producer via config
- **DLQ Storage**: Can be mounted to distributed file system
- **Metrics**: Can be shipped to central monitoring

### Bottleneck Analysis
1. **Disk I/O**: Mitigated by streaming
2. **Queue**: Configurable capacity handles spikes
3. **Slowest Sink**: Backpressure stops producer (by design)

---

## 9. Testing Strategy

### Unit Tests
- **Transformer Tests**: Verify format conversions
- **Sink Tests**: Mock sinks using Mockito
- **DLQ Tests**: Verify failure persistence
- **Rate Limiter Tests**: Verify throttling

### Integration Tests
- **Pipeline Test**: End-to-end flow
- **Retry Test**: Failure and recovery
- **Retry Integration Test**: Real sink interaction

### Test Technologies
- **JUnit 5**: Test framework
- **Mockito**: Mocking and verification
- **TempDir**: Temporary file handling for DLQ

---

## 10. Performance Tuning

### Recommendations
1. **Increase Queue Capacity** if producer often blocks:
   ```yaml
   queue:
     capacity: 5000
   ```

2. **Adjust Rate Limits** based on downstream capacity:
   ```yaml
   sinks:
     db:
       rateLimit: 2000  # Increase if DB can handle more
   ```

3. **Monitor Metrics** for bottlenecks:
   ```
   If throughput drops → queue may beoverflow
   If fail count high → sink may be failing
   If DLQ grows → retries ineffective
   ```

4. **Tune Heap** for large files:
   ```bash
   java -Xmx2g -jar fanout-engine.jar
   ```

---

## Summary

The High-Throughput Fan-Out Engine demonstrates:
- ✅ Concurrent programming (Virtual Threads)
- ✅ System design (Layers, Patterns, Scalability)
- ✅ Fault tolerance (Retries, DLQ, Metrics)
- ✅ Extensibility (Factory, Strategy patterns)
- ✅ Memory efficiency (Streaming, fixed buffers)
- ✅ Configuration-driven architecture
- ✅ Zero data loss guarantee
