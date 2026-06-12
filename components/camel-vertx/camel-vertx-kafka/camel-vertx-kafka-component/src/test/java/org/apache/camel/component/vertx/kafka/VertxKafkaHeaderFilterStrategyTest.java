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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxKafkaHeaderFilterStrategyTest {
    private final VertxKafkaHeaderFilterStrategy strategy = new VertxKafkaHeaderFilterStrategy();

    @Test
    void filtersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToCamelHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToCamelHeaders("CAMELHttpMethod", "GET", null));
        assertTrue(strategy.applyFilterToCamelHeaders("cAmElVersion", "3.14", null));
        assertTrue(strategy.applyFilterToCamelHeaders("ORg.Apache.Camel.", "value", null));
    }

    @Test
    void inboundFiltersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELHttpMethod", "GET", null));
        assertFalse(strategy.applyFilterToExternalHeaders("myHeader", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("ORg.Apache.Camel.", "value", null));
    }

    @Test
    void inboundFiltersCamelPrefixedHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelSqlQuery", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelHttpUri", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfoo", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELFOO", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("org.apache.camel.", "value", null));
    }

    @Test
    void inboundAllowsNonCamelHeaders() {
        assertFalse(strategy.applyFilterToExternalHeaders("X-Custom", "value", null));
        assertFalse(strategy.applyFilterToExternalHeaders("orderId", "12345", null));
        assertFalse(strategy.applyFilterToExternalHeaders("orgapachecamel.", "value", null));
    }

    @Test
    void outboundAllowsUserHeaders() {
        assertFalse(strategy.applyFilterToCamelHeaders("X-Custom", "value", null));
    }

    // Kafka-specific tests
    @Test
    void inboundFiltersKafkaPrefixedHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("kafka.partition", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("kafka.FileName", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("kafka.foo", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("KAFKA.FOO", "value", null));
        assertFalse(strategy.applyFilterToExternalHeaders("kafkapartition", "value", null));
    }

    @Test
    void inboundFiltersMixedCaseKafkaHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("KAfkA.PArtition", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("KaFka.FileName", "value", null));
        assertFalse(strategy.applyFilterToExternalHeaders("KAfkapartition", "value", null));
    }
}

