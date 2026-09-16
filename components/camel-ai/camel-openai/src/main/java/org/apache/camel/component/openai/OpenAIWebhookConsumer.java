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
package org.apache.camel.component.openai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.Headers;
import com.openai.errors.InvalidWebhookSignatureException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.webhooks.UnwrapWebhookEvent;
import com.openai.models.webhooks.WebhookVerificationParams;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the webhook events OpenAI sends: it serves one HTTP endpoint through the REST consumer factory of the
 * runtime (platform-http unless another component is configured), verifies the signature of each request against the
 * webhook secret, and runs the route with the parsed event.
 */
public class OpenAIWebhookConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIWebhookConsumer.class);

    private static final String[] SIGNATURE_HEADERS = { "webhook-id", "webhook-timestamp", "webhook-signature" };

    /**
     * The SDK client requires a credential even when it is only used to verify a signature, which needs the webhook
     * secret alone. A route that just receives events therefore does not have to configure an API key.
     */
    private static final String VERIFICATION_ONLY_API_KEY = "openai-webhook-verification";

    private final OpenAIEndpoint endpoint;

    private OpenAIClient client;
    private Consumer httpConsumer;

    public OpenAIWebhookConsumer(OpenAIEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        OpenAIConfiguration configuration = endpoint.getConfiguration();
        String secret = configuration.getWebhookSecret();
        if (ObjectHelper.isEmpty(secret)) {
            throw new IllegalArgumentException(
                    "webhookSecret is required by the webhook operation: it is the signing secret of the endpoint in"
                                               + " the OpenAI dashboard, and without it an event cannot be told from a forged request");
        }

        RestConsumerFactory factory = resolveRestConsumerFactory();
        if (factory == null) {
            throw new IllegalStateException(
                    "No RestConsumerFactory found. The webhook operation needs camel-platform-http, or another HTTP"
                                            + " server component named in httpServerComponent or in the rest configuration");
        }

        client = createVerificationClient(secret);

        String path = configuration.getWebhookPath();
        RestConfiguration restConfiguration = endpoint.getCamelContext().getRestConfiguration();
        httpConsumer = factory.createConsumer(endpoint.getCamelContext(), this::onWebhookRequest,
                "POST", path, null, "application/json", null, restConfiguration, Collections.emptyMap());
        endpoint.configureNestedConsumer(httpConsumer);
        ServiceHelper.startService(httpConsumer);

        LOG.debug("OpenAI webhook consumer listening on POST {}", path);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(httpConsumer);
        httpConsumer = null;
        if (client != null) {
            client.close();
            client = null;
        }
        super.doStop();
    }

    /**
     * Answers the HTTP request of OpenAI. The route never sees an event whose signature did not verify, and it runs on
     * an exchange of its own, so that no header of the request reaches it.
     */
    private void onWebhookRequest(Exchange httpExchange) {
        byte[] payload;
        try {
            payload = readPayload(httpExchange);
        } catch (IOException e) {
            LOG.debug("Rejected an OpenAI webhook request: {}", e.getMessage());
            respond(httpExchange, 413, e.getMessage());
            return;
        }

        UnwrapWebhookEvent event;
        try {
            event = client.webhooks().unwrap(verificationParams(httpExchange.getMessage(), payload));
        } catch (InvalidWebhookSignatureException | IllegalArgumentException | OpenAIInvalidDataException e) {
            // a missing header, a signature that does not match, an event older than the tolerance of the SDK
            // (5 minutes), or a body that is not an event
            LOG.debug("Rejected an OpenAI webhook request: {}", e.getMessage());
            respond(httpExchange, 400, e.getMessage());
            return;
        }

        Exchange exchange = createExchange(false);
        try {
            Message message = exchange.getMessage();
            message.setBody(event);
            describeEvent(message, payload);

            getProcessor().process(exchange);
            if (exchange.getException() != null) {
                throw exchange.getException();
            }
            respond(httpExchange, 200, "");
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing an OpenAI webhook event", exchange, e);
            // OpenAI retries an event the route did not handle
            respond(httpExchange, 500, "Error processing the event");
        } finally {
            releaseExchange(exchange, false);
        }
    }

    /**
     * Puts the event type, the event id, the id of the object it is about and the creation time in headers, read from
     * the payload rather than from the parsed event, so that an event type the SDK does not know yet is described the
     * same way.
     */
    private static void describeEvent(Message message, byte[] payload) {
        JsonObject json;
        try {
            json = (JsonObject) Jsoner.deserialize(new String(payload, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // the SDK parsed the payload already, so this is not expected; the event stays in the body
            LOG.debug("Cannot read the fields of a verified OpenAI webhook event: {}", e.getMessage());
            return;
        }
        message.setHeader(OpenAIConstants.WEBHOOK_EVENT_TYPE, json.getString("type"));
        message.setHeader(OpenAIConstants.WEBHOOK_EVENT_ID, json.getString("id"));
        message.setHeader(OpenAIConstants.WEBHOOK_CREATED_AT, json.getLong("created_at"));
        Object data = json.get("data");
        if (data instanceof JsonObject object) {
            message.setHeader(OpenAIConstants.WEBHOOK_OBJECT_ID, object.getString("id"));
        }
    }

    private WebhookVerificationParams verificationParams(Message message, byte[] payload) {
        Headers.Builder headers = Headers.builder();
        for (String name : SIGNATURE_HEADERS) {
            String value = message.getHeader(name, String.class);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return WebhookVerificationParams.builder().payload(payload).headers(headers.build()).build();
    }

    private byte[] readPayload(Exchange exchange) throws IOException {
        int max = endpoint.getConfiguration().getWebhookMaxPayloadSize();
        Object body = exchange.getMessage().getBody();
        byte[] payload;
        if (body instanceof byte[] bytes) {
            payload = bytes;
        } else if (body instanceof InputStream stream) {
            try (InputStream in = stream) {
                payload = in.readNBytes(max + 1);
            }
        } else {
            String text = exchange.getMessage().getBody(String.class);
            payload = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
        }
        if (payload.length > max) {
            throw new IOException("The request body is larger than webhookMaxPayloadSize (" + max + " bytes)");
        }
        return payload;
    }

    private OpenAIClient createVerificationClient(String secret) throws Exception {
        String apiKey = endpoint.resolveApiKey();
        return OpenAIOkHttpClient.builder()
                .apiKey(ObjectHelper.isNotEmpty(apiKey) ? apiKey : VERIFICATION_ONLY_API_KEY)
                .webhookSecret(secret)
                .build();
    }

    private static void respond(Exchange exchange, int code, String body) {
        Message message = exchange.getMessage();
        message.setBody(body);
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, code);
        message.setHeader(Exchange.CONTENT_TYPE, "text/plain");
    }

    /**
     * The HTTP server that serves the webhook: the configured one, the one of the rest configuration, platform-http
     * when it is on the classpath, or the only one there is.
     */
    private RestConsumerFactory resolveRestConsumerFactory() {
        CamelContext context = endpoint.getCamelContext();

        String serverComponent = endpoint.getConfiguration().getHttpServerComponent();
        if (serverComponent != null) {
            return namedRestConsumerFactory(serverComponent, "httpServerComponent");
        }

        RestConfiguration restConfiguration = context.getRestConfiguration();
        String restComponent = restConfiguration != null ? restConfiguration.getComponent() : null;
        if (restComponent != null) {
            return namedRestConsumerFactory(restComponent, "the rest configuration");
        }

        if (context.getComponent("platform-http", false) instanceof RestConsumerFactory factory) {
            return factory;
        }

        Set<RestConsumerFactory> factories = new LinkedHashSet<>();
        for (String name : context.getComponentNames()) {
            if (context.getComponent(name, false) instanceof RestConsumerFactory factory) {
                factories.add(factory);
            }
        }
        factories.addAll(context.getRegistry().findByType(RestConsumerFactory.class));
        if (factories.size() == 1) {
            return factories.iterator().next();
        }
        if (factories.size() > 1) {
            LOG.debug("Not choosing between the {} RestConsumerFactory found; name one in httpServerComponent",
                    factories.size());
        }
        return null;
    }

    private RestConsumerFactory namedRestConsumerFactory(String name, String source) {
        Component component = endpoint.getCamelContext().getComponent(name, true);
        if (component instanceof RestConsumerFactory factory) {
            return factory;
        }
        throw new IllegalArgumentException(
                "The component " + name + " of " + source + " is not a RestConsumerFactory, so it cannot serve the"
                                           + " webhook endpoint");
    }
}
