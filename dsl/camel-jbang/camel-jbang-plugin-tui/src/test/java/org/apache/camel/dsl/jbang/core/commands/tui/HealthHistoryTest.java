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
 * Ready and live second by second: a second with no read of its own between two reads that say the same is no gap in
 * the strip, a longer time without reads is.
 */
class HealthHistoryTest {

    private static final byte UP = HealthHistory.READY | HealthHistory.LIVE;

    private final HealthHistory history = new HealthHistory();
    private final IntegrationInfo info = new IntegrationInfo();

    @Test
    void aSecondSkippedBetweenTwoReadsThatSayTheSameIsNoGap() {
        // read a little over a second apart, as while a diagram is on screen: second 102 has no read of its own
        read(100, true);
        read(101, true);
        read(103, true);

        assertThat(history.strip("1", 103, 4)).containsExactly(UP, UP, UP, UP);
    }

    @Test
    void twoSecondsSkippedBetweenTwoReadsThatSayTheSameIsNoGap() {
        // the most that is bridged: seconds 101 and 102 have no read of their own
        read(100, true);
        read(103, true);

        assertThat(history.strip("1", 103, 4)).containsExactly(UP, UP, UP, UP);
    }

    @Test
    void threeSecondsSkippedBetweenTwoReadsThatSayTheSameIsAGap() {
        // one more than is bridged: seconds 101 to 103 have no read of their own
        read(100, true);
        read(104, true);

        assertThat(history.strip("1", 104, 5)).containsExactly(UP, HealthHistory.UNKNOWN, HealthHistory.UNKNOWN,
                HealthHistory.UNKNOWN, UP);
    }

    @Test
    void aLongerTimeWithoutReadsIsNotKnown() {
        read(100, true);
        read(110, true);

        byte[] strip = history.strip("1", 110, 11);
        assertThat(strip[0]).isEqualTo(UP);
        assertThat(strip[5]).isEqualTo(HealthHistory.UNKNOWN);
        assertThat(strip[10]).isEqualTo(UP);
    }

    @Test
    void aSkippedSecondBetweenTwoReadsThatDifferIsNotKnown() {
        read(100, true);
        read(102, false);

        byte[] strip = history.strip("1", 102, 3);
        assertThat(strip[0]).isEqualTo(UP);
        assertThat(strip[1]).as("when it went down is not known").isEqualTo(HealthHistory.UNKNOWN);
        assertThat(strip[2]).isEqualTo(HealthHistory.LIVE);
    }

    private void read(long second, boolean ready) {
        info.pid = "1";
        info.healthReady = ready;
        info.healthLive = true;
        history.record(info, second);
    }
}
