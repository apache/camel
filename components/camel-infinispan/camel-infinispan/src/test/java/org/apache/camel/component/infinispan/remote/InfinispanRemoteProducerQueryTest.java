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
package org.apache.camel.component.infinispan.remote;

import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies how the QUERY operation resolves its query builder. Builds the endpoint directly so that nothing tries to
 * reach an Infinispan server.
 */
class InfinispanRemoteProducerQueryTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private InfinispanRemoteProducer producer(InfinispanRemoteConfiguration configuration) throws Exception {
        context = new DefaultCamelContext();

        InfinispanRemoteComponent component = new InfinispanRemoteComponent(context);
        InfinispanRemoteEndpoint endpoint
                = new InfinispanRemoteEndpoint("infinispan:misc", "misc", component, configuration);

        Producer producer = endpoint.createProducer();
        return (InfinispanRemoteProducer) producer;
    }

    private Message message() {
        return new DefaultExchange(context).getMessage();
    }

    @Test
    void aQueryWithoutABuilderTouchesNoCache() throws Exception {
        InfinispanRemoteProducer producer = producer(new InfinispanRemoteConfiguration());

        Message message = message();
        message.setBody("unchanged");

        // the message is still passed through (CAMEL-9624 behaviour), but without reaching for a cache first:
        // the manager here was never started, so any remote call would fail
        assertThatCode(() -> producer.onQuery(message)).doesNotThrowAnyException();
        assertThat(message.getBody()).isEqualTo("unchanged");
    }

    @Test
    void theBuilderComesFromTheConfiguration() {
        context = new DefaultCamelContext();

        InfinispanQueryBuilder configured = cache -> null;
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setQueryBuilder(configured);

        assertThat(InfinispanRemoteUtil.resolveQueryBuilder(configuration, message())).isSameAs(configured);
    }

    @Test
    void theHeaderWinsOverTheConfiguration() {
        context = new DefaultCamelContext();

        InfinispanQueryBuilder configured = cache -> null;
        InfinispanQueryBuilder fromHeader = cache -> null;
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setQueryBuilder(configured);

        Message message = message();
        message.setHeader(InfinispanConstants.QUERY_BUILDER, fromHeader);

        assertThat(InfinispanRemoteUtil.resolveQueryBuilder(configuration, message)).isSameAs(fromHeader);
    }

    @Test
    void noBuilderAnywhereResolvesToNull() {
        context = new DefaultCamelContext();

        assertThat(InfinispanRemoteUtil.resolveQueryBuilder(new InfinispanRemoteConfiguration(), message())).isNull();
    }
}
