/**
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
package org.apache.camel.component.aws.sqs;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Queue Service
 * <a href="http://aws.amazon.com/sqs/">AWS SQS</a>
 * 
 */
public class SqsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SqsProducer.class);

    private transient String sqsProducerToString;

    public SqsProducer(SqsEndpoint endpoint) throws NoFactoryAvailableException {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        SendMessageRequest request = new SendMessageRequest(getQueueUrl(), body);
        request.setMessageAttributes(translateAttributes(exchange.getIn().getHeaders(), exchange));
        addDelay(request, exchange);
        configureFifoAttributes(request, exchange);

        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);

        SendMessageResult result = getClient().sendMessage(request);

        LOG.trace("Received result [{}]", result);

        Message message = getMessageForResponse(exchange);
        message.setHeader(SqsConstants.MESSAGE_ID, result.getMessageId());
        message.setHeader(SqsConstants.MD5_OF_BODY, result.getMD5OfMessageBody());
    }

    private void configureFifoAttributes(SendMessageRequest request, Exchange exchange) {
        if (getEndpoint().getConfiguration().isFifoQueue()) {
            // use strategies
            MessageGroupIdStrategy messageGroupIdStrategy = getEndpoint().getConfiguration().getMessageGroupIdStrategy();
            String messageGroupId = messageGroupIdStrategy.getMessageGroupId(exchange);
            request.setMessageGroupId(messageGroupId);

            MessageDeduplicationIdStrategy messageDeduplicationIdStrategy = getEndpoint().getConfiguration().getMessageDeduplicationIdStrategy();
            String messageDeduplicationId = messageDeduplicationIdStrategy.getMessageDeduplicationId(exchange);
            request.setMessageDeduplicationId(messageDeduplicationId);

        }
    }

    private void addDelay(SendMessageRequest request, Exchange exchange) {
        Integer headerValue = exchange.getIn().getHeader(SqsConstants.DELAY_HEADER, Integer.class);
        Integer delayValue;
        if (headerValue == null) {
            LOG.trace("Using the config delay");
            delayValue = getEndpoint().getConfiguration().getDelaySeconds();
        } else {
            LOG.trace("Using the header delay");
            delayValue = headerValue;
        }
        LOG.trace("found delay: " + delayValue);
        request.setDelaySeconds(delayValue == null ? Integer.valueOf(0) : delayValue);
    }

    protected AmazonSQS getClient() {
        return getEndpoint().getClient();
    }

    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }

    @Override
    public SqsEndpoint getEndpoint() {
        return (SqsEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (sqsProducerToString == null) {
            sqsProducerToString = "SqsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sqsProducerToString;
    }

    private Map<String, MessageAttributeValue> translateAttributes(Map<String, Object> headers, Exchange exchange) {
        Map<String, MessageAttributeValue> result = new HashMap<String, MessageAttributeValue>();
        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        for (Entry<String, Object> entry : headers.entrySet()) {
            // only put the message header which is not filtered into the message attribute
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("String");
                    mav.withStringValue((String)value);
                    result.put(entry.getKey(), mav);
                } else if (value instanceof ByteBuffer) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("Binary");
                    mav.withBinaryValue((ByteBuffer)value);
                    result.put(entry.getKey(), mav);
                } else {
                    // cannot translate the message header to message attribute value
                    LOG.warn("Cannot put the message header key={}, value={} into Sqs MessageAttribute", entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
}
