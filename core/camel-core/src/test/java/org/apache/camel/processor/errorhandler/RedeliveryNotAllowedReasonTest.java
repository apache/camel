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
package org.apache.camel.processor.errorhandler;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.engine.DefaultShutdownStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25365: the RejectedExecutionException of an exchange that cannot continue says why, instead of
 * {@code RejectedExecutionException - null}.
 */
public class RedeliveryNotAllowedReasonTest extends ContextTestSupport {

    private final ForcedShutdownStrategy shutdownStrategy = new ForcedShutdownStrategy();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.setShutdownStrategy(shutdownStrategy);
        return answer;
    }

    @Test
    public void aStoppedOrReloadedRoute() {
        assertEquals("The exchange cannot continue: its route is being stopped or reloaded",
                errorHandler().notAllowedReason());
    }

    @Test
    public void aForcedShutdown() {
        shutdownStrategy.forced = true;
        assertEquals("The exchange cannot continue: its route was forced to shut down, as the graceful shutdown timed out"
                     + " while it was in flight (the CamelContext is being stopped)",
                errorHandler().notAllowedReason());
    }

    private DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(context, exchange -> {
        }, null, null, new RedeliveryPolicy(), null, null, null, null);
    }

    private static final class ForcedShutdownStrategy extends DefaultShutdownStrategy {

        private boolean forced;

        @Override
        public boolean isForceShutdown() {
            return forced;
        }
    }
}
