package com.fanout.transform;

/**
 * Improved ProtoTransformer - simulates Protobuf serialization
 */
public class ProtoTransformer implements Transformer {
    
    @Override
    public String transform(String input) {
        try {
            if (input == null || input.isEmpty()) {
                return encodeProtobuf("");
            }
            return encodeProtobuf(input);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to transform Protobuf: " + e.getMessage());
            return "PROTO_ERROR";
        }
    }
    
    private String encodeProtobuf(String input) {
        byte[] data = input.getBytes();
        int length = data.length;
        StringBuilder sb = new StringBuilder();
        sb.append("0x");
        sb.append(String.format("%02x", 0x0a));
        sb.append(String.format("%02x", length));
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}