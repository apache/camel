package org.apache.camel.impl.engine;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.support.service.ServiceHelper;

public class EmptyProducerCache extends DefaultProducerCache {

    private final Object source;
    private final ExtendedCamelContext ecc;

    public EmptyProducerCache(Object source, CamelContext camelContext) {
        super(source, camelContext, -1);
        this.source = source;
        this.ecc = camelContext.adapt(ExtendedCamelContext.class);
        setExtendedStatistics(false);
    }

    @Override
    public AsyncProducer acquireProducer(Endpoint endpoint) {
        // always create a new producer
        AsyncProducer answer;
        try {
            answer = endpoint.createAsyncProducer();
            boolean startingRoutes = ecc.isSetupRoutes() || ecc.getRouteController().isStartingRoutes();
            if (startingRoutes && answer.isSingleton()) {
                // if we are currently starting a route, then add as service and enlist in JMX
                // - but do not enlist non-singletons in JMX
                // - note addService will also start the service
                getCamelContext().addService(answer);
            } else {
                // must then start service so producer is ready to be used
                ServiceHelper.startService(answer);
            }
        } catch (Exception e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
        return answer;
    }

    @Override
    public void releaseProducer(Endpoint endpoint, AsyncProducer producer) {
        // stop and shutdown the producer as its not cache or reused
        ServiceHelper.stopAndShutdownService(producer);
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public String toString() {
        return "EmptyProducerCache for source: " + source;
    }

}
