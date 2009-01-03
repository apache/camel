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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpProducer;

/**
 * Unit test for http client options.
 */
public class JettyHttpClientOptionsTest extends ContextTestSupport {

    public void testCustomHttpBinding() throws Exception {
        // assert jetty was configured with our timeout
        JettyHttpEndpoint jettyEndpoint = (JettyHttpEndpoint) context.getEndpoint("jetty:http://localhost:8080/myapp/myservice?httpClient.soTimeout=5555");
        assertNotNull("Jetty endpoint should not be null ", jettyEndpoint);
        HttpProducer producer = (HttpProducer)jettyEndpoint.createProducer();
        assertEquals("Get the wrong http client parameter", 5555, producer.getHttpClient().getParams().getSoTimeout());

        // send and receive
        Object out = template.requestBody("http://localhost:8080/myapp/myservice", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:8080/myapp/myservice?httpClient.soTimeout=5555").transform().constant("Bye World");
            }
        };
    }

}