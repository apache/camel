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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the OOM guard in {@link org.apache.camel.support.MessageSupport#getMandatoryBody(Class)} and the safe value
 * description in {@link TypeConversionException}.
 */
public class MessageSupportOversizedBodyTest extends ContextTestSupport {

    private static final String CAP_PROPERTY = "camel.convert.max-bytes";
    private static final int TEST_CAP = 16;

    @BeforeEach
    void setTinyCap() {
        System.setProperty(CAP_PROPERTY, String.valueOf(TEST_CAP));
    }

    @AfterEach
    void clearCap() {
        System.clearProperty(CAP_PROPERTY);
    }

    // ---- getMandatoryBody guard ----

    @Test
    void testStringBodyBelowCapPassesThrough() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");

        assertThat(exchange.getIn().getMandatoryBody(String.class)).isEqualTo("hello");
    }

    @Test
    void testStringBodyAtCapPassesThrough() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("x".repeat(TEST_CAP));

        assertThat(exchange.getIn().getMandatoryBody(String.class)).hasSize(TEST_CAP);
    }

    @Test
    void testAlreadySameTypeBodyOverCapIsAllowed() throws Exception {
        // A String body already in memory needs no conversion — no extra allocation.
        // The guard must NOT fire for already-compatible same-type bodies.
        Exchange exchange = new DefaultExchange(context);
        String big = "x".repeat(TEST_CAP + 1);
        exchange.getIn().setBody(big);

        // already String → no conversion → guard does not fire
        String result = exchange.getIn().getMandatoryBody(String.class);
        assertThat(result).isSameAs(big);
    }

    @Test
    void testByteArrayToStringOverCapThrowsInvalidPayloadException() {
        // byte[] → String conversion would allocate; guard fires
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new byte[TEST_CAP + 1]);

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("Refusing to convert")
                .hasMessageContaining("in-memory conversion limit")
                .hasMessageContaining("streaming");
    }

    @Test
    void testNonBulkTargetNotGuarded() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("42");

        assertThat(exchange.getIn().getMandatoryBody(Integer.class)).isEqualTo(42);
    }

    @Test
    void testSizeInExceptionMessage() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new byte[TEST_CAP + 10]);

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .getCause()
                .hasMessageContaining("size=" + (TEST_CAP + 10));
    }

    @Test
    void testCapOverrideViaExchangeProperty() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("CamelConvertMaxBytes", 5L);
        exchange.getIn().setBody(new byte[6]);

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("Refusing to convert");
    }

    @Test
    void testCapOverrideViaContextGlobalOption() {
        context.getGlobalOptions().put("CamelConvertMaxBytes", "5");
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(new byte[6]);

            assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                    .isInstanceOf(InvalidPayloadException.class)
                    .hasMessageContaining("Refusing to convert");
        } finally {
            context.getGlobalOptions().remove("CamelConvertMaxBytes");
        }
    }

    // ---- TypeConversionException safe value description ----

    @Test
    void testTypeConversionExceptionDoesNotCallToStringOnBody() {
        AtomicBoolean toStringCalled = new AtomicBoolean(false);
        Object dangerousBody = new Object() {
            @Override
            public String toString() {
                toStringCalled.set(true);
                return "should not be called";
            }
        };

        new TypeConversionException(dangerousBody, String.class, new RuntimeException("test"));

        assertThat(toStringCalled.get())
                .as("TypeConversionException must not call toString() on the body value")
                .isFalse();
    }

    @Test
    void testTypeConversionExceptionMessageContainsTypeNotValue() {
        Object body = new Object() {
            @Override
            public String toString() {
                return "huge-body-content-that-should-not-appear";
            }
        };

        TypeConversionException ex = new TypeConversionException(body, String.class, new RuntimeException("cause"));

        assertThat(ex.getMessage())
                .doesNotContain("huge-body-content-that-should-not-appear")
                .contains(body.getClass().getName());
    }

    @Test
    void testTypeConversionExceptionShortStringValueIsIncluded() {
        TypeConversionException ex = new TypeConversionException("hello", Integer.class, new RuntimeException("cause"));

        assertThat(ex.getMessage()).contains("hello");
    }

    @Test
    void testTypeConversionExceptionLongStringValueIsTruncated() {
        String longValue = "x".repeat(200);
        TypeConversionException ex = new TypeConversionException(longValue, Integer.class, new RuntimeException("cause"));

        assertThat(ex.getMessage())
                .contains("...")
                .doesNotContain(longValue);
    }
}
