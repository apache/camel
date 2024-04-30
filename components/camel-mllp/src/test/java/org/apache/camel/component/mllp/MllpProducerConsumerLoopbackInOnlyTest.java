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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class MllpProducerConsumerLoopbackInOnlyTest extends CamelTestSupport {

    @EndpointInject("direct://source")
    ProducerTemplate source;

    @EndpointInject("mock://received-and-processed")
    MockEndpoint receivedAndProcessed;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(false);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        String mllpHost = "localhost";
        int mllpPort = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder[] {
                new RouteBuilder() {

                    @Override
                    public void configure() {
                        fromF("mllp://%s:%d?autoAck=false&exchangePattern=InOnly", mllpHost, mllpPort)
                                .convertBodyTo(String.class)
                                .to(receivedAndProcessed);
                    }
                },

                new RouteBuilder() {

                    @Override
                    public void configure() {
                        from(source.getDefaultEndpoint())
                                .toF("mllp://%s:%d?exchangePattern=InOnly", mllpHost, mllpPort)
                                .setBody(header(MllpConstants.MLLP_ACKNOWLEDGEMENT));
                    }
                }
        };
    }

    @Test
    public void testLoopbackWithOneMessage() throws Exception {
        String testMessage = Hl7TestMessageGenerator.generateMessage();
        receivedAndProcessed.expectedBodiesReceived(testMessage);

        String acknowledgement = source.requestBody((Object) testMessage, String.class);
        assertThat("Should receive no acknowledgment for message 1", acknowledgement, CoreMatchers.nullValue());

        MockEndpoint.assertIsSatisfied(context, 60, TimeUnit.SECONDS);
    }
}
