/*
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

import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for http binding ref option.
 */
public class HttpBindingRefTest extends BaseJettyTest {

    @BindToRegistry("default")
    private DefaultHttpBinding binding = new DefaultHttpBinding();

    @BindToRegistry("myownbinder")
    private MyHttpBinding bindingHttp = new MyHttpBinding();

    @Test
    public void testDefaultHttpBinding() {
        Object out = template.requestBody("http://localhost:{{port}}/myapp/myservice", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));
    }

    @Test
    public void testCustomHttpBinding() {
        Object out = template.requestBody("http://localhost:{{port}}/myapp/myotherservice", "Hello World");
        assertEquals("Something went wrong but we dont care", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());

                from("jetty:http://localhost:{{port}}/myapp/myservice?httpBindingRef=default").transform()
                        .constant("Bye World");

                from("jetty:http://localhost:{{port}}/myapp/myotherservice?httpBindingRef=myownbinder")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                throw new IllegalStateException("Not implemented");
                            }
                        });
            }
        };
    }

    // START SNIPPET: e1
    public static class MyHttpBinding extends DefaultHttpBinding {

        @Override
        public void doWriteExceptionResponse(Throwable exception, HttpServletResponse response) throws IOException {
            // we override the doWriteExceptionResponse as we only want to alter
            // the binding how exceptions is
            // written back to the client.

            // we just return HTTP 200 so the client thinks its okay
            response.setStatus(200);
            // and we return this fixed text
            response.getWriter().write("Something went wrong but we dont care");
        }
    }
    // END SNIPPET: e1

}
