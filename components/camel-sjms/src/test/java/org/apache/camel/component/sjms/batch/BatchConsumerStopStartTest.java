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
package org.apache.camel.component.sjms.batch;

import java.time.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSize;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessages;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerStopStartTest extends JmsTestSupport {

    private static final String WORKER_THREAD_MARKER = "SjmsBatchConsumer";

    private static String ackOptions(String mode) {
        return switch (mode) {
            case "AUTO" -> "acknowledgementMode=AUTO_ACKNOWLEDGE";
            case "CLIENT" -> "acknowledgementMode=CLIENT_ACKNOWLEDGE";
            case "TX" -> "transacted=true";
            default -> throw new IllegalArgumentException(mode);
        };
    }

    private void addRoute(String routeId, String queue, String options, String mockUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:" + queue + "?batching=true&" + options)
                        .routeId(routeId)
                        .to(mockUri);
            }
        });
    }

    // ---------------------------------------------------------------- drain on stop

    @ParameterizedTest
    @ValueSource(strings = { "AUTO", "CLIENT", "TX" })
    public void testPartialBatchDrainedOnRouteStop(String mode) throws Exception {
        String id = "drain-" + mode;
        String queue = "batch.stop.drain." + mode;
        // batchSize and batchInterval are far out of reach, so only the stop can trigger dispatch
        addRoute(id, queue, "batchSize=100&batchInterval=60000&" + ackOptions(mode), "mock:" + id);

        MockEndpoint mock = getMockEndpoint("mock:" + id);
        mock.expectedMessageCount(1);

        sendMessages(template, "sjms:queue:" + queue, 3);

        // Messages must have reached the worker's buffer, yet nothing may be dispatched yet.
        // The worker pulls messages as soon as they arrive, so 1.5s comfortably exceeds that.
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(5))
                .until(() -> mock.getReceivedCounter() == 0);

        context.getRouteController().stopRoute(id);

        mock.assertIsSatisfied();
        assertBatchSize(mock.getExchanges().get(0), 3);
    }

    @ParameterizedTest
    @ValueSource(strings = { "AUTO", "CLIENT", "TX" })
    public void testDrainedMessagesAreNotRedeliveredAfterRestart(String mode) throws Exception {
        String id = "redeliver-" + mode;
        String queue = "batch.stop.redeliver." + mode;
        addRoute(id, queue, "batchSize=100&batchInterval=60000&" + ackOptions(mode), "mock:" + id);

        MockEndpoint mock = getMockEndpoint("mock:" + id);
        mock.expectedMessageCount(1);

        sendMessages(template, "sjms:queue:" + queue, 3);
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(5))
                .until(() -> mock.getReceivedCounter() == 0);

        context.getRouteController().stopRoute(id);
        mock.assertIsSatisfied();

        // the drained batch must have been acknowledged/committed: a restart must see an empty queue
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1500);

        context.getRouteController().startRoute(id);

        mock.assertIsSatisfied();
        // stop again so the drain-on-stop path of an empty buffer is also exercised
        context.getRouteController().stopRoute(id);
        assertEquals(0, mock.getReceivedCounter());
    }

    @Test
    public void testNothingDispatchedOnStopWhenBufferIsEmpty() throws Exception {
        String id = "drain-empty";
        String queue = "batch.stop.drain.empty";
        addRoute(id, queue, "batchSize=5&batchInterval=60000&transacted=true", "mock:" + id);

        MockEndpoint mock = getMockEndpoint("mock:" + id);
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);

        // let the worker spin for a while, then stop with nothing buffered
        await().pollDelay(Duration.ofMillis(1200)).until(() -> true);
        context.getRouteController().stopRoute(id);

        mock.assertIsSatisfied();
    }

    // ---------------------------------------------------------------- route stop / start

    @Test
    public void testMessagesSentWhileStoppedAreDeliveredAfterStart() throws Exception {
        String id = "stop-start";
        String queue = "batch.stop.start";
        addRoute(id, queue, "batchSize=5&batchInterval=60000&transacted=true", "mock:" + id);

        MockEndpoint mock = getMockEndpoint("mock:" + id);
        mock.expectedMessageCount(1);
        sendMessages(template, "sjms:queue:" + queue, 5);
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute(id);

        // sent while no consumer exists, so they wait on the broker
        sendMessages(template, "sjms:queue:" + queue, 5);

        mock.reset();
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute(id);
        mock.assertIsSatisfied();

        assertBatchSizesInOrder(mock, 5);
    }

    @Test
    public void testRepeatedStopStartCycles() throws Exception {
        String id = "stop-start-cycles";
        String queue = "batch.stop.start.cycles";
        addRoute(id, queue, "batchSize=5&batchInterval=60000&transacted=true&concurrentConsumers=1", "mock:" + id);

        MockEndpoint mock = getMockEndpoint("mock:" + id);

        for (int cycle = 0; cycle < 3; cycle++) {
            mock.reset();
            mock.expectedMessageCount(1);

            sendMessages(template, "sjms:queue:" + queue, 5);
            mock.assertIsSatisfied();
            assertBatchSizesInOrder(mock, 5);

            context.getRouteController().stopRoute(id);

            // all worker threads of the stopped container must be gone before the next start
            await().atMost(Duration.ofSeconds(10)).until(() -> countWorkerThreads(queue) == 0);

            context.getRouteController().startRoute(id);
        }
    }

    @Test
    public void testRestartedRouteRunsConfiguredNumberOfWorkers() throws Exception {
        String id = "stop-start-workers";
        String queue = "batch.stop.start.workers";
        int consumers = 3;
        addRoute(id, queue, "batchSize=5&batchInterval=60000&transacted=true&concurrentConsumers=" + consumers,
                "mock:" + id);

        await().atMost(Duration.ofSeconds(10)).until(() -> countWorkerThreads(queue) == consumers);

        context.getRouteController().stopRoute(id);
        await().atMost(Duration.ofSeconds(10)).until(() -> countWorkerThreads(queue) == 0);

        context.getRouteController().startRoute(id);
        await().atMost(Duration.ofSeconds(10)).until(() -> countWorkerThreads(queue) == consumers);
    }

    private static long countWorkerThreads(String queue) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(n -> n.contains(WORKER_THREAD_MARKER) && n.contains(queue))
                .count();
    }
}
