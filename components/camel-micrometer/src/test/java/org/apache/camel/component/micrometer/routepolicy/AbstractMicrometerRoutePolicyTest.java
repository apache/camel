package org.apache.camel.component.micrometer.routepolicy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.MicrometerComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

/**
 * @author Christian Ohr
 */
public class AbstractMicrometerRoutePolicyTest extends CamelTestSupport {

    protected MeterRegistry registry = new SimpleMeterRegistry();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind(MicrometerComponent.METRICS_REGISTRY, this.registry);
        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
        factory.setMeterRegistry(registry);
        context.addRoutePolicyFactory(factory);

        return context;
    }
}
