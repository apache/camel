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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constants a Java route refers to in classes the parser cannot see: the project's other files, and the catalog's
 * component header constants and enum values (CAMEL-25148).
 */
class ProjectConstantResolverTest {

    @Test
    void projectFilesAndTheCatalog() {
        Supplier<String> names = () -> """
                package com.acme;
                public final class Names {
                    public static final String QUEUE = "orders";
                }
                """;
        ProjectConstantResolver r = new ProjectConstantResolver(
                Map.of("src/main/java/com/acme/Names.java", names),
                ProjectOverviewTest.CATALOG);
        assertThat(r.constant("com.acme.Names", "QUEUE")).isEqualTo("orders");
        assertThat(r.constant("com.other.Names", "QUEUE")).as("another package").isNull();
        // a header constant of a component
        assertThat(r.constant("org.apache.camel.component.kafka.KafkaConstants", "PARTITION_KEY"))
                .isEqualTo("CamelKafkaPartitionKey");
        // an enum constant of a component as the option value the catalog lists
        assertThat(r.constant("org.apache.camel.component.hazelcast.HazelcastOperation", "PUT_IF_ABSENT"))
                .isEqualTo("putIfAbsent");
        assertThat(r.constant("org.apache.camel.component.hazelcast.HazelcastOperation", "NO_SUCH")).isNull();
    }
}
