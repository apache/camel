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

import java.net.SocketTimeoutException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for using http client SO timeout
 */
public class HttpProducerSOTimeoutTest extends BaseJettyTest {

    @Test
    public void testSendWithSOTimeoutNoTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String out = template.requestBody("http://localhost:{{port}}/myservice?responseTimeout=5000", null, String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSendWithSOTimeoutTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        try {
            // we use a timeout of 1 second
            template.requestBody("http://localhost:{{port}}/myservice?responseTimeout=1000", null, String.class);
            fail("Should throw an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(SocketTimeoutException.class, e.getCause());
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/myservice")
                        // but we wait for 2 sec before reply is sent back
                        .delay(2000).transform().constant("Bye World").to("mock:result");
            }
        };
    }
}
