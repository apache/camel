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

package org.apache.camel.component.clickup.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.Message;
import org.apache.camel.component.clickup.InvalidMessageSignatureException;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.model.errors.WebhookAlreadyExistsException;
import org.apache.camel.component.clickup.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickUpWebhookService {

    private static final Logger LOG = LoggerFactory.getLogger(ClickUpWebhookService.class);

    private final ClickUpService clickUpService;

    public ClickUpWebhookService(ClickUpService clickUpService) {
        this.clickUpService = clickUpService;
    }

    public Webhook registerWebhook(Long workspaceId, String endpointUrl, Set<String> events) {
        try {
            WebhookCreationCommand command = new WebhookCreationCommand();
            command.setEndpoint(endpointUrl);
            command.setEvents(events);

            return this.clickUpService.createWebhook(workspaceId, command);
        } catch (WebhookAlreadyExistsException e) {
            LOG.info("Another webhook with the same configuration already exists, trying to reuse it...");

            Set<Webhook> webhooks = this.clickUpService.getWebhooks(workspaceId);

            Optional<Webhook> matchingWebhook = webhooks.stream()
                    .filter(webhook -> webhook.matchesConfiguration(endpointUrl, events))
                    .findFirst();

            if (matchingWebhook.isEmpty()) {
                throw new RuntimeException("Cannot find the matching webhook.", e);
            }

            LOG.info(
                    "Found webhook {} with the same configuration, reusing it",
                    matchingWebhook.get().getId());

            return matchingWebhook.get();
        }
    }

    public void validateMessageSignature(Message message, String sharedSecret) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        if (sharedSecret == null) {
            throw new IllegalArgumentException("sharedSecret cannot be null");
        }

        String signature = (String) message.getHeader("x-signature");
        if (signature == null) {
            throw new RuntimeException("The webhook HTTP request doesn't contain the 'x-signature' header.");
        }

        String messageBody;
        try (InputStream body = message.getBody(InputStream.class)) {
            messageBody = new String(body.readAllBytes());

            LOG.debug("Message body {} to be validated against the given signature {}", messageBody, signature);

            body.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String calculatedSignature = Utils.computeMessageHMAC(messageBody, sharedSecret);

        signature = signature.toLowerCase();
        calculatedSignature = calculatedSignature.toLowerCase();

        boolean signatureMatches = calculatedSignature.equals(signature);
        if (!signatureMatches) {
            LOG.debug("The message signature {} does not match {}.", signature, calculatedSignature);

            throw new InvalidMessageSignatureException(messageBody, signature, calculatedSignature);
        }

        LOG.debug("The message signature {} matches {}.", signature, calculatedSignature);
    }
}
