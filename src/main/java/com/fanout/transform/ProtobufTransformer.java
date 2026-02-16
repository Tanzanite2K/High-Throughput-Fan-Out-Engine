
package com.fanout.transform;

public class ProtobufTransformer implements Transformer {
    public String transform(String input) {
        return "PROTOBUF:" + input;
    }
}
