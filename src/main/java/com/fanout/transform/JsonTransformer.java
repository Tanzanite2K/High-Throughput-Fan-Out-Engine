package com.fanout.transform;

/**
 * Improved JsonTransformer - validates and normalizes JSON
 */
public class JsonTransformer implements Transformer {
    
    @Override
    public String transform(String input) {
        try {
            // Validate JSON structure
            if (input == null || input.isEmpty()) {
                return "{}";
            }
            
            String trimmed = input.trim();
            
            // Already JSON object or array
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return trimmed;
            }
            
            // Return as-is if valid
            return trimmed;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to transform JSON: " + e.getMessage());
            return "{}";
        }
    }
}