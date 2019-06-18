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

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.soroushbot.models.MinorType;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.support.SoroushBotTestSupport;
import org.apache.camel.component.soroushbot.support.SoroushBotWS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExponentialBackOffProducerConnectionRetryTest extends SoroushBotTestSupport {

    @EndpointInject("direct:soroush")
    org.apache.camel.Endpoint endpoint;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        SoroushBotWS.clear();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:soroush").to("soroush://" + SoroushAction.sendMessage + "/retry 5?maxConnectionRetry=5"
                        + "&retryWaitingTime=500&retryExponentialCoefficient=2&backOffStrategy=exponential")
                        .to("mock:afterAllRetry")
                        .to("mock:beforeAllRetry");
            }
        };
    }

    @Test
    public void retryOnFailure() throws Exception {
        SoroushMessage body = new SoroushMessage();
        body.setType(MinorType.TEXT);
        body.setFrom("b1");
        body.setTo("u1");
        //send message in other thread
        new Thread(() -> context().createProducerTemplate().sendBody(endpoint, body)).start();
        MockEndpoint beforeAllRetry = getMockEndpoint("mock:beforeAllRetry");
        //cause this thread to sleep .5+1+2+4 second. even in this time,no message should be sent
        beforeAllRetry.setAssertPeriod(7000);
        beforeAllRetry.setExpectedCount(0);
        beforeAllRetry.assertIsSatisfied();
        MockEndpoint afterAllRetry = getMockEndpoint("mock:afterAllRetry");
        afterAllRetry.setExpectedMessageCount(1);
        //cause this thread to sleep an addition of 1 second, during this time,the message must be sent to the server
        afterAllRetry.setAssertPeriod(1000);
        afterAllRetry.assertIsSatisfied();
        Assert.assertEquals("message sent successfully", SoroushBotWS.getReceivedMessages().get(0), body);
    }
}
