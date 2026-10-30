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
package org.apache.camel.component.chaos;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether the consumer of an endpoint keeps a failed message, from the options of the endpoint as its configurer
 * returns them.
 */
class ChaosConsumersTest {

    @TempDir
    Path dir;

    private CamelContext context;
    private int endpoints;

    @BeforeEach
    void start() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stop() {
        context.stop();
    }

    private Endpoint endpoint(String scheme, Map<String, Object> options) {
        context.removeComponent(scheme);
        context.addComponent(scheme, new OptionsComponent(options));
        // a new endpoint each time, as the context keeps the endpoints it created
        return context.getEndpoint(scheme + ":x" + endpoints++);
    }

    @Test
    void fileWithoutMoveFailedKeepsTheFile() {
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint("file:" + dir))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint("file:" + dir + "?noop=true"))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint("file:" + dir + "?moveFailed=.error"))).isFalse();
    }

    @Test
    void ftpFamilyLikeFile() {
        assertThat(ChaosConsumers.keepsFailed(endpoint("sftp", Map.of()))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(endpoint("ftps", Map.of("moveFailed", "failed")))).isFalse();
    }

    @Test
    void jmsTransactedOrClientAcknowledgeSynchronous() {
        assertThat(ChaosConsumers.keepsFailed(endpoint("jms", Map.of("transacted", true)))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(endpoint("activemq",
                Map.of("acknowledgementModeName", "CLIENT_ACKNOWLEDGE", "asyncConsumer", false)))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(endpoint("amqp",
                Map.of("acknowledgementModeName", "CLIENT_ACKNOWLEDGE", "asyncConsumer", true)))).isFalse();
        assertThat(ChaosConsumers.keepsFailed(endpoint("activemq6",
                Map.of("acknowledgementModeName", "AUTO_ACKNOWLEDGE", "transacted", false)))).isFalse();
    }

    @Test
    void kafkaWithBreakOnFirstError() {
        assertThat(ChaosConsumers.keepsFailed(endpoint("kafka", Map.of("breakOnFirstError", "true")))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(endpoint("kafka", Map.of("breakOnFirstError", false)))).isFalse();
    }

    @Test
    void onlyJmsKeepsAnExchangeMarkedRollbackOnly() {
        assertThat(ChaosConsumers.keepsRollbackOnly(endpoint("jms", Map.of("transacted", true)))).isTrue();
        assertThat(ChaosConsumers.keepsRollbackOnly(endpoint("activemq",
                Map.of("acknowledgementModeName", "CLIENT_ACKNOWLEDGE", "asyncConsumer", false)))).isTrue();
        assertThat(ChaosConsumers.keepsRollbackOnly(endpoint("amqp", Map.of("transacted", false)))).isFalse();
        // the file consumer and kafka look at the exception only, and commit
        assertThat(ChaosConsumers.keepsRollbackOnly(context.getEndpoint("file:" + dir))).isFalse();
        assertThat(ChaosConsumers.keepsRollbackOnly(endpoint("kafka", Map.of("breakOnFirstError", true)))).isFalse();
        assertThat(ChaosConsumers.keepsRollbackOnly(null)).isFalse();
    }

    @Test
    void otherConsumersAndUnknownOptionsDoNotKeep() {
        assertThat(ChaosConsumers.keepsFailed(endpoint("seda2", Map.of("transacted", true)))).isFalse();
        assertThat(ChaosConsumers.keepsFailed(null)).isFalse();
        // a component without a configurer: unknown, so not kept
        context.removeComponent("jms");
        context.addComponent("jms", new OptionsComponent(null));
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint("jms:y"))).isFalse();
    }

    /** A component whose endpoint configurer returns the given option values, as the generated ones do. */
    static final class OptionsComponent extends DefaultComponent {

        private final Map<String, Object> options;

        OptionsComponent(Map<String, Object> options) {
            this.options = options != null ? new HashMap<>(options) : null;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public PropertyConfigurer getEndpointPropertyConfigurer() {
            if (options == null) {
                return null;
            }
            return new Getter(options);
        }
    }

    private record Getter(Map<String, Object> options) implements PropertyConfigurer, PropertyConfigurerGetter {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return Object.class;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return options.get(name);
        }
    }
}
