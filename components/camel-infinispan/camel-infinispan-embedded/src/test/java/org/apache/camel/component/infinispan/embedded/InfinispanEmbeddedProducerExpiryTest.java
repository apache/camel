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
package org.apache.camel.component.infinispan.embedded;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies when the producer considers an expiry to be configured on a message. An expiry needs both its amount and its
 * time unit, and half of a pair used to be dropped without a word.
 */
class InfinispanEmbeddedProducerExpiryTest {

    private DefaultCamelContext context;
    private ExpiryProbe producer;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();

        InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();
        InfinispanEmbeddedComponent component = new InfinispanEmbeddedComponent(context);
        InfinispanEmbeddedEndpoint endpoint
                = new InfinispanEmbeddedEndpoint("infinispan-embedded:misc", "misc", component, configuration);

        // the manager is only needed to reach a cache, which these assertions never do
        producer = new ExpiryProbe(endpoint, configuration);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private Message message() {
        return new DefaultExchange(context).getMessage();
    }

    @Test
    void noExpiryHeadersMeansNoExpiry() {
        Message message = message();

        assertThat(producer.lifespan(message)).isFalse();
        assertThat(producer.maxIdleTime(message)).isFalse();
    }

    @Test
    void anAmountWithItsTimeUnitIsAnExpiry() {
        Message lifespan = message();
        lifespan.setHeader(InfinispanConstants.LIFESPAN_TIME, 100L);
        lifespan.setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS);
        assertThat(producer.lifespan(lifespan)).isTrue();

        Message maxIdle = message();
        maxIdle.setHeader(InfinispanConstants.MAX_IDLE_TIME, 100L);
        maxIdle.setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS);
        assertThat(producer.maxIdleTime(maxIdle)).isTrue();
    }

    @Test
    void anAmountWithoutItsTimeUnitIsNotAnExpiry() {
        // the entry is still stored, without an expiry - but the producer now reports it at WARN
        Message lifespan = message();
        lifespan.setHeader(InfinispanConstants.LIFESPAN_TIME, 100L);
        assertThat(producer.lifespan(lifespan)).isFalse();

        Message maxIdle = message();
        maxIdle.setHeader(InfinispanConstants.MAX_IDLE_TIME, 100L);
        assertThat(producer.maxIdleTime(maxIdle)).isFalse();

        // a route that gets this wrong gets it wrong for every message, so the verdict must not change
        // when the pair is reported only the first time
        assertThat(producer.lifespan(lifespan)).isFalse();
        assertThat(producer.maxIdleTime(maxIdle)).isFalse();
    }

    @Test
    void aTimeUnitWithoutItsAmountIsNotAnExpiry() {
        Message lifespan = message();
        lifespan.setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS);
        assertThat(producer.lifespan(lifespan)).isFalse();

        Message maxIdle = message();
        maxIdle.setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS);
        assertThat(producer.maxIdleTime(maxIdle)).isFalse();
    }

    /**
     * Opens up the two protected decisions of the producer, which are otherwise only reachable from a subclass.
     */
    private static final class ExpiryProbe extends InfinispanEmbeddedProducer {

        private ExpiryProbe(InfinispanEndpoint endpoint, InfinispanEmbeddedConfiguration configuration) {
            super((InfinispanEmbeddedEndpoint) endpoint, "misc", null, configuration);
        }

        private boolean lifespan(Message message) {
            return hasLifespan(message);
        }

        private boolean maxIdleTime(Message message) {
            return hasMaxIdleTime(message);
        }
    }
}
