package com.fanout.ingestion;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * File Producer that reads from various formats (JSON, JSONL, CSV, Fixed-width)
 * and pushes records to the queue for processing.
 * 
 * Supports:
 * - JSON: Array of objects
 * - JSONL: JSON Lines (one JSON object per line)
 * - CSV: Comma-separated values
 * - Fixed-width: Fixed column widths
 */
public class FileProducer implements Runnable {

    private final BlockingQueue<String> queue;
    private final String filePath;
    private final String format;

    public FileProducer(BlockingQueue<String> queue, String filePath) {
        this(queue, filePath, "jsonl");
    }

    public FileProducer(BlockingQueue<String> queue, String filePath, String format) {
        this.queue = queue;
        this.filePath = filePath;
        this.format = format != null ? format.toLowerCase() : "jsonl";
    }

    @Override
    public void run() {
        try {
            switch (format) {
                case "json":
                    readJsonArray();
                    break;
                case "jsonl":
                    readJsonLines();
                    break;
                case "csv":
                    readCsv();
                    break;
                case "fixedwidth":
                    readFixedWidth();
                    break;
                default:
                    readJsonLines(); // Default to JSONL
            }
        } catch (Exception e) {
            System.err.println("❌ Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reads JSON array format: [{"id":1}, {"id":2}]
     */
    private void readJsonArray() throws IOException, InterruptedException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equals("[") || line.equals("]")) continue;
                
                // Remove trailing comma if present
                if (line.endsWith(",")) {
                    line = line.substring(0, line.length() - 1);
                }
                
                if (line.startsWith("{") && line.endsWith("}")) {
                    queue.put(line);
                }
            }
        }
    }

    /**
     * Reads JSON Lines format: one JSON object per line
     */
    private void readJsonLines() throws IOException, InterruptedException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equals("[") && !line.equals("]")) {
                    // Remove trailing comma
                    if (line.endsWith(",")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    if (line.startsWith("{")) {
                        queue.put(line);
                    }
                }
            }
        }
    }

    /**
     * Reads CSV format: converts each row to JSON
     * First row is treated as headers
     */
    private void readCsv() throws IOException, InterruptedException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;
            
            String[] headers = headerLine.split(",");
            String line;
            
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                StringBuilder json = new StringBuilder("{");
                
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(headers[i].trim()).append("\":\"")
                        .append(values[i].trim()).append("\"");
                }
                
                json.append("}");
                queue.put(json.toString());
            }
        }
    }

    /**
     * Reads Fixed-width format
     * Assumes columns are separated by consistent spacing
     */
    private void readFixedWidth() throws IOException, InterruptedException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;
            
            // Simple fixed-width parser (assumes pipe-delimited or tab-separated)
            String[] headers;
            if (headerLine.contains("|")) {
                headers = headerLine.split("\\|");
            } else {
                headers = headerLine.split("\t");
            }
            
            String line;
            while ((line = br.readLine()) != null) {
                String[] values;
                if (line.contains("|")) {
                    values = line.split("\\|");
                } else {
                    values = line.split("\t");
                }
                
                StringBuilder json = new StringBuilder("{");
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(headers[i].trim()).append("\":\"")
                        .append(values[i].trim()).append("\"");
                }
                json.append("}");
                queue.put(json.toString());
            }
        }
    }
}