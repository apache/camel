package org.apache.camel.builder.script;

import java.util.Map;
import java.util.HashMap;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Tests a routing expression using Python
 */
public class PythonExpressionTest extends ContextTestSupport {
    public void testSendMatchingMessage() throws Exception {
        // Currently, this test fails because the Python expression in createRouteBuilder
        // below returns null and that is treated as 'false', therefore it's as if the
        // message didn't match the expression
        // To fix that, we need to figure out how to get the expression to return a boolean
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:unmatched").expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisifed();
    }

    public void testSendNonMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "foo");
        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisifed();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").choice().
                        // The following python expression should return a boolean
                        // but it seems to return null instead -- what's up with that?
                        when().python("request.headers['foo'] == 'bar'").to("mock:result")
                        .otherwise().to("mock:unmatched");
            }
        };
    }
}
