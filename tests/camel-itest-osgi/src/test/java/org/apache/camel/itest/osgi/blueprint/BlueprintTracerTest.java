package org.apache.camel.itest.osgi.blueprint;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.DefaultTraceEventMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class BlueprintTracerTest extends OSGiBlueprintTestSupport {

    private String name = BlueprintTracerTest.class.getName();

    @Test
    public void testTracer() throws Exception {
        // start bundle
        getInstalledBundle(name).start();

        // must use the camel context from osgi
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=" + name + ")", 30000);

        ProducerTemplate myTemplate = ctx.createProducerTemplate();
        myTemplate.start();

        // do our testing
        MockEndpoint result = ctx.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedMessageCount(1);

        MockEndpoint tracer = ctx.getEndpoint("mock:traced", MockEndpoint.class);

        myTemplate.sendBody("direct:start", "Hello World");

        result.assertIsSatisfied();

        DefaultTraceEventMessage em = tracer.getReceivedExchanges().get(0).getIn().getBody(DefaultTraceEventMessage.class);
        assertEquals("Hello Camel", em.getBody());

        assertEquals("String", em.getBodyType());
        assertEquals(null, em.getCausedByException());
        assertNotNull(em.getExchangeId());
        assertNotNull(em.getShortExchangeId());
        assertNotNull(em.getExchangePattern());
        assertEquals("direct://start", em.getFromEndpointUri());
        // there is always a breadcrumb header
        assertNotNull(em.getHeaders());
        assertNotNull(em.getProperties());
        assertNull(em.getOutBody());
        assertNull(em.getOutBodyType());
        assertNull(em.getOutHeaders());
        assertNull(em.getPreviousNode());
        assertNotNull(em.getToNode());
        assertNotNull(em.getTimestamp());

        myTemplate.stop();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/test.xml", BlueprintTracerTest.class.getResource("blueprint-29.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, BlueprintTracerTest.class.getName())
                        .set(Constants.BUNDLE_VERSION, "1.0.0")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .add(MyTraceEventHandler.class)
                        .build()).noStart(),

                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint"));

        return options;
    }

}
