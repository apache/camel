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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ExchangeHelper#convertToType} and {@link ExchangeHelper#convertToMandatoryType} throw a
 * descriptive {@link IllegalStateException} instead of a {@link NullPointerException} when
 * {@link CamelContext#getTypeConverter()} returns {@code null}.
 *
 * <p>
 * Real-world trigger: CamelContext stopping or restarting while a Quartz SFTP poll is still running. The consumer calls
 * {@code exchange.getProperty(name, Boolean.class)} which delegates to
 * {@code ExchangeHelper.convertToType(exchange, Boolean.class, "true")}. If the type converter registry has already
 * been cleared during shutdown, {@code getTypeConverter()} returns {@code null} and the original code threw a bare
 * {@code NullPointerException} with no actionable message.
 */
class ExchangeHelperNullTypeConverterTest {

    @Test
    void convertToType_nullTypeConverter_throwsIllegalStateException() {
        Exchange exchange = mockExchangeWithNullTypeConverter();

        assertThatThrownBy(() -> ExchangeHelper.convertToType(exchange, Boolean.class, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext type converter is not available")
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("java.lang.Boolean");
    }

    @Test
    void convertToMandatoryType_nullTypeConverter_throwsIllegalStateException() {
        Exchange exchange = mockExchangeWithNullTypeConverter();

        assertThatThrownBy(() -> ExchangeHelper.convertToMandatoryType(exchange, Boolean.class, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext type converter is not available")
                .hasMessageContaining("java.lang.Boolean");
    }

    @Test
    void convertToType_nullValue_returnsNull() throws Exception {
        Exchange exchange = mockExchangeWithNullTypeConverter();

        assertThat(ExchangeHelper.convertToType(exchange, Boolean.class, null)).isNull();
    }

    @Test
    void convertToType_alreadyCorrectType_returnsValueWithoutConverter() throws Exception {
        Exchange exchange = mockExchangeWithNullTypeConverter();

        assertThat(ExchangeHelper.convertToType(exchange, Boolean.class, Boolean.TRUE))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void convertToType_nullExchange_throwsIllegalStateException() {
        assertThatThrownBy(() -> ExchangeHelper.convertToType(null, Boolean.class, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext type converter is not available");
    }

    private static Exchange mockExchangeWithNullTypeConverter() {
        CamelContext context = mock(CamelContext.class);
        when(context.getTypeConverter()).thenReturn(null);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getContext()).thenReturn(context);
        return exchange;
    }
}
