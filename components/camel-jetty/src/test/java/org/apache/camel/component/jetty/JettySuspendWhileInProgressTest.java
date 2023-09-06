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
package org.apache.camel.component.jetty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JettySuspendWhileInProgressTest extends BaseJettyTest {
    private static final Logger LOG = LoggerFactory.getLogger(JettySuspendWhileInProgressTest.class);

    private String serverUri = "http://localhost:" + getPort() + "/cool";
    private CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testJettySuspendWhileInProgress() throws Exception {
        context.getShutdownStrategy().setTimeout(50);

        // send a request/reply and have a future handle, so we can shut down while
        // in progress
        Future<String> reply = template.asyncRequestBodyAndHeader(serverUri, null, "name", "Camel", String.class);

        // shutdown camel while in progress
        Executors.newSingleThreadExecutor().execute(this::triggerShutdown);

        // wait a bit more before sending next
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(context::isStopping);

        // now send a new req/reply
        Future<String> reply2 = template.asyncRequestBodyAndHeader(serverUri, null, "name", "Tiger", String.class);

        // the first should wait to have its reply returned
        assertEquals("Bye Camel", reply.get(20, TimeUnit.SECONDS));

        // the 2nd should have a 503 returned as we are shutting down
        Exception ex = assertThrows(ExecutionException.class, () -> reply2.get(20, TimeUnit.SECONDS),
                "Should have thrown an exception");
        RuntimeCamelException rce = assertIsInstanceOf(RuntimeCamelException.class, ex.getCause());
        HttpOperationFailedException hofe = assertIsInstanceOf(HttpOperationFailedException.class, rce.getCause());
        assertEquals(503, hofe.getStatusCode(), "The 2nd status code should have been a 503 as we are shutting down");
    }

    private void triggerShutdown() {
        try {
            // wait for 2 seconds until the first req has been received in Camel route, and then triggers the shutdown
            if (!latch.await(2, TimeUnit.SECONDS)) {
                LOG.warn("Timed out waiting for the latch. Shutting down anyway ...");
            }
            context.stop();
        } catch (Exception e) {
            LOG.warn("Error reported while shutting down: {}", e.getMessage(), e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty://" + serverUri).log("Got data will wait 5 sec with reply")
                        .process(e -> latch.countDown())
                        .delay(5000)
                        .transform(simple("Bye ${header.name}"));
            }
        };
    }
}
