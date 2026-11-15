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
package org.apache.camel.component.file;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.impl.health.ConsumerHealthCheck;
import org.apache.camel.support.ScheduledPollConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The healthCheckMaxPollDuration endpoint option of a polling consumer (CAMEL-26998): the file consumer routes the
 * files it polled within the poll, so a route that blocks is a poll that hangs.
 */
public class FileConsumerHangHealthCheckTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void release() {
        // also when the test failed, so the route does not wait at shutdown
        release.countDown();
    }

    @Test
    public void testConsumerHealthCheckIsDownWhileThePollHangs() throws Exception {
        ScheduledPollConsumer consumer = (ScheduledPollConsumer) context.getRoute("hang").getConsumer();
        assertEquals(200, consumer.getHealthCheckMaxPollDuration());

        ConsumerHealthCheck check = new ConsumerHealthCheck(context.getRoute("hang"), "consumer:hang");
        CamelContextAware.trySetCamelContext(check, context);

        // an empty directory: the polls are done, UP
        await().atMost(10, TimeUnit.SECONDS).until(() -> check.callReadiness().getState() == State.UP);

        // a file whose routing blocks: DOWN once the poll has been running longer than 200 millis
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        await().atMost(10, TimeUnit.SECONDS).until(() -> check.callReadiness().getState() == State.DOWN);
        HealthCheck.Result result = check.callReadiness();
        String message = result.getMessage().orElse("");
        assertTrue(message.startsWith("Consumer has been polling for "), message);
        assertTrue(message.contains(", longer than healthCheckMaxPollDuration (200ms) route: hang (file:"), message);
        assertTrue(consumer.isPolling());

        // the routing goes on and the poll is done: UP again
        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> check.callReadiness().getState() == State.UP);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&healthCheckMaxPollDuration=200")).routeId("hang")
                        .process(exchange -> release.await(20, TimeUnit.SECONDS));
            }
        };
    }
}
