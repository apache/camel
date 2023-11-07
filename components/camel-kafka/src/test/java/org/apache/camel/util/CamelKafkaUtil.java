package org.apache.camel.util;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;

import java.util.Objects;

public final class CamelKafkaUtil {
    
    public static String buildKafkaLogMessage(String msg, Exchange exchange, boolean includeBody) {
        String eol = "\n";

        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(msg)) {
            sb.append(msg);
            sb.append(eol);
        }
        
        sb.append("Message consumed from ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.TOPIC, String.class));
        sb.append(eol);
        sb.append("The Partition:Offset is ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.PARTITION, String.class));
        sb.append(":");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.OFFSET, String.class));
        sb.append(eol);
        sb.append("The Key is ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.KEY, String.class));
        
        if (includeBody) {
            sb.append(eol);
            sb.append(exchange.getMessage().getBody(String.class));
        }
        
        return sb.toString();
    }

}
