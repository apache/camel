package org.apache.camel.processor.interceptor;

import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.StreamCache;
import org.apache.camel.model.InterceptorRef;
import org.apache.camel.model.InterceptorType;
import org.apache.camel.processor.DelegateProcessor;

public class StreamCachingInterceptorTest extends ContextTestSupport {
	
    private MockEndpoint a;
    private MockEndpoint b;
    
    public void testConvertStreamSourceWithRouteBuilderStreamCaching() throws Exception {
        a.expectedMessageCount(1);
        
        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:a", message);

        assertMockEndpointsSatisifed();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }
    
    public void testConvertStreamSourceWithRouteOnlyStreamCaching() throws Exception {
        b.expectedMessageCount(1);
        
        StreamSource message = new StreamSource(new StringReader("<hello>world!</hello>"));
        template.sendBody("direct:b", message);

        assertMockEndpointsSatisifed();
        assertTrue(b.assertExchangeReceived(0).getIn().getBody() instanceof StreamCache);
    }
    
    public void testIgnoreAlreadyRereadable() throws Exception {
        a.expectedMessageCount(1);
        
        template.sendBody("direct:a", "<hello>world!</hello>");

        assertMockEndpointsSatisifed();
        assertTrue(a.assertExchangeReceived(0).getIn().getBody() instanceof String);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = getMockEndpoint("mock:a");
        b = getMockEndpoint("mock:b");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
        	
            public void configure() {
                //Stream caching for a single route...
                from("direct:a").streamCaching().to("mock:a");
                
                //... or for all the following routes in this builder
                streamCaching();
                from("direct:b").to("mock:b");
            }
        };
    }
    
    public void testNoStreamCaching() throws Exception {
        List<InterceptorType> interceptors = new LinkedList<InterceptorType>();
        InterceptorRef streamCache = new InterceptorRef(new StreamCachingInterceptor());
        interceptors.add(streamCache);
        interceptors.add(new InterceptorRef(new DelegateProcessor()));
        StreamCachingInterceptor.noStreamCaching(interceptors);
        assertEquals(1, interceptors.size());
        assertFalse(interceptors.contains(streamCache));
    }
}
