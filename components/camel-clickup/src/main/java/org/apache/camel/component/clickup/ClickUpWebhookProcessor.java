package org.apache.camel.component.clickup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.*;
import org.apache.camel.component.clickup.model.Event;
import org.apache.camel.component.clickup.service.ClickUpWebhookService;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;

import java.io.IOException;
import java.io.InputStream;

public class ClickUpWebhookProcessor extends AsyncProcessorSupport implements AsyncProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsyncProcessor nextProcessor;

    private final ClickUpWebhookService clickUpWebhookService;

    private String webhookSecret;

    public ClickUpWebhookProcessor(Processor nextProcessor, ClickUpWebhookService clickUpWebhookService, String webhookSecret) {
        this.nextProcessor = AsyncProcessorConverterHelper.convert(nextProcessor);
        this.clickUpWebhookService = clickUpWebhookService;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message incomingMessage = exchange.getIn();

        try {
            this.clickUpWebhookService.validateMessageSignature(incomingMessage, this.webhookSecret);
        } catch (RuntimeCamelException e) {
            exchange.setException(e);

            callback.done(true);

            return true;
        }

        Event event;
        try (InputStream body = exchange.getIn().getBody(InputStream.class)) {
            event = MAPPER.readValue(body, Event.class);
        } catch (IOException e) {
            exchange.setException(e);

            callback.done(true);

            return true;
        }

        exchange.getMessage().setBody(event);

        // This is needed to adhere to the delegate pattern adopted by the camel-webhook meta-component
        return nextProcessor.process(exchange, doneSync -> {
            exchange.getMessage().setBody(null);

            callback.done(doneSync);
        });
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

}
