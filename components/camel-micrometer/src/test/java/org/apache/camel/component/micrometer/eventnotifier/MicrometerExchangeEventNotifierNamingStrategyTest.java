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
package org.apache.camel.component.micrometer.eventnotifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MicrometerExchangeEventNotifierNamingStrategyTest {

    @Test
    void testDefaultFormatName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;

        String result = strategy.formatName("some.metric.name");

        assertEquals("some.metric.name", result);
    }

    @Test
    void testLegacyFormatName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.LEGACY;

        String result = strategy.formatName("some.metric.name");

        assertEquals("SomeMetricName", result);
    }

    @Test
    void getDefaultInflightExchangesName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;
        String result = strategy.getInflightExchangesName();

        assertEquals("camel.exchanges.inflight", result);
    }

    @Test
    void getLegacyInflightExchangesName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.LEGACY;
        String result = strategy.getInflightExchangesName();

        assertEquals("CamelExchangesInflight", result);
    }

}
