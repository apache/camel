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
package org.apache.camel.jta;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that JTA transacted routes commit correctly after context.suspend() + context.resume().
 *
 * During suspend, DefaultShutdownStrategy sets preparingShutdown = true on TransactionErrorHandler via
 * getChildServices(service, includeErrorHandler=true). During resume, the error handler is excluded from the service
 * list (includeErrorHandler=false) and its status stays STARTED, so neither doStart() nor doResume() is called to reset
 * the flag. Subsequent transacted exchanges are silently rolled back.
 */
public class TransactionErrorHandlerSuspendResumeTest {

    private CamelContext camelContext;
    private final AtomicInteger commitCount = new AtomicInteger();
    private final AtomicBoolean lastExchangeRolledBack = new AtomicBoolean(false);

    @BeforeEach
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();

        JtaTransactionPolicy testPolicy = new JtaTransactionPolicy() {
            @Override
            public void run(Runnable runnable) throws Throwable {
                try {
                    runnable.run();
                    commitCount.incrementAndGet();
                    lastExchangeRolledBack.set(false);
                } catch (Throwable t) {
                    lastExchangeRolledBack.set(true);
                    throw t;
                }
            }
        };

        camelContext.getRegistry().bind("PROPAGATION_REQUIRED", testPolicy);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transacted").routeId("transactedRoute")
                        .transacted()
                        .setBody(constant("done"));
            }
        });

        camelContext.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void testTransactionCommitsAfterContextSuspendResume() throws Exception {
        // first send — must commit
        camelContext.createProducerTemplate().sendBody("direct:transacted", "first");
        assertEquals(1, commitCount.get(), "First transaction must commit");
        assertFalse(lastExchangeRolledBack.get(), "First exchange must not be rolled back");

        // suspend and resume the entire context
        camelContext.suspend();
        camelContext.resume();

        // second send — must also commit if preparingShutdown was properly reset
        camelContext.createProducerTemplate().sendBody("direct:transacted", "second");
        assertEquals(2, commitCount.get(),
                "After context suspend/resume: second transaction must commit, "
                                           + "but preparingShutdown is still true causing silent rollback");
        assertFalse(lastExchangeRolledBack.get(),
                "After context suspend/resume: exchange must not be rolled back");
    }

    @Test
    public void testTransactionCommitsAfterRouteSuspendResume() throws Exception {
        // first send — must commit
        camelContext.createProducerTemplate().sendBody("direct:transacted", "first");
        assertEquals(1, commitCount.get(), "First transaction must commit");

        // suspend and resume a single route
        camelContext.getRouteController().suspendRoute("transactedRoute");
        camelContext.getRouteController().resumeRoute("transactedRoute");

        // second send — must also commit
        camelContext.createProducerTemplate().sendBody("direct:transacted", "second");
        assertEquals(2, commitCount.get(),
                "After route suspend/resume: second transaction must commit");
        assertFalse(lastExchangeRolledBack.get(),
                "After route suspend/resume: exchange must not be rolled back");
    }
}
