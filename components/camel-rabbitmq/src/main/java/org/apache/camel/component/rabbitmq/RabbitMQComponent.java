package org.apache.camel.component.rabbitmq;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

/**
 * @author Stephen Samuel
 */
public class RabbitMQComponent extends DefaultComponent {

    public RabbitMQComponent() {
    }

    public RabbitMQComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected RabbitMQEndpoint createEndpoint(String uri,
                                              String remaining,
                                              Map<String, Object> params) throws Exception {
        RabbitMQEndpoint endpoint = new RabbitMQEndpoint(uri, remaining, this);
        setProperties(endpoint, params);
        return endpoint;
    }
}
