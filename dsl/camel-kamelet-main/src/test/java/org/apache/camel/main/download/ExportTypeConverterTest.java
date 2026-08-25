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

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportTypeConverterTest {

    private SimpleCamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new SimpleCamelContext();
        TypeConverterRegistry registry = context.getTypeConverterRegistry();
        registry.setTypeConverterExists(TypeConverterExists.Override);
        registry.addTypeConverter(Duration.class, String.class, new ExportTypeConverter());
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void shouldConvertStringToDurationWhenExportConverterOverridesDefault() throws Exception {
        Duration duration = context.getTypeConverter().mandatoryConvertTo(Duration.class, "500");

        assertEquals(Duration.ofMillis(500), duration);
    }

    @Test
    void shouldConvertDurationTextWithUnitWhenExportConverterOverridesDefault() throws Exception {
        Duration duration = context.getTypeConverter().mandatoryConvertTo(Duration.class, "2s");

        assertEquals(Duration.ofSeconds(2), duration);
    }

    @Test
    void shouldFailMandatoryConversionWithoutDurationSupport() {
        TypeConverterRegistry registry = context.getTypeConverterRegistry();
        registry.addTypeConverter(Duration.class, String.class, new ExportTypeConverterWithoutDuration());

        assertThrows(NoTypeConversionAvailableException.class,
                () -> context.getTypeConverter().mandatoryConvertTo(Duration.class, "500"));
    }

    @Test
    void shouldConvertViaExportTypeConverterDirectly() {
        ExportTypeConverter converter = new ExportTypeConverter();

        Duration duration = converter.convertTo(Duration.class, null, "500ms");

        assertEquals(Duration.ofMillis(500), duration);
    }

    /**
     * Mimics ExportTypeConverter before Duration support was added.
     */
    private static final class ExportTypeConverterWithoutDuration extends ExportTypeConverter {
        @Override
        public <T> T convertTo(Class<T> type, org.apache.camel.Exchange exchange, Object value) {
            if (type == Duration.class) {
                return null;
            }
            return super.convertTo(type, exchange, value);
        }
    }
}
