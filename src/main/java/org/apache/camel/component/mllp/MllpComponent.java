package org.apache.camel.component.mllp;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;

import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link MllpEndpoint}.
 */
public class MllpComponent extends UriEndpointComponent {
    Logger log = LoggerFactory.getLogger(this.getClass());
    
    public MllpComponent() {
        super(MllpEndpoint.class);
        log.trace( "MllpComponent()");
    }

    public MllpComponent(CamelContext context) {
        super(context, MllpEndpoint.class);
        log.trace( "MllpComponent(context: {})", context.getName() );
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        log.trace( "createEndpoint(uri: {}, remaining: {}, parameters: {})", uri, remaining, parameters);
        Endpoint endpoint = new MllpEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        log.trace( "doStart()");
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace( "doStop()");
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.trace( "doSuspend()");

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.trace( "doResume()");

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.trace( "doShutdown()");

        super.doShutdown();
    }

}
