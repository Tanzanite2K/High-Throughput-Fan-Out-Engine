package com.fanout.transform;

/**
 * Improved XmlTransformer - creates proper XML structure with CDATA
 */
public class XmlTransformer implements Transformer {
    
    @Override
    public String transform(String input) {
        try {
            if (input == null || input.isEmpty()) {
                return "<?xml version=\"1.0\"?><root/>";
            }
            
            return "<?xml version=\"1.0\"?>\n" +
                   "<message>\n" +
                   "  <data><![CDATA[" + input + "]]></data>\n" +
                   "</message>";
        } catch (Exception e) {
            System.err.println("⚠️ Failed to transform XML: " + e.getMessage());
            return "<?xml version=\"1.0\"?><error/>";
        }
    }
}