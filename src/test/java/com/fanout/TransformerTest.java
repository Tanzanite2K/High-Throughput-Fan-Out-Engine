package com.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fanout.transform.AvroTransformer;
import com.fanout.transform.JsonTransformer;
import com.fanout.transform.ProtoTransformer;
import com.fanout.transform.Transformer;
import com.fanout.transform.XmlTransformer;

/**
 * Transformer tests validating data transformation logic
 */
public class TransformerTest {

    @Test
    void jsonTransformerValidatesJson() {
        Transformer t = new JsonTransformer();
        String result = t.transform("{\"id\":1,\"name\":\"test\"}");
        
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    void jsonTransformerHandlesEmpty() {
        Transformer t = new JsonTransformer();
        String result = t.transform("");
        
        assertEquals("{}", result);
    }

    @Test
    void xmlTransformerCreatesWellFormedXml() {
        Transformer t = new XmlTransformer();
        String result = t.transform("<test>data</test>");
        
        assertTrue(result.contains("<?xml"));
        assertTrue(result.contains("<message>"));
        assertTrue(result.contains("<data>"));
        assertTrue(result.contains("</data>"));
        assertTrue(result.contains("</message>"));
    }

    @Test
    void xmlTransformerUsesLowercaseCdata() {
        Transformer t = new XmlTransformer();
        String result = t.transform("test");
        
        assertTrue(result.contains("<![CDATA["));
        assertTrue(result.contains("]]>"));
    }

    @Test
    void protoTransformerCreatesBinaryRepresentation() {
        Transformer t = new ProtoTransformer();
        String result = t.transform("{\"id\":1}");
        
        assertTrue(result.startsWith("0x"));
        assertTrue(result.contains("0a")); // Field 1, wire type 2
    }

    @Test
    void protoTransformerHandlesEmpty() {
        Transformer t = new ProtoTransformer();
        String result = t.transform("");
        
        assertTrue(result.startsWith("0x"));
    }

    @Test
    void avroTransformerCreatesBinaryRepresentation() {
        Transformer t = new AvroTransformer();
        String result = t.transform("{\"id\":1}");
        
        assertTrue(result.startsWith("0x"));
        assertTrue(result.contains("4f626a01")); // Avro magic
    }

    @Test
    void avroTransformerHandlesEmpty() {
        Transformer t = new AvroTransformer();
        String result = t.transform("");
        
        assertTrue(result.startsWith("0x"));
    }

    @Test
    void transformerStrategyPatternWorks() {
        // Arrange
        String input = "{\"id\":1,\"name\":\"test\"}";
        
        // Act & Assert
        Transformer json = new JsonTransformer();
        Transformer xml = new XmlTransformer();
        Transformer proto = new ProtoTransformer();
        Transformer avro = new AvroTransformer();
        
        String jsonResult = json.transform(input);
        String xmlResult = xml.transform(input);
        String protoResult = proto.transform(input);
        String avroResult = avro.transform(input);
        
        // All transformers produce different outputs
        assertNotEquals(jsonResult, xmlResult);
        assertNotEquals(jsonResult, protoResult);
        assertNotEquals(jsonResult, avroResult);
        
        // Verify formats
        assertTrue(jsonResult.startsWith("{"));
        assertTrue(xmlResult.contains("<?xml"));
        assertTrue(protoResult.startsWith("0x"));
        assertTrue(avroResult.startsWith("0x"));
    }
}
