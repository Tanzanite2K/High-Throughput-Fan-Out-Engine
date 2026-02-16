package com.fanout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fanout.config.ConfigLoader;
import com.fanout.dlq.DeadLetterQueue;
import com.fanout.ingestion.FileProducer;
import com.fanout.sink.GrpcSink;
import com.fanout.sink.MessageQueueSink;
import com.fanout.sink.RestSink;
import com.fanout.sink.Sink;
import com.fanout.sink.WideColumnDbSink;
import com.fanout.transform.AvroTransformer;
import com.fanout.transform.JsonTransformer;
import com.fanout.transform.ProtoTransformer;
import com.fanout.transform.Transformer;
import com.fanout.transform.XmlTransformer;
import com.fanout.util.Metrics;

/**
 * FanOutOrchestrator coordinates the entire data flow:
 * Producer → Queue → Transformers → Sinks → Metrics/DLQ
 * 
 * Uses configuration loader for externalized settings and
 * factory pattern for sink creation.
 */
public class FanOutOrchestrator {

    private final boolean testMode;
    private final BlockingQueue<String> queue;
    private final Metrics metrics = new Metrics();
    private final List<Sink> sinks;
    private final DeadLetterQueue dlq;
    private final ConfigLoader config;

    private final Map<String, Transformer> transformers = Map.of(
            "REST", new JsonTransformer(),
            "GRPC", new ProtoTransformer(),
            "MQ", new XmlTransformer(),
            "DB", new AvroTransformer()
    );

    public FanOutOrchestrator() {
        this(false);
    }

    public FanOutOrchestrator(boolean testMode) {
        this.testMode = testMode;
        this.config = ConfigLoader.getInstance();
        
        // Initialize queue with configured capacity
        int queueCapacity = config.getInt("queue.capacity", 1000);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        
        // Initialize DLQ
        boolean dlqEnabled = config.getBoolean("dlq.enabled", true);
        String dlqPath = config.getString("dlq.filePath", "dlq/failed-records.jsonl");
        this.dlq = new DeadLetterQueue(dlqPath, dlqEnabled);
        
        // Create sinks using factory pattern
        this.sinks = createSinks();
    }

    /**
     * Factory method to create sinks based on configuration.
     * This demonstrates the Factory pattern for extensibility.
     */
    private List<Sink> createSinks() {
        List<Sink> sinkList = new ArrayList<>();
        
        // REST Sink
        int restRate = config.getInt("sinks.rest.rateLimit", 50);
        sinkList.add(new RestSink(restRate));
        
        // gRPC Sink
        int grpcRate = config.getInt("sinks.grpc.rateLimit", 200);
        sinkList.add(new GrpcSink(grpcRate));
        
        // Message Queue Sink
        int mqRate = config.getInt("sinks.mq.rateLimit", 500);
        sinkList.add(new MessageQueueSink(mqRate));
        
        // Wide-Column DB Sink
        int dbRate = config.getInt("sinks.db.rateLimit", 1000);
        sinkList.add(new WideColumnDbSink(dbRate));
        
        return sinkList;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public DeadLetterQueue getDLQ() {
        return dlq;
    }

    /**
     * Starts the orchestrator in normal mode (production-like).
     */
    public void start(boolean enableMetrics) throws Exception {
        System.out.println("🚀 Fan-Out Orchestrator starting...");
        System.out.println("📊 Config: Queue=" + queue.remainingCapacity() + 
                         ", Sinks=" + sinks.size() + ", DLQ=" + (dlq != null));

        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

        // Load configuration
        String filePath = config.getString("input.filePath", "sample-data/input.json");
        String format = config.getString("input.format", "jsonl");

        // Start producer
        exec.submit(new FileProducer(queue, filePath, format));

        if (enableMetrics) {
            startMetrics();
        }

        // Process records continuously
        processRecordsStream(exec);

        exec.shutdown();
        if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
            System.err.println("❌ Executor did not terminate in time");
            exec.shutdownNow();
        }
        
        System.out.println("✅ Fan-Out Orchestrator finished");
        printFinalMetrics();
    }

    /**
     * Starts the orchestrator in test mode with a fixed number of records.
     */
    public void startTestMode(int maxRecords) throws Exception {
        System.out.println("🧪 Test Mode: Processing " + maxRecords + " records");
        
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

        String filePath = config.getString("input.filePath", "sample-data/input.json");
        String format = config.getString("input.format", "jsonl");
        exec.submit(new FileProducer(queue, filePath, format));

        int count = 0;
        List<Future<?>> futures = new ArrayList<>();

        while (count < maxRecords) {
            String record = queue.poll(5, TimeUnit.SECONDS);
            if (record == null) break; // Queue empty
            
            metrics.processed.incrementAndGet();
            count++;

            for (Sink sink : sinks) {
                futures.add(exec.submit(() -> processSink(record, sink)));
            }
        }

        // Wait for all futures
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("⚠️ Task failed: " + e.getMessage());
            }
        }

        exec.shutdown();
    }

    /**
     * Processes records in a continuous stream.
     */
    private void processRecordsStream(ExecutorService exec) throws InterruptedException {
        try {
            while (true) {
                String record = queue.poll(5, TimeUnit.SECONDS);
                if (record == null) {
                    System.out.println("📭 Queue empty, stopping...");
                    break;
                }
                
                metrics.processed.incrementAndGet();

                for (Sink sink : sinks) {
                    exec.submit(() -> processSink(record, sink));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Production stream interrupted");
        }
    }

    /**
     * Processes a record through a single sink with retry logic and DLQ.
     */
    private void processSink(String record, Sink sink) {
        String sinkName = sink.name();
        int maxRetries = config.getInt("dlq.maxRetries", 3);
        int retryCount = 0;
        boolean success = false;

        String transformedData = null;
        try {
            // Transform data
            Transformer transformer = transformers.get(sinkName);
            transformedData = transformer != null ? transformer.transform(record) : record;
        } catch (Exception e) {
            System.err.println("❌ Transformation failed for " + sinkName + ": " + e.getMessage());
            dlq.recordFailure(record, sinkName, 0, "Transformation failed: " + e.getMessage());
            metrics.incFail(sinkName);
            return;
        }

        // Retry logic
        while (retryCount < maxRetries && !success) {
            try {
                success = sink.send(transformedData).join();
                if (success) {
                    metrics.incSuccess(sinkName);
                } else {
                    retryCount++;
                }
            } catch (Exception e) {
                retryCount++;
                System.err.println("⚠️ Retry " + retryCount + " for " + sinkName + ": " + e.getMessage());
            }
        }

        // If all retries failed, record to DLQ
        if (!success) {
            dlq.recordFailure(record, sinkName, maxRetries, 
                            "Max retries (" + maxRetries + ") exceeded");
            metrics.incFail(sinkName);
        }
    }

    /**
     * Starts the metrics reporter that prints every N seconds.
     */
    private void startMetrics() {
        int intervalSeconds = config.getInt("metrics.intervalSeconds", 5);
        
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsReporter");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            System.out.println("\n📊 === METRICS REPORT ===");
            System.out.println("   Processed: " + metrics.processed.get());
            System.out.println("   Throughput: " + metrics.throughput() + " records/sec");
            System.out.println("   Success: " + metrics.success);
            System.out.println("   Failed: " + metrics.fail);
            System.out.println("   DLQ Records: " + dlq.getFailedCount());
            System.out.println("========================\n");
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Prints final metrics when orchestrator stops.
     */
    private void printFinalMetrics() {
        System.out.println("\n📈 === FINAL METRICS ===");
        System.out.println("Total Records Processed: " + metrics.processed.get());
        System.out.println("Average Throughput: " + metrics.throughput() + " records/sec");
        System.out.println("Success by Sink: " + metrics.success);
        System.out.println("Failures by Sink: " + metrics.fail);
        System.out.println("DLQ Failed Records: " + dlq.getFailedCount());
        System.out.println("=======================\n");
    }
}
