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
package org.apache.camel.component.aws2.sns;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Notification Service
 * <a href="http://aws.amazon.com/sns/">AWS SNS</a>
 */
public class Sns2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Sns2Producer.class);

    private transient String snsProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Sns2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        PublishRequest.Builder request = PublishRequest.builder();

        request.topicArn(getConfiguration().getTopicArn());
        request.subject(determineSubject(exchange));
        request.messageStructure(determineMessageStructure(exchange));
        request.message(exchange.getIn().getBody(String.class));
        request.messageAttributes(this.translateAttributes(exchange.getIn().getHeaders(), exchange));
        configureFifoAttributes(request, exchange);

        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);

        PublishResponse result = getEndpoint().getSNSClient().publish(request.build());

        LOG.trace("Received result [{}]", result);

        Message message = getMessageForResponse(exchange);
        message.setHeader(Sns2Constants.MESSAGE_ID, result.messageId());
    }

    private String determineSubject(Exchange exchange) {
        String subject = exchange.getIn().getHeader(Sns2Constants.SUBJECT, String.class);
        if (subject == null) {
            subject = getConfiguration().getSubject();
        }

        return subject;
    }

    private String determineMessageStructure(Exchange exchange) {
        String structure = exchange.getIn().getHeader(Sns2Constants.MESSAGE_STRUCTURE, String.class);
        if (structure == null) {
            structure = getConfiguration().getMessageStructure();
        }

        return structure;
    }

    Map<String, MessageAttributeValue> translateAttributes(Map<String, Object> headers, Exchange exchange) {
        Map<String, MessageAttributeValue> result = new HashMap<>();
        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        for (Entry<String, Object> entry : headers.entrySet()) {
            // only put the message header which is not filtered into the
            // message attribute
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                Object value = entry.getValue();
                if (value instanceof String && !((String) value).isEmpty()) {
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("String");
                    mav.stringValue((String) value);
                    result.put(entry.getKey(), mav.build());
                } else if (value instanceof Number) {
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("String");
                    mav.stringValue(value.toString());
                    result.put(entry.getKey(), mav.build());
                } else if (value instanceof ByteBuffer) {
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("Binary");
                    mav.binaryValue(SdkBytes.fromByteBuffer((ByteBuffer) value));
                    result.put(entry.getKey(), mav.build());
                } else if (value instanceof byte[]) {
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("Binary");
                    mav.binaryValue(SdkBytes.fromByteArray((byte[]) value));
                    result.put(entry.getKey(), mav.build());
                } else if (value instanceof Date) {
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("String");
                    mav.stringValue(value.toString());
                    result.put(entry.getKey(), mav.build());
                } else if (value instanceof List) {
                    String resultString = ((List<?>) value).stream()
                            .map(o -> o instanceof String ? String.format("\"%s\"", o) : Objects.toString(o))
                            .collect(Collectors.joining(", "));
                    MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
                    mav.dataType("String.Array");
                    mav.stringValue("[" + resultString + "]");
                    result.put(entry.getKey(), mav.build());
                } else {
                    // cannot translate the message header to message attribute
                    // value
                    LOG.warn("Cannot put the message header key={}, value={} into Sns MessageAttribute", entry.getKey(),
                            entry.getValue());
                }
            }
        }
        return result;
    }

    private void configureFifoAttributes(PublishRequest.Builder request, Exchange exchange) {
        if (getEndpoint().getConfiguration().isFifoTopic()) {
            // use strategies
            if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getMessageGroupIdStrategy())) {
                MessageGroupIdStrategy messageGroupIdStrategy = getEndpoint().getConfiguration().getMessageGroupIdStrategy();
                String messageGroupId = messageGroupIdStrategy.getMessageGroupId(exchange);
                request.messageGroupId(messageGroupId);
            }

            if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getMessageDeduplicationIdStrategy())) {
                MessageDeduplicationIdStrategy messageDeduplicationIdStrategy
                        = getEndpoint().getConfiguration().getMessageDeduplicationIdStrategy();
                String messageDeduplicationId = messageDeduplicationIdStrategy.getMessageDeduplicationId(exchange);
                request.messageDeduplicationId(messageDeduplicationId);
            }

        }
    }

    protected Sns2Configuration getConfiguration() {
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
    public Sns2Endpoint getEndpoint() {
        return (Sns2Endpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Sns2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
