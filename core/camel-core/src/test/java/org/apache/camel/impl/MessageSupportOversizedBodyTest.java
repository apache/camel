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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link org.apache.camel.support.MessageSupport#getMandatoryBody(Class)} refuses to convert bodies that
 * exceed the in-memory conversion size limit instead of triggering an {@code OutOfMemoryError}.
 *
 * <p>
 * Uses a tiny cap (16 bytes) via system property {@code camel.message.max-in-memory-body} to avoid allocating huge
 * strings in tests.
 */
public class MessageSupportOversizedBodyTest extends ContextTestSupport {

    private static final String CAP_PROPERTY = "camel.message.max-in-memory-body";
    private static final int TEST_CAP = 16;

    @BeforeEach
    void setTinyCap() {
        System.setProperty(CAP_PROPERTY, String.valueOf(TEST_CAP));
    }

    @AfterEach
    void clearCap() {
        System.clearProperty(CAP_PROPERTY);
    }

    @Test
    void testStringBodyBelowCapPassesThrough() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");

        String result = exchange.getIn().getMandatoryBody(String.class);

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testStringBodyAtCapPassesThrough() throws Exception {
        // Exactly at the cap (16 chars) must be allowed
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("x".repeat(TEST_CAP));

        String result = exchange.getIn().getMandatoryBody(String.class);

        assertThat(result).hasSize(TEST_CAP);
    }

    @Test
    void testStringBodyOverCapThrowsInvalidPayloadException() {
        // One byte over the cap must be refused
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("x".repeat(TEST_CAP + 1));

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("Refusing to convert")
                .hasMessageContaining("in-memory conversion size limit")
                .hasMessageContaining("streaming");
    }

    @Test
    void testByteArrayBodyOverCapThrowsInvalidPayloadException() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new byte[TEST_CAP + 1]);

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("Refusing to convert")
                .hasMessageContaining("size=");
    }

    @Test
    void testNonBulkTargetTypeNotGuarded() throws Exception {
        // Conversion to non-bulk type (e.g. Integer) is never guarded
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("42");

        Integer result = exchange.getIn().getMandatoryBody(Integer.class);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void testSizeInExceptionMessage() {
        long bodySize = TEST_CAP + 10;
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("x".repeat((int) bodySize));

        assertThatThrownBy(() -> exchange.getIn().getMandatoryBody(String.class))
                .isInstanceOf(InvalidPayloadException.class)
                .getCause()
                .hasMessageContaining("size=" + bodySize);
    }
}
