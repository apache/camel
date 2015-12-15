/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.apache.camel.test.Hl7MessageGenerator.generateMessage;


public class MllpProducerConsumerLoopbackTest extends CamelTestSupport {
    int mllpPort = AvailablePortFinder.getNextAvailable();

    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        RouteBuilder[] builders = new RouteBuilder[2];

        builders[0] = new RouteBuilder() {
            String routeId = "mllp-sender";

            String host = "0.0.0.0";

            public void configure() {
                fromF("direct://trigger").routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending: ${body}")
                        .toF("mllp://%s:%d", host, mllpPort)
                ;
            }
        };

        builders[1] = new RouteBuilder() {
            String routeId = "mllp-receiver";

            public void configure() {
                fromF("mllp:%d?autoAck=true", mllpPort)
                        .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                        .to("mock:result")
                        .setBody().constant("Got It")
                ;
            }
        };

        return builders;
    }

    @Test
    public void testLoopbackWithOneMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct://trigger", generateMessage());

        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);
    }

}

