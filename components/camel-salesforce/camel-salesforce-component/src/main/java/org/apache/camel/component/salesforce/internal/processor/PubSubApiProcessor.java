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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.IOException;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.pubsub.PublishResult;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSubApiProcessor extends AbstractSalesforceProcessor {

    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private final String topic;
    private PubSubApiClient pubSubClient;

    public PubSubApiProcessor(final SalesforceEndpoint endpoint) {
        super(endpoint);
        this.topic = endpoint.getTopicName();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            if (operationName == OperationName.PUBSUB_PUBLISH) {
                processPublish(exchange, callback);
                return false;
            } else {
                throw new SalesforceException("Unknown operation: " + operationName.value(), null);
            }
        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(
                    String.format("Error processing %s: [%s] \"%s\"", operationName.value(), e.getStatusCode(), e.getMessage()),
                    e));
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e));
            callback.done(true);
            return true;
        }
    }

    private void processPublish(Exchange exchange, AsyncCallback callback) throws SalesforceException {
        try {
            LOG.debug("Publishing on topic: {}", topic);
            final List<?> body = exchange.getIn().getMandatoryBody(List.class);
            final List<PublishResult> results = pubSubClient.publishMessage(topic, body);
            exchange.getIn().setBody(results);
            callback.done(false);
        } catch (InvalidPayloadException | IOException e) {
            exchange.setException(new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e));
            callback.done(true);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.pubSubClient = new PubSubApiClient(
                endpoint.getComponent().getSession(),
                endpoint.getComponent().getLoginConfig(), endpoint.getComponent().getPubSubHost(),
                endpoint.getComponent().getPubSubPort(), 0, 0);
        ServiceHelper.startService(pubSubClient);
    }

    @Override
    public void doStop() throws Exception {
        ServiceHelper.stopService(pubSubClient);
        super.doStop();
    }
}
