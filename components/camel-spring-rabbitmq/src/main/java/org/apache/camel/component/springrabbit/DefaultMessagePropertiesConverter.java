/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.springrabbit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
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
        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            answer.setContentType(contentType);
        }

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
            if (messageProperties.getContentType() != null) {
                answer.put(Exchange.CONTENT_TYPE, messageProperties.getContentType());
            }
            if (messageProperties.getTimestamp() != null) {
                answer.put(Exchange.MESSAGE_TIMESTAMP, messageProperties.getTimestamp().getTime());
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
