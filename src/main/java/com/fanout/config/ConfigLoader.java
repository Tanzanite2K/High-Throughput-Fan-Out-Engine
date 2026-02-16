package com.fanout.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple YAML configuration loader.
 * Loads application.yaml and provides configuration access.
 */
public class ConfigLoader {
    
    private static ConfigLoader instance;
    private final Map<String, Object> config = new HashMap<>();
    
    private ConfigLoader() {
        loadConfig();
    }
    
    public static ConfigLoader getInstance() {
        if (instance == null) {
            synchronized (ConfigLoader.class) {
                if (instance == null) {
                    instance = new ConfigLoader();
                }
            }
        }
        return instance;
    }
    
    private void loadConfig() {
        try {
            // Load application.yaml from classpath
            var resource = this.getClass().getClassLoader().getResourceAsStream("application.yaml");
            if (resource == null) {
                System.out.println("⚠️  application.yaml not found. Using defaults.");
                loadDefaults();
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                    parseConfigLine(line);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️  Error loading configuration: " + e.getMessage());
            loadDefaults();
        }
    }
    
    private void parseConfigLine(String line) {
        // Simple YAML key: value parser
        if (line.contains(":")) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                config.put(key, value);
            }
        }
    }
    
    private void loadDefaults() {
        // Default configuration
        config.put("input.filePath", "sample-data/input.json");
        config.put("input.format", "jsonl");
        config.put("queue.capacity", "1000");
        config.put("sinks.rest.rateLimit", "50");
        config.put("sinks.grpc.rateLimit", "200");
        config.put("sinks.mq.rateLimit", "500");
        config.put("sinks.db.rateLimit", "1000");
        config.put("dlq.enabled", "true");
        config.put("dlq.filePath", "dlq/failed-records.jsonl");
        config.put("metrics.intervalSeconds", "5");
    }
    
    public String getString(String key, String defaultValue) {
        return (String) config.getOrDefault(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        try {
            String value = (String) config.get(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = (String) config.get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    public SinkConfig getSinkConfig(String sinkName) {
        int rateLimit = getInt("sinks." + sinkName.toLowerCase() + ".rateLimit", 100);
        String endpoint = getString("sinks." + sinkName.toLowerCase() + ".endpoint", "");
        int retryCount = getInt("sinks." + sinkName.toLowerCase() + ".retryCount", 3);
        
        return new SinkConfig(sinkName, endpoint, rateLimit, retryCount);
    }
    
    // Inner class for sink configuration
    public static class SinkConfig {
        public final String name;
        public final String endpoint;
        public final int rateLimit;
        public final int retryCount;
        
        public SinkConfig(String name, String endpoint, int rateLimit, int retryCount) {
            this.name = name;
            this.endpoint = endpoint;
            this.rateLimit = rateLimit;
            this.retryCount = retryCount;
        }
    }
}
