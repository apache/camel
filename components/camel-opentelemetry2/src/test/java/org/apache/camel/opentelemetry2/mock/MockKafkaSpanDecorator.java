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
package org.apache.camel.opentelemetry2.mock;

/**
 * Span decorator for mock Kafka component used in tests. Extends the real KafkaSpanDecorator to inherit all
 * Kafka-specific behavior (partition, offset, key tags) and adds SpanKind.
 */
public class MockKafkaSpanDecorator extends org.apache.camel.telemetry.decorators.KafkaSpanDecorator {

    @Override
    public String getComponent() {
        return "mock-kafka";
    }

    @Override
    public String getComponentClassName() {
        return MockKafkaComponent.class.getName();
    }
}
