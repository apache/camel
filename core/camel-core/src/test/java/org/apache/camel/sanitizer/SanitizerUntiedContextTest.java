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
package org.apache.camel.sanitizer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The violations of a context that is not tied to an open session (built on a thread no test marked) are charged to
 * every session open when they happen, and not to a closed one. Isolated: the test classes running at the same time
 * would be charged too.
 */
@Isolated
class SanitizerUntiedContextTest {

    @Test
    // charged to this class too when the CI switch runs the extension for it
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void aContextThatIsNotTiedChargesTheOpenSessions() throws Exception {
        SanitizerTestSession open = SanitizerTestSession.open("open");
        SanitizerTestSession closed = SanitizerTestSession.open("closed");
        closed.close();
        ExecutorService builder = Executors.newSingleThreadExecutor(r -> new Thread(r, "untied-builder"));
        CamelContext context = builder.submit(() -> {
            CamelContext c = new DefaultCamelContext();
            c.getPropertiesComponent().addOverrideProperty("camel.sanitizer.enabled", "true");
            c.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:broken").process(new BrokenUserCode.ThrowsInProcess()).id("broken");
                }
            });
            c.start();
            return c;
        }).get(30, TimeUnit.SECONDS);
        try {
            Exchange out = context.createProducerTemplate().send("direct:broken", e -> e.getMessage().setBody("x"));
            assertNotNull(out.getException());
            List<SanitizerViolation> charged = open.drain();
            assertEquals(1, charged.size());
            assertEquals("broken", charged.get(0).getStepId());
            assertTrue(closed.drain().isEmpty());
        } finally {
            open.close();
            context.stop();
            builder.shutdownNow();
        }
    }
}
