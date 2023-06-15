package org.apache.camel.http.common;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CamelServletTest {

    @Test
    public void testDuplicatedServletPath() {
        CamelServlet camelServlet = new CamelServlet();

        HttpCommonEndpoint httpCommonEndpoint = new HttpCommonEndpoint() {

            @Override
            public Producer createProducer() throws Exception {
                return null;
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {
                return null;
            }
        };

        DefaultCamelContext dc = new DefaultCamelContext();

        httpCommonEndpoint.setEndpointUriIfNotSpecified("rest:post://camel.apache.org");
        httpCommonEndpoint.setCamelContext(dc);

        HttpConsumer httpConsumer1 = new HttpConsumer(httpCommonEndpoint, null);
        HttpConsumer httpConsumer2 = new HttpConsumer(httpCommonEndpoint, null);

        camelServlet.connect(httpConsumer1);
        IllegalStateException illegalStateException =
                assertThrows(IllegalStateException.class, () -> camelServlet.connect(httpConsumer2));
        assertEquals("Duplicate request path for rest:post://camel.apache.org",
                illegalStateException.getMessage());
    }
}
