package org.apache.camel.scr;

import org.apache.camel.*;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.osgi.framework.BundleContext;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

public class ConcreteCamelRunner extends AbstractCamelRunner implements LifecycleStrategy {

    protected int camelContextStarted = 0;
    protected int camelContextStopped = 0;
    protected int routeAdded = 0;

    public Map<String, String> getDefaultProperties() {
        // Set default properties
        Map<String,String> defaultProps = new HashMap<>();
        defaultProps.put("camelContextId", "camel-runner-test");
        defaultProps.put("camelRouteId", "test/direct-mock");
        defaultProps.put("active", "true");
        defaultProps.put("from", "direct:start");
        defaultProps.put("to", "mock:end");
        defaultProps.put("messageOk", "Success");
        defaultProps.put("messageError", "Failure");
        defaultProps.put("maximumRedeliveries", "0");
        defaultProps.put("redeliveryDelay", "1000");
        defaultProps.put("backOffMultiplier", "2");
        defaultProps.put("maximumRedeliveryDelay", "60000");
        return defaultProps;
    }

    @Override
    protected void createCamelContext(BundleContext bundleContext, Map<String, String> props) {
        super.createCamelContext(bundleContext, props);
        context.addLifecycleStrategy(this);
    }

    @Override
    public List<RoutesBuilder> getRouteBuilders() {
        List<RoutesBuilder> routesBuilders = new ArrayList<>();
        routesBuilders.add(new TestRouteBuilder());
        routesBuilders.add(new TestRouteBuilder2());
        return routesBuilders;
    }

    @Override
    public void onContextStart(CamelContext camelContext) throws VetoCamelContextStartException {
        camelContextStarted++;
    }

    @Override
    public void onContextStop(CamelContext camelContext) {
        camelContextStopped++;
    }

    @Override
    public void onComponentAdd(String s, Component component) {}

    @Override
    public void onComponentRemove(String s, Component component) {}

    @Override
    public void onEndpointAdd(Endpoint endpoint) {}

    @Override
    public void onEndpointRemove(Endpoint endpoint) {}

    @Override
    public void onServiceAdd(CamelContext camelContext, Service service, Route route) {}

    @Override
    public void onServiceRemove(CamelContext camelContext, Service service, Route route) {}

    @Override
    public void onRoutesAdd(Collection<Route> routes) {
        routeAdded++;
    }

    @Override
    public void onRoutesRemove(Collection<Route> routes) {}

    @Override
    public void onRouteContextCreate(RouteContext routeContext) {}

    @Override
    public void onErrorHandlerAdd(RouteContext routeContext, Processor processor, ErrorHandlerFactory errorHandlerFactory) {}

    @Override
    public void onErrorHandlerRemove(RouteContext routeContext, Processor processor, ErrorHandlerFactory errorHandlerFactory) {}

    @Override
    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPoolExecutor, String s, String s2, String s3, String s4) {}

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPoolExecutor) {}
}
