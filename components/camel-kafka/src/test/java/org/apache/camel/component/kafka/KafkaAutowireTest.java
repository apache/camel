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
package org.apache.camel.component.kafka;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertSame;

public class KafkaAutowireTest {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private final CamelContext context = contextExtension.getContext();

    @BindToRegistry
    private final KafkaClientFactory clientFactory = new TestKafkaClientFactory();

    @Test
    public void testKafkaComponentAutowiring() {
        KafkaComponent component = context.getComponent("kafka", KafkaComponent.class);
        assertSame(clientFactory, component.getKafkaClientFactory());

        KafkaEndpoint endpoint = context.getEndpoint("kafka:foo", KafkaEndpoint.class);
        assertSame(clientFactory, endpoint.getKafkaClientFactory());
    }

    static final class TestKafkaClientFactory extends DefaultKafkaClientFactory {

    }
}
