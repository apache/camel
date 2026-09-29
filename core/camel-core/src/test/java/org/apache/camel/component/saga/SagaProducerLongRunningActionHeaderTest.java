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
package org.apache.camel.component.saga;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exchange that is not bound to a saga must not pick a saga to complete or compensate via the Long-Running-Action
 * header, unless the saga service takes part in a protocol that carries the id that way.
 */
public class SagaProducerLongRunningActionHeaderTest extends ContextTestSupport {

    private final LongRunningActionHeaderSagaService sagaService = new LongRunningActionHeaderSagaService();

    @Test
    public void testHeaderIgnoredWhenNotSupported() throws Exception {
        String sagaId = startManualSaga();

        MockEndpoint compensated = getMockEndpoint("mock:compensated");
        compensated.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:compensate", "cancel", Exchange.SAGA_LONG_RUNNING_ACTION,
                        sagaId));
        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(cause.getMessage().contains("not bound to a saga context"));

        compensated.assertIsSatisfied(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testHeaderUsedWhenSupported() throws Exception {
        sagaService.setHeaderSupported(true);
        String sagaId = startManualSaga();

        MockEndpoint compensated = getMockEndpoint("mock:compensated");
        compensated.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:compensate", "cancel", Exchange.SAGA_LONG_RUNNING_ACTION, sagaId);

        compensated.assertIsSatisfied();
    }

    private String startManualSaga() {
        Exchange out = template.request("direct:start", e -> e.getIn().setBody("order"));
        String sagaId = out.getMessage().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION, String.class);
        assertNotNull(sagaId);
        return sagaId;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addService(sagaService);

                from("direct:start")
                        .saga().compensation("mock:compensated").completion("mock:completed")
                        .completionMode(SagaCompletionMode.MANUAL)
                        .to("mock:start");

                from("direct:compensate")
                        .to("saga:compensate");
            }
        };
    }

    private static final class LongRunningActionHeaderSagaService extends InMemorySagaService {

        private boolean headerSupported;

        void setHeaderSupported(boolean headerSupported) {
            this.headerSupported = headerSupported;
        }

        @Override
        public boolean isLongRunningActionHeaderSupported() {
            return headerSupported;
        }
    }
}
