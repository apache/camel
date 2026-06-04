package camel.example;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class Greeter implements Processor {

    private String message;

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        exchange.getIn().setBody(message + " " + body);
    }

}