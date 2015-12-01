package org.apache.camel.component.kinesis;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisComponent extends DefaultComponent {

    public KinesisComponent() {
    }

    public KinesisComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected KinesisEndpoint createEndpoint(String uri,
                                           String remaining,
                                           Map<String, Object> params) throws Exception {
        KinesisEndpoint endpoint = new KinesisEndpoint(uri, remaining, this);
        setProperties(endpoint, params);
        return endpoint;
    }
}
