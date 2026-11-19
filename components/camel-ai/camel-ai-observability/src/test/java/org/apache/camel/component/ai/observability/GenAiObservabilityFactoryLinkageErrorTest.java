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
package org.apache.camel.component.ai.observability;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Injector;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * An implementation that is found but cannot be linked (such as camel-telemetry excluded from the classpath) must not
 * fail the LLM call, and is only tried once.
 */
class GenAiObservabilityFactoryLinkageErrorTest extends ExchangeTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setInjector(new FailingImplInjector(context.getInjector()));
        return context;
    }

    @Test
    void shouldReturnNoopWhenTheImplementationCannotBeLoaded() {
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .requestModel("test-model")
                .build();

        assertThatCode(() -> {
            GenAiObservability.start(new DefaultExchange(context), observationContext).close();
            GenAiObservability.start(new DefaultExchange(context), observationContext).close();
        }).doesNotThrowAnyException();
        assertThat(attempts).hasValue(1);
    }

    private final class FailingImplInjector implements Injector {

        private final Injector delegate;

        private FailingImplInjector(Injector delegate) {
            this.delegate = delegate;
        }

        private void failForImpl(Class<?> type) {
            if (type == GenAiObservabilityImpl.class) {
                attempts.incrementAndGet();
                throw new NoClassDefFoundError("org/apache/camel/telemetry/Tracer");
            }
        }

        @Override
        public <T> T newInstance(Class<T> type) {
            failForImpl(type);
            return delegate.newInstance(type);
        }

        @Override
        public <T> T newInstance(Class<T> type, String factoryMethod) {
            failForImpl(type);
            return delegate.newInstance(type, factoryMethod);
        }

        @Override
        public <T> T newInstance(Class<T> type, Class<?> factoryClass, String factoryMethod) {
            failForImpl(type);
            return delegate.newInstance(type, factoryClass, factoryMethod);
        }

        @Override
        public <T> T newInstance(Class<T> type, boolean postProcessBean) {
            failForImpl(type);
            return delegate.newInstance(type, postProcessBean);
        }

        @Override
        public boolean supportsAutoWiring() {
            return delegate.supportsAutoWiring();
        }
    }
}
