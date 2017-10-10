package org.apache.camel.component.aws.sqs;

import org.apache.camel.Exchange;

public class NullStrategy implements StringValueFromExchangeStrategy {

    @Override
    public String value(Exchange exchange) {
        return null;
    }

}
