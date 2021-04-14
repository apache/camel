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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultExchangeTest {

    private static final String SAFE_PROPERTY = "SAFE_PROPERTY";
    private static final String UNSAFE_PROPERTY = "UNSAFE_PROPERTY";

    @Test
    public void testExchangeCopy() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        SafeProperty property = new SafeProperty();
        UnsafeProperty unsafeProperty = new UnsafeProperty();
        exchange.setProperty(SAFE_PROPERTY, property);
        exchange.setProperty(UNSAFE_PROPERTY, unsafeProperty);

        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);

        assertThat(copy.getProperty(SAFE_PROPERTY)).isNotSameAs(property);
        assertThat(copy.getProperty(UNSAFE_PROPERTY)).isSameAs(unsafeProperty);

    }

    private static final class SafeProperty implements CamelCopySafeProperty<SafeProperty> {

        private SafeProperty() {

        }

        @Override
        public SafeProperty safeCopy() {
            return new SafeProperty();
        }

    }

    private static class UnsafeProperty {

    }

}
