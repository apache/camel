package org.apache.camel.component.clickup.model.errors;

public class WebhookAlreadyExistsException extends RuntimeException {

    public WebhookAlreadyExistsException() {
        super("Another webhook with the same characteristics already exists.");
    }

}
