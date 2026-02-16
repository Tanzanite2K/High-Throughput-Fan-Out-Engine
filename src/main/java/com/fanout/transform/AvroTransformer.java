package com.fanout.transform;

/**
 * Improved AvroTransformer - simulates Avro serialization
 */
public class AvroTransformer implements Transformer {
    
    @Override
    public String transform(String input) {
        try {
            if (input == null || input.isEmpty()) {
                return encodeAvro("");
            }
            return encodeAvro(input);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to transform Avro: " + e.getMessage());
            return "AVRO_ERROR";
        }
    }
    
    private String encodeAvro(String input) {
        byte[] data = input.getBytes();
        StringBuilder sb = new StringBuilder();
        sb.append("0x4f626a01");
        sb.append(String.format("%08x", data.length));
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xff));
        }
        sb.append("deadbeefcafebabe");
        return sb.toString();
    }
}