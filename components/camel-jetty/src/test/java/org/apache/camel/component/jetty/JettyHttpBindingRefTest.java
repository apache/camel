package org.apache.camel.component.jetty;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.DefaultHttpBinding;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for http binding ref option.
 */
public class JettyHttpBindingRefTest extends ContextTestSupport {

    public void testDefaultHttpBinding() throws Exception {
        Object out = template.requestBody("http://localhost:8080/myapp/myservice", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testCustomHttpBinding() throws Exception {
        Object out = template.requestBody("http://localhost:8081/myapp/myotherservice", "Hello World");
        assertEquals("Something went wrong but we dont care", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("default", new DefaultHttpBinding());
        jndi.bind("myownbinder", new MyHttpBinding());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("jetty:http://localhost:8080/myapp/myservice?httpBindingRef=default").transform().constant("Bye World");

                from("jetty:http://localhost:8081/myapp/myotherservice?httpBindingRef=myownbinder").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalStateException("Not implemented");
                    }
                });
            }
        };
    }

    // START SNIPPET: e1
    public class MyHttpBinding extends DefaultHttpBinding {

        @Override
        public void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException {
            // we override the doWriteExceptionResponse as we only want to alter the binding how exceptions is
            // written back to the client. 

            // we just return HTTP 200 so the client thinks its okay
            response.setStatus(200);
            // and we return this fixed text
            response.getWriter().write("Something went wrong but we dont care");
        }
    }
    // END SNIPPET: e1

}

