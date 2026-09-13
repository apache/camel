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
package org.apache.camel.main;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-24706: camel.main.duration is an alias of durationMaxSeconds that accepts a time unit.
 */
public class MainDurationAliasTest {

    @Test
    public void testDurationWithUnit() {
        MainConfigurationProperties config = new MainConfigurationProperties();
        config.setDuration("15s");
        assertEquals(15, config.getDurationMaxSeconds());
        config.setDuration("2m");
        assertEquals(120, config.getDurationMaxSeconds());
        config.setDuration("1h");
        assertEquals(3600, config.getDurationMaxSeconds());
        config.setDuration("500ms");
        assertEquals(1, config.getDurationMaxSeconds());
    }

    @Test
    public void testPlainNumberIsSeconds() {
        MainConfigurationProperties config = new MainConfigurationProperties();
        config.setDuration("30");
        assertEquals(30, config.getDurationMaxSeconds());
    }

    @Test
    public void testDurationAsProperty() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.main.duration", "45s");
        main.configure().withDurationMaxMessages(0);
        try {
            main.start();
            assertEquals(45, main.configure().getDurationMaxSeconds());
        } finally {
            main.stop();
        }
    }
}
