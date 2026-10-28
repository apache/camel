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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The values the listings of running integrations show the same way.
 */
class ListingFormatTest {

    @Test
    void shortUri() {
        assertThat(ListingFormat.uri("kafka:orders?brokers=localhost:9092", true)).isEqualTo("kafka:orders");
        assertThat(ListingFormat.uri("kafka:orders?brokers=localhost:9092", false))
                .isEqualTo("kafka:orders?brokers=localhost:9092");
        assertThat(ListingFormat.uri("timer:tick", true)).isEqualTo("timer:tick");
        assertThat(ListingFormat.uri(null, true)).isNull();
    }

    @Test
    void sinceLast() {
        assertThat(ListingFormat.sinceLast("1s", "2s", null)).isEqualTo("1s/2s/-");
    }

    @Test
    void delta() {
        assertThat(ListingFormat.delta("5")).isEqualTo("+5");
        assertThat(ListingFormat.delta("-5")).isEqualTo("-5");
        assertThat(ListingFormat.delta("0")).isEqualTo("0");
        assertThat(ListingFormat.delta(null)).isNull();
    }

    @Test
    void load() {
        assertThat(ListingFormat.load("1.00", "0.50", "0.00")).isEqualTo("1.00/0.50/-");
        assertThat(ListingFormat.load("0.00", null, "0.00")).isEqualTo("0/0/0");
    }

    @Test
    void slowestFirst() {
        List<String> means = new ArrayList<>(List.of("10", "300", "20"));
        means.sort(ListingFormat.slowestFirst("name", m -> m));
        assertThat(means).containsExactly("300", "20", "10");
        // the fastest first with a - in front of the sort key
        means.sort(ListingFormat.slowestFirst("-name", m -> m));
        assertThat(means).containsExactly("10", "20", "300");
    }
}
