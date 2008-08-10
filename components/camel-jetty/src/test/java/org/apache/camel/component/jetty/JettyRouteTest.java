package org.apache.camel.component.jetty;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for wiki demonstration.
 */
public class JettyRouteTest extends ContextTestSupport {

    public void testSendToJetty() throws Exception {
        Object response = template.requestBody("http://localhost:8080/myapp/myservice", "bookid=123");
        // convert the response to a String
        String body = context.getTypeConverter().convertTo(String.class, response);
        assertEquals("<html><body>Book 123 is Camel in Action</body></html>", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("jetty:http://localhost:8080/myapp/myservice").process(new MyBookService());
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            // just get the body as a string
            String body = exchange.getIn().getBody(String.class);

            // we have access to the HttpServletRequest here and we can grab it if we need it
            HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
            assertNotNull(req);

            // for unit testing
            assertEquals("bookid=123", body);

            // send a html response
            exchange.getOut(true).setBody("<html><body>Book 123 is Camel in Action</body></html>");
        }
    }
    // END SNIPPET: e2

}
