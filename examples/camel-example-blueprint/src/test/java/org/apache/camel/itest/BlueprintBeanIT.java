package org.apache.camel.itest;

import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class BlueprintBeanIT extends CamelKarafTestSupport {

    /*
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-bean.xml";
    }
    */

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // return super.createCamelContext();
        // TODO:  Create either a OSGi Camel Context or a Blueprint Camel Context - not sure which would be better yet
        // CamelContext context = new DefaultCamelContext(createRegistry());
        CamelContext context = new OsgiDefaultCamelContext(bundleContext);

        // Depricated call/feature
        // context.setLazyLoadTypeConverters(isLazyLoadingTypeConverter());

        return context;

    }

    @Test
    public void testRoute() throws Exception {
        // the route is timer based, so every 5th second a message is send
        // we should then expect at least one message
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        // assert expectations
        assertMockEndpointsSatisfied();
    }



}
