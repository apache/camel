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
package org.apache.camel.processor.throttle.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledOnOs(OS.WINDOWS)
public class ThrottlerMethodCallTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThrottlerMethodCallTest.class);
    private static final int INTERVAL = 100;
    protected int messageCount = 10;
    private MockEndpoint resultEndpoint;
    private ExecutorService executor;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", this);
        return jndi;
    }

    public long getConcurrentMessages() {
        return 3;
    }

    @BeforeEach
    public void prepareTest() {
        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        executor = Executors.newFixedThreadPool(messageCount);
    }

    @AfterEach
    public void cleanupTest() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            LOG.warn("The tasks did not finish within the expected time");
            executor.shutdownNow();
        }
    }

    @Test
    public void testConfigurationWithMethodCallExpression() {
        for (int i = 0; i < messageCount; i++) {
            executor.execute(() -> template.sendBody("direct:expressionMethod", "<message>payload</message>"));
        }

        // let's wait for the exchanges to arrive
        try {
            resultEndpoint.assertIsSatisfied();
        } catch (InterruptedException e) {
            Assertions.fail(e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:expressionMethod").throttle(method("myBean", "getConcurrentMessages")).concurrentRequestsMode()
                        .delay(INTERVAL)
                        .to("log:result", "mock:result");
            }
        };
    }
}
