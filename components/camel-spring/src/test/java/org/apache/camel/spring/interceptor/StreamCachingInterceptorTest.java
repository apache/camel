package org.apache.camel.spring.interceptor;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.StreamCache;

/**
 * Test case for enabling stream caching through XML
 */
public class StreamCachingInterceptorTest extends ContextTestSupport {
    
    public void testStreamCachingInterceptorEnabled() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);

        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisifed();
        //assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/interceptor/streamCachingOnRoute.xml");
    }

}
