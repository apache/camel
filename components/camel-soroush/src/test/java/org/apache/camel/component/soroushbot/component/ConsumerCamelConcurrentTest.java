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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.support.SoroushBotTestSupport;
import org.apache.logging.log4j.LogManager;
import org.junit.Assert;
import org.junit.Test;

public class ConsumerCamelConcurrentTest extends SoroushBotTestSupport {
    final List<String> fromOrder = new ArrayList<>();

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("soroush://" + SoroushAction.getMessage + "/10").threads().poolSize(4).process(exchange -> {
                    SoroushMessage message = exchange.getIn().getBody(SoroushMessage.class);
                    String from = message.getFrom();
                    //message from u0 (0,4,8) should be processed at the end
                    if (from.equals("u0")) {
                        Thread.sleep(1000);
                    }
                    synchronized (fromOrder) {
                        fromOrder.add(from);
                    }
                }).to("mock:MultithreadConsumerTest");
            }
        };
    }

    @Test
    public void checkEachUserGoesToSingleThread() throws InterruptedException {
        //checking is take place in createRouteBuilder
        MockEndpoint mockEndpoint = getMockEndpoint("mock:MultithreadConsumerTest");
        mockEndpoint.expectedMessageCount(10);
        mockEndpoint.assertIsSatisfied();
        LogManager.getLogger().info(fromOrder.toString());
        Assert.assertEquals(fromOrder.size(), 10);
        Assert.assertEquals(fromOrder.get(7), "u0");
        Assert.assertEquals(fromOrder.get(8), "u0");
        Assert.assertEquals(fromOrder.get(9), "u0");
    }
}