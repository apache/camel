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

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.support.SoroushBotTestSupport;
import org.apache.logging.log4j.LogManager;
import org.junit.Assert;
import org.junit.Test;

public class ConsumerNativeConcurrentTest extends SoroushBotTestSupport {
    ConcurrentHashMap<String, Thread> userToThread;
    AtomicInteger badThread = new AtomicInteger(0);

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        userToThread = new ConcurrentHashMap<>();
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("soroush://" + SoroushAction.getMessage + "/10?concurrentConsumers=3&maxConnectionRetry=0").process(exchange -> {
                    String from = exchange.getIn().getBody(SoroushMessage.class).getFrom();
                    Thread currentThread = Thread.currentThread();
                    Thread previousThread = userToThread.putIfAbsent(from, currentThread);
                    if (previousThread != null) {
                        if (previousThread != currentThread) {
                            badThread.addAndGet(1);
                        }
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
        LogManager.getLogger().info(userToThread.size());
        LogManager.getLogger().info(userToThread.values());
        Assert.assertEquals("previous and current thread must be equal", badThread.get(), 0);
        Assert.assertTrue("there must be more than 1 thread in $userToThread unless this test is not useful", new HashSet(userToThread.values()).size() > 1);
    }
}
