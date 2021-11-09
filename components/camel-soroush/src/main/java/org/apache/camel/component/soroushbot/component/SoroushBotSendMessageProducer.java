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
package org.apache.camel.component.soroushbot.component;

import java.io.IOException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.service.SoroushService;
import org.apache.camel.component.soroushbot.utils.MaximumConnectionRetryReachedException;
import org.apache.camel.component.soroushbot.utils.SoroushException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this Producer is responsible for URIs of type {@link SoroushAction#sendMessage} to send message to SoroushAPI. it
 * will be instantiated for URIs like "soroush:sendMessage/[token]
 */
public class SoroushBotSendMessageProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SoroushBotSendMessageProducer.class);
    SoroushBotEndpoint endpoint;
    ObjectMapper objectMapper = new ObjectMapper();

    private static class SoroushMessagePayload {
        final SoroushMessage message;
        Exception exception;

        public SoroushMessagePayload(SoroushMessage message) {
            this.message = message;
        }
    }

    public SoroushBotSendMessageProducer(SoroushBotEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SoroushMessage message = exchange.getIn().getBody(SoroushMessage.class);
        // if autoUploadFile is true try to upload files inside the message
        if (endpoint.isAutoDownload()) {
            endpoint.handleFileUpload(message);
        }
        sendMessage(message);
    }

    private boolean doSendMessage(SoroushMessagePayload payload) {
        try {
            Response response = endpoint.getSendMessageTarget().request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.entity(objectMapper.writeValueAsString(payload.message), MediaType.APPLICATION_JSON_TYPE));
            SoroushService.get().assertSuccessful(response, payload.message);

            return true;
        } catch (SoroushException | IOException | ProcessingException e) {
            LOG.warn("failed to send message: {}", payload.message, e);

            payload.exception = e;
        }

        return false;
    }

    /**
     * @throws MaximumConnectionRetryReachedException if can not connect to soroush after retry
     *                                                {@link SoroushBotEndpoint#getMaxConnectionRetry()} times
     * @throws SoroushException                       if soroush response code wasn't 200
     */
    private void sendMessage(SoroushMessage message) throws MaximumConnectionRetryReachedException {

        BlockingTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationBudget()
                        .withMaxIterations(endpoint.getMaxConnectionRetry() + 1)
                        .withBackOffStrategy(endpoint.getBackOffStrategyHelper())
                        .build())
                .withName("send-message")
                .build();

        SoroushMessagePayload payload = new SoroushMessagePayload(message);

        if (!task.run(this::doSendMessage, payload)) {
            if (payload.exception != null) {
                throw new MaximumConnectionRetryReachedException(
                        "Failed to send message. maximum retry limit reached. aborting... message: "
                                                                 + message,
                        payload.exception, message);
            }
        }
    }

}
