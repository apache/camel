/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jetty;

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.Test;

/**
 * Unit test for jetty http binding ref option.
 */
public class JettyHttpBindingRefTest extends BaseJettyTest {

    @Test
    public void testDefaultJettyHttpBinding() throws Exception {
        Object out = template.requestBody("jetty:http://localhost:{{port}}/myapp/myservice?jettyHttpBindingRef=default", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));

        try {
            template.requestBody("jetty:http://localhost:{{port}}/myapp/myotherservice", "Hello World");
            fail();
        } catch (CamelExecutionException e) {
            assertNotNull(e.getCause());
            assertTrue(e.getCause() instanceof HttpOperationFailedException);

            assertFalse("Not exactly the message the server returned.".equals(((HttpOperationFailedException) e.getCause()).getResponseBody()));
        }
    }

    @Test
    public void testCustomJettyHttpBinding() throws Exception {
        
        Object out = template.requestBody("jetty:http://localhost:{{port}}/myapp/myotherservice?jettyHttpBindingRef=myownbinder", "Hello World");
        assertEquals("Not exactly the message the server returned.", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("default", new DefaultJettyHttpBinding());
        jndi.bind("myownbinder", new MyJettyHttpBinding());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("jetty:http://localhost:{{port}}/myapp/myservice").transform().constant("Bye World");

                from("jetty:http://localhost:{{port}}/myapp/myotherservice").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalStateException("Not implemented");
                    }
                });
            }
        };
    }

    // START SNIPPET: e1
    public class MyJettyHttpBinding extends DefaultJettyHttpBinding {
        @Override
        protected void populateResponse(Exchange exchange, JettyContentExchange httpExchange, Message in,
                                        HeaderFilterStrategy strategy, int responseCode) throws IOException {

            Message answer = exchange.getOut();

            answer.setHeaders(in.getHeaders());
            answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
            answer.setBody("Not exactly the message the server returned.");
        }
    }
    // END SNIPPET: e1

}

