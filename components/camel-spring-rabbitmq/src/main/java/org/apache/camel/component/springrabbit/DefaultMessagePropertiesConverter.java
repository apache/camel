package org.apache.camel.component.springrabbit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.springframework.amqp.core.MessageProperties;

public class DefaultMessagePropertiesConverter implements MessagePropertiesConverter {

    private final CamelContext camelContext;
    private final HeaderFilterStrategy headerFilterStrategy;

    public DefaultMessagePropertiesConverter(CamelContext camelContext, HeaderFilterStrategy headerFilterStrategy) {
        this.camelContext = camelContext;
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public MessageProperties toMessageProperties(Exchange exchange) {
        MessageProperties answer = new MessageProperties();

        Set<Map.Entry<String, Object>> entries = exchange.getMessage().getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            appendOutputHeader(answer, headerName, headerValue, exchange);
        }

        return answer;
    }

    @Override
    public Map<String, Object> fromMessageProperties(MessageProperties messageProperties, Exchange exchange) {
        Map<String, Object> answer = new HashMap<>();

        if (messageProperties != null) {
            Set<Map.Entry<String, Object>> entries = messageProperties.getHeaders().entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                String headerName = entry.getKey();
                Object headerValue = entry.getValue();
                appendInputHeader(answer, headerName, headerValue, exchange);
            }
        }

        return answer;
    }

    private void appendOutputHeader(MessageProperties answer, String headerName, Object headerValue, Exchange ex) {
        if (shouldOutputHeader(headerName, headerValue, ex)) {
            answer.setHeader(headerName, headerValue);
        }
    }

    private void appendInputHeader(Map<String, Object> answer, String headerName, Object headerValue, Exchange ex) {
        if (shouldOutputHeader(headerName, headerValue, ex)) {
            answer.put(headerName, headerValue);
        }
    }

    /**
     * Strategy to allow filtering of headers which are put on the RabbitMQ message
     */
    protected boolean shouldOutputHeader(String headerName, Object headerValue, Exchange exchange) {
        return headerFilterStrategy == null
                || !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

    /**
     * Strategy to allow filtering from RabbitMQ message to Camel message
     */
    protected boolean shouldInputHeader(String headerName, Object headerValue, Exchange exchange) {
        return headerFilterStrategy == null
                || !headerFilterStrategy.applyFilterToExternalHeaders(headerName, headerValue, exchange);
    }

}
