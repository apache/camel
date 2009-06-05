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

import java.io.InputStream;
import java.net.SocketTimeoutException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for using http client SO timeout
 *
 * @version $Revision$
 */
public class HttpProducerSOTimeoutTest extends ContextTestSupport {

    public void testSendWithSOTimeoutNoTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        InputStream out = (InputStream) template.requestBody("http://localhost:9080/myservice?httpClient.soTimeout=5000", null);        
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));

        assertMockEndpointsSatisfied();
    }

    public void testSendWithSOTimeoutTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        try {
            // we use a timeout of 1 second
            template.requestBody("http://localhost:9080/myservice?httpClient.soTimeout=1000", null);
            fail("Should throw an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(SocketTimeoutException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://0.0.0.0:9080/myservice")
                    // but we wait for 2 sec before reply is sent back
                    .delayer(2000)
                    .transform().constant("Bye World").to("mock:result");
            }
        };
    }
}
