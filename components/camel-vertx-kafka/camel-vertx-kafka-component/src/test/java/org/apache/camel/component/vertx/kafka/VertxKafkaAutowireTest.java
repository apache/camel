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
package org.apache.camel.component.vertx.kafka;

import io.vertx.core.Vertx;
import org.apache.camel.BindToRegistry;
import org.apache.camel.component.vertx.kafka.offset.DefaultVertxKafkaManualCommitFactory;
import org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommitFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertSame;

public class VertxKafkaAutowireTest extends CamelTestSupport {

    @BindToRegistry
    Vertx vertx = Vertx.vertx();

    @BindToRegistry
    VertxKafkaClientFactory clientFactory = new TestVertxKafkaClientFactory();

    @BindToRegistry
    VertxKafkaManualCommitFactory commitFactory = new TestVertxKafkaManualCommitFactory();

    @Override
    public void afterAll(ExtensionContext context) {
        super.afterAll(context);
        vertx.close();
    }

    @Test
    public void testVertxKafkaComponentAutowiring() {
        VertxKafkaComponent component = context.getComponent("vertx-kafka", VertxKafkaComponent.class);
        assertSame(vertx, component.getVertx());
        assertSame(clientFactory, component.getVertxKafkaClientFactory());
        assertSame(commitFactory, component.getKafkaManualCommitFactory());
    }

    static final class TestVertxKafkaClientFactory extends DefaultVertxKafkaClientFactory {

    }

    static final class TestVertxKafkaManualCommitFactory extends DefaultVertxKafkaManualCommitFactory {

    }
}
