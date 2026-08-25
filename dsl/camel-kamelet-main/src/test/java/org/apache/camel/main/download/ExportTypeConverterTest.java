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
package org.apache.camel.main.download;

import java.time.Duration;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportTypeConverterTest {

    private SimpleCamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new SimpleCamelContext();
        TypeConverterRegistry registry = context.getTypeConverterRegistry();
        ExportTypeConverter exportConverter = new ExportTypeConverter();
        registry.addTypeConverter(Duration.class, String.class, exportConverter);
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void shouldConvertStringToDurationAsMilliseconds() {
        Duration duration = context.getTypeConverter().convertTo(Duration.class, "500");

        assertEquals(Duration.ofMillis(500), duration);
    }

    @Test
    void shouldConvertDurationTextWithUnit() {
        Duration duration = context.getTypeConverter().convertTo(Duration.class, "2s");

        assertEquals(Duration.ofSeconds(2), duration);
    }

    @Test
    void shouldConvertViaExportTypeConverterDirectly() {
        ExportTypeConverter converter = new ExportTypeConverter();

        Duration duration = converter.convertTo(Duration.class, null, "500ms");

        assertEquals(Duration.ofMillis(500), duration);
    }
}
