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
package org.apache.camel.component.soroushbot.component;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.soroushbot.models.MinorType;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.support.SoroushBotTestSupport;
import org.apache.camel.component.soroushbot.support.SoroushBotWS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerMaxConnectionRetryReachedTest extends SoroushBotTestSupport {

    @EndpointInject("direct:soroush")
    org.apache.camel.Endpoint endpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        SoroushBotWS.clear();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:soroush").to("soroush://" + SoroushAction.sendMessage + "/retry 3?maxConnectionRetry=2")
                        .to("mock:soroush");
            }
        };
    }

    @Test
    public void retryOnFailure() throws Exception {
        SoroushMessage body = new SoroushMessage();
        body.setType(MinorType.TEXT);
        body.setFrom("b1");
        body.setTo("u1");
        boolean exceptionOccur = false;
        try {
            context().createProducerTemplate().sendBody(endpoint, body);
        } catch (CamelExecutionException ex) {
            exceptionOccur = true;
        }
        assertTrue(exceptionOccur, "an exception happend due to maximumConnetion try reached!");
        MockEndpoint mockEndpoint = getMockEndpoint("mock:soroush");
        mockEndpoint.setAssertPeriod(1000);
        mockEndpoint.setExpectedMessageCount(0);
        mockEndpoint.assertIsSatisfied();
        assertEquals(0, SoroushBotWS.getReceivedMessages().size(), "message did not send");
    }
}
