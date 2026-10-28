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
package org.apache.camel.test.dst.sample;

import java.util.concurrent.TimeUnit;

/**
 * Code under test that sleeps, for the sleep test.
 */
public final class SampleSleeper {

    private SampleSleeper() {
    }

    public static void sleepMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    public static void sleepMillisNanos(long millis) throws InterruptedException {
        Thread.sleep(millis, 500);
    }

    public static void sleepSeconds(long seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
