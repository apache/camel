package org.apache.camel.example.transformer.demo;

import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderResponseValidator extends Validator {
    private static final Logger LOG = LoggerFactory.getLogger(OrderResponseValidator.class);

    @Override
    public void validate(Message message, DataType type) throws ValidationException {
        Object body = message.getBody();
        LOG.info("Validating message body: {}", body);
        if (!(body instanceof OrderResponse)) {
            throw new ValidationException(message.getExchange(), "Expected OrderResponse, but was " + body.getClass());
        }
        OrderResponse r = (OrderResponse)body;
        if (!r.isAccepted()) {
            throw new ValidationException(message.getExchange(), "Order was not accepted:" + r);
        }
    }

}
