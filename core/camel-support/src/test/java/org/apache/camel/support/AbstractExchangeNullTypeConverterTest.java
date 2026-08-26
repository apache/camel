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
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link AbstractExchange#getIn(Class)} and {@link AbstractExchange#getOut(Class)} throw a descriptive
 * {@link IllegalStateException} instead of a {@link NullPointerException} when {@link CamelContext#getTypeConverter()}
 * returns {@code null}.
 *
 * <p>
 * Real-world trigger: same race condition as CAMEL-24510 — a Quartz SFTP poll or similar thread calls
 * {@code exchange.getIn(SomeType.class)} while the CamelContext is shutting down and
 * {@code DefaultCamelContextExtension.resetTypeConverter()} has already set the type converter to {@code null}.
 */
class AbstractExchangeNullTypeConverterTest {

    @Test
    void testGetInWithTypeNullTypeConverterThrowsIllegalStateException() {
        CamelContext context = mock(CamelContext.class);
        when(context.getTypeConverter()).thenReturn(null);

        DefaultExchange exchange = new DefaultExchange(context);
        Message in = mock(Message.class);
        exchange.setIn(in);

        assertThatThrownBy(() -> exchange.getIn(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext type converter is not available")
                .hasMessageContaining("java.lang.String");
    }

    @Test
    void testGetOutWithTypeNullTypeConverterThrowsIllegalStateException() {
        CamelContext context = mock(CamelContext.class);
        when(context.getTypeConverter()).thenReturn(null);

        DefaultExchange exchange = new DefaultExchange(context);
        Message out = mock(Message.class);
        exchange.setOut(out);

        assertThatThrownBy(() -> exchange.getOut(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CamelContext type converter is not available")
                .hasMessageContaining("java.lang.String");
    }
}
