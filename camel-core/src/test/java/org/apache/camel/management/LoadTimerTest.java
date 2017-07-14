/**
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
package org.apache.camel.management;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.TimerListener;
import org.apache.camel.management.mbean.LoadTriplet;
import org.apache.camel.support.TimerListenerManager;

import static org.awaitility.Awaitility.await;

public class LoadTimerTest extends ContextTestSupport {

    private static final int SAMPLES = 2;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testTimer() throws Exception {
        TimerListenerManager myTimer = new TimerListenerManager();
        myTimer.setCamelContext(context);
        myTimer.start();

        TestLoadAware test = new TestLoadAware();
        myTimer.addTimerListener(test);
        try {
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(test.counter >= SAMPLES);
                assertFalse(Double.isNaN(test.load.getLoad1()));
                assertTrue(test.load.getLoad1() > 0.0d);
                assertTrue(test.load.getLoad1() < SAMPLES);
            });
        } finally {
            myTimer.removeTimerListener(test);
        }

        myTimer.stop();
    }

    private class TestLoadAware implements TimerListener {

        volatile int counter;
        LoadTriplet load = new LoadTriplet();

        @Override
        public void onTimer() {
            load.update(++counter);
        }

    }
}
