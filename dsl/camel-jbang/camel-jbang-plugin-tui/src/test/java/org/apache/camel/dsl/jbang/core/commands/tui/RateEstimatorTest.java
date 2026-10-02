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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateEstimatorTest {

    @Test
    void theRateIsMeasuredWhenTheAppReportsNone() {
        // a Quarkus app in prod: no load statistics, so 0.00 while 2 messages a second are processed
        RateEstimator rates = new RateEstimator();
        assertEquals("0.00", rates.estimate("1", "0.00", 100, 0), "one sample is not a rate yet");
        assertEquals("2.00", rates.estimate("1", "0.00", 102, 1000));
        assertEquals("2.00", rates.estimate("1", "", 110, 5000));
        assertNull(rates.estimate("2", null, 7, 0));
        assertEquals("1.00", rates.estimate("2", null, 8, 1000));
    }

    @Test
    void aReportedRateIsKept() {
        RateEstimator rates = new RateEstimator();
        rates.estimate("1", "1.00", 100, 0);
        assertEquals("1.00", rates.estimate("1", "1.00", 150, 1000), "the app measures it better");
    }

    @Test
    void anIdleAppHasNoRate() {
        RateEstimator rates = new RateEstimator();
        rates.estimate("1", "0.00", 100, 0);
        rates.estimate("1", "0.00", 100, 1000);
        assertEquals("0.00", rates.estimate("1", "0.00", 100, 2000));
    }

    @Test
    void theWindowIsTheLastTenSeconds() {
        RateEstimator rates = new RateEstimator();
        rates.estimate("1", null, 0, 0);
        rates.estimate("1", null, 100, 1000);
        // nothing for a while: the burst falls out of the window
        rates.estimate("1", null, 100, 20_000);
        assertEquals("0.00", rates.estimate("1", "0.00", 100, 21_000));
    }

    @Test
    void aResetStartsAgain() {
        RateEstimator rates = new RateEstimator();
        rates.estimate("1", null, 500, 0);
        assertNull(rates.estimate("1", null, 3, 1000), "the stats were reset");
        assertEquals("3.00", rates.estimate("1", null, 6, 2000));
    }

    @Test
    void theRoutesGetARateToo() {
        RateEstimator rates = new RateEstimator();
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "42";
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        info.routes.add(route);

        info.exchangesTotal = 10;
        route.total = 10;
        rates.fill(info, 0);
        info.exchangesTotal = 14;
        route.total = 12;
        rates.fill(info, 2000);
        assertEquals("2.00", info.throughput);
        assertEquals("1.00", route.throughput);

        rates.retain(Set.of());
        assertNull(rates.estimate("42", null, 20, 3000), "a gone integration starts over");
    }
}
