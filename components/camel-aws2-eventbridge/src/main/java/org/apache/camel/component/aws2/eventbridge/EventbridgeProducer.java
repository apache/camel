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
package org.apache.camel.component.aws2.eventbridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;

/**
 * A Producer which sends messages to the Amazon Eventbridge Service SDK v2
 * <a href="http://aws.amazon.com/eventbridge/">AWS Eventbridge</a>
 */
public class EventbridgeProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventbridgeProducer.class);

    private transient String eventbridgeProducerToString;

    public EventbridgeProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case putRule:
                putRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case putTargets:
                putTargets(getEndpoint().getEventbridgeClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private EventbridgeOperations determineOperation(Exchange exchange) {
        EventbridgeOperations operation
                = exchange.getIn().getHeader(EventbridgeConstants.OPERATION, EventbridgeOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected EventbridgeConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (eventbridgeProducerToString == null) {
            eventbridgeProducerToString = "EventbridgeProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return eventbridgeProducerToString;
    }

    private void putRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException, IOException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutRuleRequest) {
                PutRuleResponse result;
                try {
                    result = eventbridgeClient.putRule((PutRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("PutRule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutRuleRequest.Builder builder = PutRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            if (ObjectHelper.isEmpty(exchange.getIn().getHeader(EventbridgeConstants.EVENT_PATTERN))) {
                InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getEndpoint().getCamelContext(),
                        getConfiguration().getEventPatternFile());
                String eventPattern = IOUtils.toString(s, Charset.defaultCharset());
                builder.eventPattern(eventPattern);
            } else {
                String eventPattern = exchange.getIn().getHeader(EventbridgeConstants.EVENT_PATTERN, String.class);
                builder.eventPattern(eventPattern);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            PutRuleResponse result;
            try {
                result = eventbridgeClient.putRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Put Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void putTargets(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutTargetsRequest) {
                PutTargetsResponse result;
                try {
                    result = eventbridgeClient.putTargets((PutTargetsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("PutTargets command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutTargetsRequest.Builder builder = PutTargetsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.rule(ruleName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.TARGETS))) {
                Collection<Target> targets = exchange.getIn().getHeader(EventbridgeConstants.TARGETS, Collection.class);
                builder.targets(targets);
            } else {
                throw new IllegalArgumentException("At least one targets must be specified");
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            PutTargetsResponse result;
            try {
                result = eventbridgeClient.putTargets(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Put Targets command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @Override
    public EventbridgeEndpoint getEndpoint() {
        return (EventbridgeEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
