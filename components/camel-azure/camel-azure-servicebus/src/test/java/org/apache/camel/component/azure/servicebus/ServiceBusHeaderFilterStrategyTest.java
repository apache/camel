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

package org.apache.camel.component.azure.servicebus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class ServiceBusHeaderFilterStrategyTest {
    private final ServiceBusHeaderFilterStrategy headerFilterStrategy = new ServiceBusHeaderFilterStrategy();

    @ParameterizedTest
    @ArgumentsSource(HeaderArgumentsProvider.class)
    void testApplyFilterToCamelHeadersPassesKnownTypes(String headerName, Object headerValue) {
        assertThat(headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, null))
                .isFalse();
    }

    @Test
    void testApplyFilterToCamelHeadersFiltersUnknownType() {
        assertThat(headerFilterStrategy.applyFilterToCamelHeaders("objectHeader", new Object(), null))
                .isTrue();
    }

    static class HeaderArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                    Arguments.of("booleanHeader", true),
                    Arguments.of("byteHeader", (byte) 1),
                    Arguments.of("characterHeader", '1'),
                    Arguments.of("doubleHeader", 1.0D),
                    Arguments.of("floatHeader", 1.0F),
                    Arguments.of("integerHeader", 1),
                    Arguments.of("longHeader", 1L),
                    Arguments.of("shortHeader", (short) 1),
                    Arguments.of("stringHeader", "stringHeader"),
                    Arguments.of("timestampHeader", new Date()),
                    Arguments.of("uuidHeader", UUID.randomUUID()));
        }
    }
}
