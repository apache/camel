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
package org.apache.camel.component.aws.sns;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.URISupport;


/**
 * A Producer which sends messages to the Amazon Web Service Simple Notification Service
 * <a href="http://aws.amazon.com/sns/">AWS SNS</a>
 */
public class SnsProducer extends DefaultProducer {

    private transient String snsProducerToString;

    public SnsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        PublishRequest request = new PublishRequest();

        request.setTopicArn(getConfiguration().getTopicArn());
        request.setSubject(determineSubject(exchange));
        request.setMessageStructure(determineMessageStructure(exchange));
        request.setMessage(exchange.getIn().getBody(String.class));
        request.setMessageAttributes(this.translateAttributes(exchange.getIn().getHeaders(), exchange));

        log.trace("Sending request [{}] from exchange [{}]...", request, exchange);

        PublishResult result = getEndpoint().getSNSClient().publish(request);

        log.trace("Received result [{}]", result);

        Message message = getMessageForResponse(exchange);
        message.setHeader(SnsConstants.MESSAGE_ID, result.getMessageId());
    }

    private String determineSubject(Exchange exchange) {
        String subject = exchange.getIn().getHeader(SnsConstants.SUBJECT, String.class);
        if (subject == null) {
            subject = getConfiguration().getSubject();
        }

        return subject;
    }

    private String determineMessageStructure(Exchange exchange) {
        String structure = exchange.getIn().getHeader(SnsConstants.MESSAGE_STRUCTURE, String.class);
        if (structure == null) {
            structure = getConfiguration().getMessageStructure();
        }

        return structure;
    }

    Map<String, MessageAttributeValue> translateAttributes(Map<String, Object> headers, Exchange exchange) {
        Map<String, MessageAttributeValue> result = new HashMap<>();
        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        for (Entry<String, Object> entry : headers.entrySet()) {
            // only put the message header which is not filtered into the message attribute
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                Object value = entry.getValue();
                if (value instanceof String && !((String)value).isEmpty()) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("String");
                    mav.withStringValue((String)value);
                    result.put(entry.getKey(), mav);
                } else if (value instanceof ByteBuffer) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("Binary");
                    mav.withBinaryValue((ByteBuffer)value);
                    result.put(entry.getKey(), mav);
                } else if (value instanceof Date) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("String");
                    mav.withStringValue(value.toString());
                    result.put(entry.getKey(), mav);
                } else if (value instanceof List) {
                    List<?> valueList = ((List<?>) value).stream().filter(Objects::nonNull).collect(Collectors.toList());
                    if (valueList.size() > 0) {
                        // Avoiding reliance on .toString()
                        String delimiter = ", ", prefix = "[", suffix = "]";
                        if (String.class == valueList.get(0).getClass()) {
                            delimiter = "\", \"";
                            prefix = "[\"";
                            suffix = "\"]";
                        }
                        MessageAttributeValue mav = new MessageAttributeValue();
                        mav.setDataType("String.Array");
                        mav.withStringValue(valueList.stream().map(Object::toString).collect(Collectors.joining(delimiter, prefix, suffix)));
                        result.put(entry.getKey(), mav);
                    } else {
                        log.warn("Either list is empty or has all null values for key={}, value={} . " +
                                "Cannot put in Sns MessageAttribute", entry.getKey(), entry.getValue());
                    }
                } else {
                    // cannot translate the message header to message attribute value
                    log.warn("Cannot put the message header key={}, value={} into Sns MessageAttribute", entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    protected SnsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (snsProducerToString == null) {
            snsProducerToString = "SnsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return snsProducerToString;
    }

    @Override
    public SnsEndpoint getEndpoint() {
        return (SnsEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }
}