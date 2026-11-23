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
package org.apache.camel.processor.throttle;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When suspending or resuming the consumer fails in the half open check of {@link ThrottlingExceptionRoutePolicy}, the
 * circuit stays open, and the policy checks it again after halfOpenAfter: the consumer is suspended, so no exchange
 * would check it.
 * <p>
 * The first suspend or resume of each check path fails, the later ones work.
 */
class ThrottlingExceptionRoutePolicyHalfOpenCheckFailureTest extends ContextTestSupport {

    private final FailOncePolicy halfOpenPolicy = new FailOncePolicy();
    private final FailOncePolicy closePolicy = new FailOncePolicy();
    private final FailOncePolicy reopenPolicy = new FailOncePolicy();
    private final AtomicInteger closeChecks = new AtomicInteger();
    private final AtomicInteger reopenChecks = new AtomicInteger();

    @Test
    void halfOpenCheckIsRepeatedWhenResumingTheConsumerFails() {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("halfOpen").getConsumer();

        // the failure opens the circuit and suspends the consumer (the suspend is counted; the state is not asserted
        // here, as the first check may already have run 100 millis later on a slow machine)
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:halfOpen", "fail"));
        assertEquals(1, halfOpenPolicy.suspends.get());

        // the first check fails to resume the consumer, the next one resumes it
        await().atMost(10, TimeUnit.SECONDS).until(() -> "half opened".equals(halfOpenPolicy.getStateAsString()));
        assertEquals(2, halfOpenPolicy.resumes.get());
        assertTrue(consumer.isStarted());
    }

    @Test
    void halfOpenCheckIsRepeatedWhenResumingTheConsumerFailsWhileClosing() {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("close").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:close", "fail"));
        assertEquals(1, closePolicy.suspends.get());

        // the half open handler is ready: the first check fails to resume the consumer, the next one closes the circuit
        await().atMost(10, TimeUnit.SECONDS).until(() -> "closed".equals(closePolicy.getStateAsString()));
        assertEquals(2, closeChecks.get());
        assertEquals(2, closePolicy.resumes.get());
        assertTrue(consumer.isStarted());
    }

    @Test
    void halfOpenCheckIsRepeatedWhenSuspendingTheConsumerFailsWhileKeepingItOpen() {
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:reopen", "fail"));
        assertEquals("opened", reopenPolicy.getStateAsString());

        // the half open handler is not ready: the first check fails to suspend the consumer again, and the circuit is
        // still checked afterwards
        await().atMost(10, TimeUnit.SECONDS).until(() -> reopenChecks.get() >= 3);
        assertEquals("opened", reopenPolicy.getStateAsString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        closePolicy.setHalfOpenHandler(() -> {
            closeChecks.incrementAndGet();
            return true;
        });
        reopenPolicy.setHalfOpenHandler(() -> {
            reopenChecks.incrementAndGet();
            return false;
        });
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:halfOpen").routeId("halfOpen").routePolicy(halfOpenPolicy)
                        .throwException(new IllegalStateException("Downstream failure"));
                from("direct:close").routeId("close").routePolicy(closePolicy)
                        .throwException(new IllegalStateException("Downstream failure"));
                from("direct:reopen").routeId("reopen").routePolicy(reopenPolicy)
                        .throwException(new IllegalStateException("Downstream failure"));
            }
        };
    }

    // opens the circuit on the first failure; the first resume fails, and so does the second suspend (the first one
    // opens the circuit)
    private static class FailOncePolicy extends ThrottlingExceptionRoutePolicy {
        private final AtomicInteger resumes = new AtomicInteger();
        private final AtomicInteger suspends = new AtomicInteger();

        FailOncePolicy() {
            super(1, 60000, 100, null);
        }

        @Override
        public boolean resumeOrStartConsumer(Consumer consumer) throws Exception {
            if (resumes.incrementAndGet() == 1) {
                throw new IllegalStateException("Cannot resume the consumer");
            }
            return super.resumeOrStartConsumer(consumer);
        }

        @Override
        public boolean suspendOrStopConsumer(Consumer consumer) throws Exception {
            if (suspends.incrementAndGet() == 2) {
                throw new IllegalStateException("Cannot suspend the consumer");
            }
            return super.suspendOrStopConsumer(consumer);
        }
    }
}
