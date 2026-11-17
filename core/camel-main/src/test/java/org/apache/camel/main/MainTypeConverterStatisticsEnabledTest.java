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
 * Camel Main builds the CamelContext, which creates the type converter registry, before it applies the configuration,
 * so camel.main.typeConverterStatisticsEnabled must be applied before the context is built (CAMEL-27103).
 */
public class MainTypeConverterStatisticsEnabledTest {

    @Test
    public void testTypeConverterStatisticsEnabledFromProperties() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.main.typeConverterStatisticsEnabled", "true");
        assertStatisticsEnabled(main);
    }

    @Test
    public void testTypeConverterStatisticsEnabledFromConfigure() throws Exception {
        Main main = new Main();
        main.configure().withTypeConverterStatisticsEnabled(true);
        assertStatisticsEnabled(main);
    }

    @Test
    public void testTypeConverterStatisticsEnabledFromSystemProperty() throws Exception {
        // lower case, as the auto configuration ignores case
        System.setProperty("camel.main.typeconverterstatisticsenabled", "true");
        try {
            assertStatisticsEnabled(new Main());
        } finally {
            System.clearProperty("camel.main.typeconverterstatisticsenabled");
        }
    }

    @Test
    public void testTypeConverterStatisticsEnabledOnContextBeforeBuild() throws Exception {
        // enabled in code before the context is built: the default (false) of camel.main must not turn it off
        Main main = new Main() {
            @Override
            protected CamelContext createCamelContext() {
                CamelContext context = super.createCamelContext();
                context.setTypeConverterStatisticsEnabled(true);
                return context;
            }
        };
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            TypeConverterRegistry.Statistics statistics = context.getTypeConverterRegistry().getStatistics();
            long before = statistics.getAttemptCounter();

            assertEquals(123, context.getTypeConverter().convertTo(int.class, "123"));

            assertEquals(before + 1, statistics.getAttemptCounter());
        } finally {
            main.stop();
        }
    }

    private static void assertStatisticsEnabled(Main main) throws Exception {
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertTrue(context.isTypeConverterStatisticsEnabled());

            TypeConverterRegistry.Statistics statistics = context.getTypeConverterRegistry().getStatistics();
            long before = statistics.getAttemptCounter();

            assertEquals(123, context.getTypeConverter().convertTo(int.class, "123"));

            assertEquals(before + 1, statistics.getAttemptCounter());
        } finally {
            main.stop();
        }
    }
}
