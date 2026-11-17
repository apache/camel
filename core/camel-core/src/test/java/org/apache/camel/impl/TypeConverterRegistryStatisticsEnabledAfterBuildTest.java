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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The type converter registry is created when the CamelContext is built, so the statistics option can be set after it
 * exists (CAMEL-27103).
 */
public class TypeConverterRegistryStatisticsEnabledAfterBuildTest {

    @Test
    public void testEnableAfterBuild() throws Exception {
        // the default constructor builds the context, which creates the type converter registry
        try (CamelContext context = new DefaultCamelContext()) {
            context.setTypeConverterStatisticsEnabled(true);
            context.start();

            assertEquals(123, context.getTypeConverter().convertTo(int.class, "123"));

            TypeConverterRegistry.Statistics statistics = context.getTypeConverterRegistry().getStatistics();
            assertEquals(1, statistics.getAttemptCounter());
            assertEquals(1, statistics.getHitCounter());
        }
    }

    @Test
    public void testDisableAfterBuild() throws Exception {
        try (CamelContext context = new DefaultCamelContext(false)) {
            context.setTypeConverterStatisticsEnabled(true);
            context.build();
            context.setTypeConverterStatisticsEnabled(false);
            context.start();

            assertEquals(123, context.getTypeConverter().convertTo(int.class, "123"));

            TypeConverterRegistry.Statistics statistics = context.getTypeConverterRegistry().getStatistics();
            assertEquals(0, statistics.getAttemptCounter());
            assertEquals(0, statistics.getHitCounter());
        }
    }
}
