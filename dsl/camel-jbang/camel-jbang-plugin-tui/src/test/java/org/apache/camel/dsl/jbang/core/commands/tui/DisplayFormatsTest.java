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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the tables show URIs, durations and exchange ids.
 */
class DisplayFormatsTest {

    @Test
    void endpointUrisReadAsWritten() {
        assertThat(TuiHelper.displayUri("platform-http:///stock/%7Bsku%7D?httpMethodRestrict=GET"))
                .isEqualTo("platform-http:///stock/{sku}?httpMethodRestrict=GET");
        assertThat(TuiHelper.displayUri("file://parked?fileName=%24%7Bheader.CamelFileName%7D"))
                .isEqualTo("file://parked?fileName=${header.CamelFileName}");
        // a plus stays a plus, and a URI that is not encoded is kept
        assertThat(TuiHelper.displayUri("sql:select a+b from t%20x")).isEqualTo("sql:select a+b from t x");
        assertThat(TuiHelper.displayUri("timer:tick?period=1000")).isEqualTo("timer:tick?period=1000");
        assertThat(TuiHelper.displayUri("bad%zz")).isEqualTo("bad%zz");
        assertThat(TuiHelper.displayUri(null)).isNull();
    }

    @Test
    void durationsReadAsSeconds() {
        assertThat(TuiHelper.formatDurationMs(17)).isEqualTo("17ms");
        assertThat(TuiHelper.formatDurationMs(2020)).isEqualTo("2.02s");
        assertThat(TuiHelper.formatDurationMs(12345)).isEqualTo("12.3s");
        assertThat(TuiHelper.formatDurationMs(125_000)).isEqualTo("2m5s");
    }

    @Test
    void exchangeIdsByTheirCounter() {
        assertThat(ActivityTab.shortExchangeId("AD50EEC2B14A8A3-0000000000000001")).isEqualTo("AD50EE…-1");
        assertThat(ActivityTab.shortExchangeId("AD50EEC2B14A8A3-000000000000000A")).isEqualTo("AD50EE…-A");
        assertThat(ActivityTab.shortExchangeId("AD50EEC2B14A8A3-0000000000000000")).isEqualTo("AD50EE…-0");
        assertThat(ActivityTab.shortExchangeId("no-dash-")).isEqualTo("no-dash-");
        assertThat(ActivityTab.shortExchangeId("plain")).isEqualTo("plain");
        assertThat(ActivityTab.shortExchangeId(null)).isEmpty();
    }
}
