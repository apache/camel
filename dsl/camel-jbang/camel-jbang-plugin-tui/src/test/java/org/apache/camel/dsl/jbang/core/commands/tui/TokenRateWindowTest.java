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

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenRateWindowTest {

    @Test
    void steadyCounterGivesTokensPerSecond() {
        TokenRateWindow w = new TokenRateWindow(1000);
        w.sample(0, 0);
        w.sample(500, 25);
        w.sample(1000, 50);
        assertEquals(50.0, w.ratePerSecond(1000), 0.001);
    }

    @Test
    void windowDropsOldSamplesButKeepsABaseline() {
        TokenRateWindow w = new TokenRateWindow(1000);
        w.sample(0, 0);
        w.sample(500, 10);
        w.sample(1000, 20);
        w.sample(1500, 80); // 60 tokens in the last half second
        w.sample(2000, 140);
        // baseline is the sample at t=1000 (20 tokens): 120 tokens over 1 second
        assertEquals(120.0, w.ratePerSecond(2000), 0.001);
    }

    @Test
    void singleSampleOrStaleCounterIsZero() {
        TokenRateWindow w = new TokenRateWindow(1000);
        assertEquals(0.0, w.ratePerSecond(0), 0.001);
        w.sample(0, 100);
        assertEquals(0.0, w.ratePerSecond(0), 0.001);
        w.sample(500, 150);
        assertEquals(100.0, w.ratePerSecond(500), 0.001);
        // nothing sampled for longer than the window: the model stopped
        assertEquals(0.0, w.ratePerSecond(2000), 0.001);
    }

    @Test
    void counterResetStartsANewBaseline() {
        TokenRateWindow w = new TokenRateWindow(1000);
        w.sample(0, 90);
        w.sample(500, 100);
        // new request: llama-server counts decoded tokens per request, so the counter drops
        w.sample(1000, 3);
        w.sample(1500, 33);
        assertEquals(60.0, w.ratePerSecond(1500), 0.001);
    }

    @Test
    void idleCounterIsZeroNotNegative() {
        TokenRateWindow w = new TokenRateWindow(1000);
        w.sample(0, 40);
        w.sample(500, 40);
        w.sample(1000, 40);
        assertEquals(0.0, w.ratePerSecond(1000), 0.001);
    }
}
