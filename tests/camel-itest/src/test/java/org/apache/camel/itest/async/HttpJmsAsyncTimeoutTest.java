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
package org.apache.camel.itest.async;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.Registry;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class HttpJmsAsyncTimeoutTest extends HttpAsyncTestSupport {

    @Test
    public void testHttpJmsAsync() throws Exception {
        try {
            template.requestBody("http://0.0.0.0:"  + getPort() + "/myservice", "Hello World", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(504, cause.getStatusCode());
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);
        registry.bind("jms", amq);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // a lot of timeouts in the play :)

                // jetty will timeout after 2 seconds
                fromF("jetty:http://0.0.0.0:%s/myservice?continuationTimeout=2000", getPort())
                    // jms request/reply will timeout after 5 seconds
                    .to("jms:queue:foo?requestTimeout=5000");

                from("jms:queue:foo")
                    // and this one is slow and will reply after 10 seconds
                    .delayer(10000)
                    .transform(constant("Bye World"));
            }
        };
    }
}
