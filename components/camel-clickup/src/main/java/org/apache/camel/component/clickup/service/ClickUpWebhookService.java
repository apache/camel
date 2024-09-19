package org.apache.camel.component.clickup.service;

import org.apache.camel.Message;
import org.apache.camel.component.clickup.ClickUpEndpoint;
import org.apache.camel.component.clickup.InvalidMessageSignatureException;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.errors.WebhookAlreadyExistsException;
import org.apache.camel.component.clickup.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

public class ClickUpWebhookService {

    private static final Logger LOG = LoggerFactory.getLogger(ClickUpWebhookService.class);

    private final ClickUpService clickUpService;

    public ClickUpWebhookService(ClickUpService clickUpService) {
        this.clickUpService = clickUpService;
    }

    public Webhook registerWebhook(Long workspaceId, String endpointUrl, Set<String> events) {
        try {
            return this.clickUpService.createWebhook(workspaceId, endpointUrl, events);
        } catch (WebhookAlreadyExistsException e) {
            LOG.info("Another webhook with the same configuration already exists, trying to reuse it...");

            Set<Webhook> webhooks = this.clickUpService.getWebhooks(workspaceId);

            Optional<Webhook> matchingWebhook = webhooks.stream()
                    .filter(webhook -> webhook.matchesConfiguration(endpointUrl, events))
                    .findFirst();

            if (matchingWebhook.isEmpty()) {
                throw new RuntimeException("Cannot find the matching webhook.", e);
            }

            LOG.info("Found webhook {} with the same configuration, reusing it", matchingWebhook.get().getId());

            return matchingWebhook.get();
        }
    }


    public static void validateMessageSignature(Message message, String sharedSecret) {
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
