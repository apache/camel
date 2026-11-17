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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel Main builds the CamelContext before it applies camel.main.typeConverterStatisticsEnabled (CAMEL-27103).
 */
public class MainTypeConverterStatisticsEnabledTest {

    @Test
    public void testTypeConverterStatisticsEnabled() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.main.typeConverterStatisticsEnabled", "true");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertTrue(context.isTypeConverterStatisticsEnabled());

            assertEquals(123, context.getTypeConverter().convertTo(int.class, "123"));

            TypeConverterRegistry.Statistics statistics = context.getTypeConverterRegistry().getStatistics();
            assertEquals(1, statistics.getAttemptCounter());
            assertEquals(1, statistics.getHitCounter());
        } finally {
            main.stop();
        }
    }
}
